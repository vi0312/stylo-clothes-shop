import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { prisma } from '../../src/lib/prisma.js'
import { signVnpParams } from '../../src/modules/payments/gateway/vnpay.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) throw new Error(`.env.test must set ${name} for the VNPay callback tests`)
  return value
}

const HASH_SECRET = requireEnv('VNPAY_HASH_SECRET')
const TMN_CODE = requireEnv('VNPAY_TMN_CODE')

const ORDER_CODE = 'STY-261104-A7K2'
const TXN_REF = 'STY261104032000123'
const AMOUNT = 428_000
const PAY_DATE = '20261104102112'
const PAID_AT = new Date('2026-11-04T03:21:12.000Z')

let userId: number
let orderId: number
let paymentId: number

function callback(overrides: Record<string, string> = {}): Record<string, string> {
  const params: Record<string, string> = {
    vnp_Amount: String(AMOUNT * 100),
    vnp_BankCode: 'NCB',
    vnp_OrderInfo: `Thanh toan don hang ${ORDER_CODE}`,
    vnp_PayDate: PAY_DATE,
    vnp_ResponseCode: '00',
    vnp_TmnCode: TMN_CODE,
    vnp_TransactionNo: '14000001',
    vnp_TransactionStatus: '00',
    vnp_TxnRef: TXN_REF,
    ...overrides,
  }
  return { ...params, vnp_SecureHash: signVnpParams(params, HASH_SECRET) }
}

async function seedVnpayAttempt(txnRef = TXN_REF): Promise<void> {
  const order = await prisma.order.create({
    data: {
      code: ORDER_CODE,
      userId,
      clientOrderId: '3f0c2b7e-8a41-4f6d-9c2e-1b5a7d9e0f12',
      paymentMethod: 'ONLINE',
      recipientName: 'Nguyễn Văn An',
      recipientPhone: '0901234567',
      shippingAddress: '12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
      subtotal: AMOUNT,
      shippingFee: 0,
      total: AMOUNT,
    },
  })
  orderId = order.id

  const payment = await prisma.payment.create({
    data: { orderId, provider: 'VNPAY', status: 'PENDING', amount: AMOUNT, txnRef },
  })
  paymentId = payment.id
}

async function paymentRow() {
  return prisma.payment.findUniqueOrThrow({ where: { id: paymentId } })
}

async function orderRow() {
  return prisma.order.findUniqueOrThrow({ where: { id: orderId } })
}

beforeEach(async () => {
  await resetDb()
  const user = await createUser({ email: 'an@example.com' })
  userId = user.id
  await seedVnpayAttempt()
})

describe('GET /payments/vnpay/return', () => {
  it('settles the payment and the order once the signature and amount match', async () => {
    const response = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback())

    expect(response.status).toBe(200)
    expect(response.headers['content-type']).toMatch(/text\/html/)
    expect(response.text).toContain('Thanh toán thành công')
    expect(response.text).toContain(ORDER_CODE)

    expect(await paymentRow()).toMatchObject({
      status: 'SUCCEEDED',
      providerTxnNo: '14000001',
      bankCode: 'NCB',
      payDate: PAID_AT,
      failureReason: null,
    })
    const paidOrder = await orderRow()
    expect(paidOrder.status).toBe('CONFIRMED')
    expect(paidOrder.paidAt).not.toBeNull()
  })

  it('renders the same success page for a replay and never rewrites paidAt', async () => {
    await request(app).get('/api/v1/payments/vnpay/return').query(callback()).expect(200)
    const firstPaidAt = (await orderRow()).paidAt

    const replay = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback())

    expect(replay.status).toBe(200)
    expect(replay.text).toContain('Thanh toán thành công')
    expect((await orderRow()).paidAt).toEqual(firstPaidAt)
  })

  it('rejects an invalid signature without touching state', async () => {
    const params = callback()
    const tampered = { ...params, vnp_Amount: '1' }

    const response = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(tampered)

    expect(response.status).toBe(400)
    expect(response.text).toContain('Chữ ký')
    expect(await paymentRow()).toMatchObject({ status: 'PENDING', failureReason: null })
    expect((await orderRow()).paidAt).toBeNull()
  })

  it('answers 404 for a signed callback naming another attempt', async () => {
    const response = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback({ vnp_TxnRef: 'STY000000000000000' }))

    expect(response.status).toBe(404)
    expect(response.text).toContain('Không tìm thấy giao dịch')
  })

  it('fails the attempt when the callback amount differs from the order', async () => {
    const response = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback({ vnp_Amount: String(AMOUNT * 100 + 100) }))

    expect(response.status).toBe(200)
    expect(response.text).toContain('không khớp')
    expect(await paymentRow()).toMatchObject({
      status: 'FAILED',
      failureReason: 'AMOUNT_MISMATCH',
    })
    expect((await orderRow()).paidAt).toBeNull()
  })

  it('fails the attempt when VNPay reports a declined transaction', async () => {
    const response = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback({ vnp_ResponseCode: '24', vnp_TransactionStatus: '02' }))

    expect(response.status).toBe(200)
    expect(response.text).toContain('mã 24')
    expect(await paymentRow()).toMatchObject({
      status: 'FAILED',
      failureReason: 'VNPAY_CODE_24',
    })
    expect((await orderRow()).paidAt).toBeNull()
  })

  it('never settles a mock attempt through the VNPay callback', async () => {
    const mockAttempt = await prisma.payment.create({
      data: {
        orderId,
        provider: 'MOCK',
        status: 'PENDING',
        amount: AMOUNT,
        txnRef: 'STYMOCK0000000001',
      },
    })

    const response = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback({ vnp_TxnRef: mockAttempt.txnRef }))

    expect(response.status).toBe(404)
    expect(
      await prisma.payment.findUniqueOrThrow({ where: { id: mockAttempt.id } }),
    ).toMatchObject({ status: 'PENDING' })
  })

  it('acknowledges a genuine callback that arrives after the attempt already failed', async () => {
    await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback({ vnp_ResponseCode: '24', vnp_TransactionStatus: '02' }))
      .expect(200)

    const success = await request(app)
      .get('/api/v1/payments/vnpay/return')
      .query(callback())

    expect(success.status).toBe(200)
    expect(success.text).toContain('Thanh toán thành công')
    expect(await paymentRow()).toMatchObject({ status: 'SUCCEEDED', failureReason: null })
  })
})

describe('GET /payments/vnpay/ipn', () => {
  async function ipn(params: Record<string, string>) {
    return request(app).get('/api/v1/payments/vnpay/ipn').query(params)
  }

  it('confirms a genuine callback and always answers HTTP 200 with RspCode 00', async () => {
    const response = await ipn(callback())

    expect(response.status).toBe(200)
    expect(response.body).toEqual({ RspCode: '00', Message: 'Confirm Success' })
    expect(await paymentRow()).toMatchObject({ status: 'SUCCEEDED', providerTxnNo: '14000001' })
    const paidOrder = await orderRow()
    expect(paidOrder.status).toBe('CONFIRMED')
    expect(paidOrder.paidAt).not.toBeNull()
  })

  it('answers 00 to a replay without rewriting paidAt', async () => {
    await ipn(callback())
    const paidAt = (await orderRow()).paidAt

    const replay = await ipn(callback())

    expect(replay.body).toEqual({ RspCode: '00', Message: 'Confirm Success' })
    expect((await orderRow()).paidAt).toEqual(paidAt)
  })

  it('answers 01 for an unknown reference', async () => {
    const response = await ipn(callback({ vnp_TxnRef: 'STY000000000000000' }))
    expect(response.body).toEqual({ RspCode: '01', Message: 'Order not found' })
  })

  it('answers 97 for a bad signature and changes nothing', async () => {
    const params = callback()
    const response = await ipn({ ...params, vnp_Amount: '1' })

    expect(response.body).toEqual({ RspCode: '97', Message: 'Invalid signature' })
    expect(await paymentRow()).toMatchObject({ status: 'PENDING' })
    expect((await orderRow()).paidAt).toBeNull()
  })

  it('answers 04 and fails the attempt on an amount mismatch', async () => {
    const response = await ipn(callback({ vnp_Amount: String(AMOUNT * 100 + 100) }))

    expect(response.body).toEqual({ RspCode: '04', Message: 'Invalid amount' })
    expect(await paymentRow()).toMatchObject({
      status: 'FAILED',
      failureReason: 'AMOUNT_MISMATCH',
    })
  })

  it('answers 99 for a callback reporting a failed transaction', async () => {
    const response = await ipn(callback({ vnp_ResponseCode: '24', vnp_TransactionStatus: '02' }))

    expect(response.body).toEqual({ RspCode: '99', Message: 'Unknown error' })
    expect(await paymentRow()).toMatchObject({ status: 'PENDING' })
  })

  it('answers 01 when the reference belongs to a mock attempt', async () => {
    const mockAttempt = await prisma.payment.create({
      data: {
        orderId,
        provider: 'MOCK',
        status: 'PENDING',
        amount: AMOUNT,
        txnRef: 'STYMOCK0000000001',
      },
    })

    const response = await ipn(callback({ vnp_TxnRef: mockAttempt.txnRef }))
    expect(response.body).toEqual({ RspCode: '01', Message: 'Order not found' })
  })
})
