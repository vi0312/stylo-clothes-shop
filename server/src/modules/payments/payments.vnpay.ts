import { getEnv } from '../../config/env.js'
import { verifyVnpSignature } from './gateway/vnpay.js'
import { vnpayResultPage } from './payments.vnpay-page.js'
import * as paymentsService from './payments.service.js'

export interface HtmlResult {
  status: number
  html: string
}

export interface IpnResult {
  RspCode: string
  Message: string
}

const PROVIDER = 'VNPAY'

/** Express hands query values back decoded; arrays only appear for a malformed duplicate. */
function flatQuery(query: Record<string, unknown>): Record<string, string | undefined> {
  const params: Record<string, string | undefined> = {}
  for (const [key, value] of Object.entries(query)) {
    if (typeof value === 'string') params[key] = value
  }
  return params
}

function parseAmount(raw: string | undefined): number | null {
  if (!raw || !/^\d+$/.test(raw)) return null
  const minorUnits = Number(raw)
  return Number.isSafeInteger(minorUnits) && minorUnits % 100 === 0 ? minorUnits / 100 : null
}

/** `vnp_PayDate` is `YYYYMMDDHHmmss` in VNPay's GMT+7 clock. */
function parseVnpayDate(raw: string | undefined): Date | null {
  if (!raw || !/^\d{14}$/.test(raw)) return null
  const iso = `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}T${raw.slice(8, 10)}:${raw.slice(10, 12)}:${raw.slice(12, 14)}+07:00`
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? null : date
}

function successPage(orderCode: string, amount: number): HtmlResult {
  return {
    status: 200,
    html: vnpayResultPage({
      ok: true,
      title: 'Thanh toán thành công',
      message:
        'Bạn có thể quay lại ứng dụng Stylo. Đơn hàng đã được ghi nhận thanh toán.',
      orderCode,
      amount,
    }),
  }
}

function failurePage(
  orderCode: string,
  amount: number,
  message: string,
): HtmlResult {
  return {
    status: 200,
    html: vnpayResultPage({
      ok: false,
      title: 'Thanh toán không thành công',
      message,
      orderCode,
      amount,
    }),
  }
}

function unverifiedPage(status: number, title: string, message: string): HtmlResult {
  return { status, html: vnpayResultPage({ ok: false, title, message }) }
}

export async function handleVnpayReturn(
  query: Record<string, unknown>,
): Promise<HtmlResult> {
  const params = flatQuery(query)
  if (!verifyVnpSignature(params, getEnv().VNPAY_HASH_SECRET)) {
    return unverifiedPage(
      400,
      'Không xác thực được phản hồi',
      'Chữ ký của VNPay không hợp lệ. Không có thay đổi nào được ghi nhận.',
    )
  }

  const txnRef = params.vnp_TxnRef
  if (!txnRef) {
    return unverifiedPage(400, 'Thiếu thông tin giao dịch', 'Phản hồi không có mã giao dịch.')
  }

  const attempt = await paymentsService.findGatewayAttempt(txnRef, PROVIDER)
  if (!attempt) {
    return unverifiedPage(
      404,
      'Không tìm thấy giao dịch',
      'Không tìm thấy giao dịch này trong hệ thống.',
    )
  }

  // `SUCCEEDED` is terminal: a replay renders the success page, never an error.
  if (attempt.status === 'SUCCEEDED') return successPage(attempt.orderCode, attempt.amount)

  const amount = parseAmount(params.vnp_Amount)
  if (amount === null || amount !== attempt.amount) {
    await paymentsService.failPayment(txnRef, 'AMOUNT_MISMATCH')
    return failurePage(
      attempt.orderCode,
      attempt.amount,
      'Số tiền VNPay trả về không khớp với số tiền của đơn hàng.',
    )
  }

  const responseCode = params.vnp_ResponseCode
  const transactionStatus = params.vnp_TransactionStatus
  const failed =
    responseCode !== '00' || (transactionStatus !== undefined && transactionStatus !== '00')
  if (failed) {
    await paymentsService.failPayment(txnRef, `VNPAY_CODE_${responseCode ?? 'MISSING'}`)
    return failurePage(
      attempt.orderCode,
      attempt.amount,
      `VNPay báo giao dịch không thành công (mã ${responseCode ?? 'không xác định'}).`,
    )
  }

  const result = await paymentsService.confirmPayment({
    txnRef,
    amount,
    providerTxnNo: params.vnp_TransactionNo ?? null,
    bankCode: params.vnp_BankCode ?? null,
    payDate: parseVnpayDate(params.vnp_PayDate),
    rawResponse: params,
  })

  if (result.outcome === 'SUCCEEDED' || result.outcome === 'ALREADY_SUCCEEDED') {
    return successPage(attempt.orderCode, attempt.amount)
  }
  if (result.outcome === 'AMOUNT_MISMATCH') {
    return failurePage(
      attempt.orderCode,
      attempt.amount,
      'Số tiền VNPay trả về không khớp với số tiền của đơn hàng.',
    )
  }
  return unverifiedPage(
    404,
    'Không tìm thấy giao dịch',
    'Không tìm thấy giao dịch này trong hệ thống.',
  )
}

/** VNPay's server-to-server contract: HTTP 200 with a `RspCode`, never a thrown error. */
export async function handleVnpayIpn(query: Record<string, unknown>): Promise<IpnResult> {
  const params = flatQuery(query)
  if (!verifyVnpSignature(params, getEnv().VNPAY_HASH_SECRET)) {
    return { RspCode: '97', Message: 'Invalid signature' }
  }

  const txnRef = params.vnp_TxnRef
  if (!txnRef) return { RspCode: '01', Message: 'Order not found' }

  const attempt = await paymentsService.findGatewayAttempt(txnRef, PROVIDER)
  if (!attempt) return { RspCode: '01', Message: 'Order not found' }
  if (attempt.status === 'SUCCEEDED') return { RspCode: '00', Message: 'Confirm Success' }

  const amount = parseAmount(params.vnp_Amount)
  if (amount === null || amount !== attempt.amount) {
    await paymentsService.failPayment(txnRef, 'AMOUNT_MISMATCH')
    return { RspCode: '04', Message: 'Invalid amount' }
  }

  const responseCode = params.vnp_ResponseCode
  const transactionStatus = params.vnp_TransactionStatus
  if (
    responseCode !== '00' ||
    (transactionStatus !== undefined && transactionStatus !== '00')
  ) {
    return { RspCode: '99', Message: 'Unknown error' }
  }

  const result = await paymentsService.confirmPayment({
    txnRef,
    amount,
    providerTxnNo: params.vnp_TransactionNo ?? null,
    bankCode: params.vnp_BankCode ?? null,
    payDate: parseVnpayDate(params.vnp_PayDate),
    rawResponse: params,
  })

  if (result.outcome === 'SUCCEEDED' || result.outcome === 'ALREADY_SUCCEEDED') {
    return { RspCode: '00', Message: 'Confirm Success' }
  }
  if (result.outcome === 'AMOUNT_MISMATCH') {
    return { RspCode: '04', Message: 'Invalid amount' }
  }
  return { RspCode: '01', Message: 'Order not found' }
}
