import { createHmac, timingSafeEqual } from 'node:crypto'
import { getEnv } from '../../../config/env.js'
import type { PaymentGateway, PaymentUrlInput } from './PaymentGateway.js'

const VNPAY_VERSION = '2.1.0'
const VNPAY_COMMAND = 'pay'
const VNPAY_ORDER_TYPE = 'other'
const VNPAY_CURRENCY = 'VND'
const VNPAY_LOCALE = 'vn'
const VNPAY_HASH_PATTERN = /^[0-9a-f]{128}$/

/** VNPay timestamps are GMT+7, while `createdAt` is a UTC instant. */
const VN_TIME = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
})

export interface VnpayConfig {
  tmnCode: string
  hashSecret: string
  paymentUrl: string
  returnUrl: string
  ipAddress: string
}

export function vnpayConfigFromEnv(): VnpayConfig {
  const env = getEnv()
  return {
    tmnCode: env.VNPAY_TMN_CODE,
    hashSecret: env.VNPAY_HASH_SECRET,
    paymentUrl: env.VNPAY_URL,
    returnUrl: `${env.PUBLIC_BASE_URL.replace(/\/+$/, '')}/api/v1/payments/vnpay/return`,
    ipAddress: env.VNPAY_IP_ADDR,
  }
}

/**
 * VNPay's reference implementation is PHP `urlencode`: spaces become `+` and `!'()*~`
 * are escaped, which `encodeURIComponent` leaves alone. Its own docs disagree about
 * hashing encoded vs raw values; this follows the 2.1.0 changeTypeHash page, which hashes
 * the encoded string it also puts in the query.
 */
export function vnpEncode(value: string): string {
  return encodeURIComponent(value)
    .replaceAll('%20', '+')
    .replace(/[!'()*~]/g, (char) => `%${char.charCodeAt(0).toString(16).toUpperCase()}`)
}

function vnpHashData(params: Record<string, string>): string {
  return Object.keys(params)
    .sort()
    .map((key) => `${vnpEncode(key)}=${vnpEncode(params[key])}`)
    .join('&')
}

export function signVnpParams(params: Record<string, string>, hashSecret: string): string {
  return createHmac('sha512', hashSecret).update(vnpHashData(params)).digest('hex')
}

export function verifyVnpSignature(
  params: Record<string, string | undefined>,
  hashSecret: string,
): boolean {
  const received = params.vnp_SecureHash?.toLowerCase()
  if (!received || !VNPAY_HASH_PATTERN.test(received)) return false

  const signed = { ...params }
  delete signed.vnp_SecureHash
  delete signed.vnp_SecureHashType

  const expected = signVnpParams(compact(signed), hashSecret)
  return timingSafeEqual(Buffer.from(expected, 'hex'), Buffer.from(received, 'hex'))
}

function compact(params: Record<string, string | undefined>): Record<string, string> {
  const result: Record<string, string> = {}
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined) result[key] = value
  }
  return result
}

function vnpTimestamp(date: Date): string {
  const parts = Object.fromEntries(
    VN_TIME.formatToParts(date).map((part) => [part.type, part.value]),
  )
  return `${parts.year}${parts.month}${parts.day}${parts.hour}${parts.minute}${parts.second}`
}

function vnpQueryString(params: Record<string, string>): string {
  return Object.keys(params)
    .sort()
    .map((key) => `${vnpEncode(key)}=${vnpEncode(params[key])}`)
    .join('&')
}

export function buildVnpayParams(
  input: PaymentUrlInput,
  config: VnpayConfig,
): Record<string, string> {
  return {
    vnp_Version: VNPAY_VERSION,
    vnp_Command: VNPAY_COMMAND,
    vnp_TmnCode: config.tmnCode,
    // VNPay takes the amount in the currency's smallest unit: VND has none, so ×100.
    vnp_Amount: String(input.amount * 100),
    vnp_CurrCode: VNPAY_CURRENCY,
    vnp_TxnRef: input.txnRef,
    // OrderInfo must be unaccented ASCII, or VNPay rejects the request.
    vnp_OrderInfo: `Thanh toan don hang ${input.orderCode}`,
    vnp_OrderType: VNPAY_ORDER_TYPE,
    vnp_Locale: VNPAY_LOCALE,
    vnp_ReturnUrl: config.returnUrl,
    vnp_IpAddr: config.ipAddress,
    vnp_CreateDate: vnpTimestamp(input.createdAt),
  }
}

export function buildVnpayUrl(input: PaymentUrlInput, config: VnpayConfig): string {
  const params = buildVnpayParams(input, config)
  const secureHash = signVnpParams(params, config.hashSecret)
  return `${config.paymentUrl}?${vnpQueryString(params)}&vnp_SecureHash=${secureHash}`
}

export const vnpayGateway: PaymentGateway = {
  provider: 'VNPAY',
  buildPaymentUrl(input) {
    return buildVnpayUrl(input, vnpayConfigFromEnv())
  },
}
