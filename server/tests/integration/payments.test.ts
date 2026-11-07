import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { prisma } from '../../src/lib/prisma.js'
import { confirmPayment } from '../../src/modules/payments/payments.service.js'
import { createProduct } from '../factories/catalog.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()

const CLIENT_ORDER_ID = '3f0c2b7e-8a41-4f6d-9c2e-1b5a7d9e0f12'
const OTHER_CLIENT_ORDER_ID = '9b6d1f3a-2c7e-4a58-8d41-0f2e6b7c9a13'
const ADDRESS_LINE = '12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM'
const PRICE = 199_000
const SHIPPING_FEE = 30_000
const TOTAL = PRICE + SHIPPING_FEE

let customerId: number
let otherId: number
let customerToken: string
let otherToken: string
let adminToken: string
let addressId: number
let variantId: number

beforeEach(async () => {
  await resetDb()
  const customer = await createUser({ email: 'an@example.com' })
  const other = await createUser({ email: 'binh@example.com' })
  const admin = await createUser({ email: 'quan-tri@example.com', role: 'ADMIN' })
  customerId = customer.id
  otherId = other.id
  customerToken = signAccessToken(customer.id, customer.role)
  otherToken = signAccessToken(other.id, other.role)
  adminToken = signAccessToken(admin.id, admin.role)

  const address = await prisma.address.create({
    data: {
      userId: customerId,
      recipientName: 'Nguyễn Văn An',
      phone: '0901234567',
      line: ADDRESS_LINE,
    },
  })
  addressId = address.id

  const product = await createProduct({
    variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: PRICE, stock: 10 }],
  })
  const variant = product.variants.find((row) => row.sku === 'AT-DEN-M')
  if (!variant) throw new Error('missing AT-DEN-M')
  variantId = variant.id
})

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

async function fillCart(quantity = 1) {
  const response = await request(app)
    .post('/api/v1/cart/items')
    .set(auth(customerToken))
    .send({ variantId, quantity })
  expect(response.status).toBe(200)
}

async function checkout(
  paymentMethod: 'COD' | 'ONLINE' = 'ONLINE',
  clientOrderId = CLIENT_ORDER_ID,
) {
  const response = await request(app)
    .post('/api/v1/orders')
    .set(auth(customerToken))
    .send({ clientOrderId, addressId, paymentMethod })
  expect([200, 201]).toContain(response.status)
  return response
}

async function placeOnlineOrder() {
  await fillCart()
  const response = await checkout('ONLINE')
  expect(response.status).toBe(201)
  return response.body.payment as {
    id: number
    txnRef: string
    provider: 'MOCK' | 'VNPAY'
    status: string
    amount: number
    paymentUrl: string
    failureReason: string | null
    createdAt: string
  }
}

describe('checkout payment attempts', () => {
  it('creates one PENDING attempt whose amount is the order total, and none for COD', async () => {
    await fillCart()
    const online = await checkout('ONLINE')

    expect(online.status).toBe(201)
    expect(online.body.order.paymentMethod).toBe('ONLINE')
    expect(online.body.order.paymentStatus).toBe('UNPAID')
    expect(online.body.payment).toMatchObject({
      provider: 'MOCK',
      status: 'PENDING',
      amount: TOTAL,
      failureReason: null,
    })
    expect(online.body.payment.txnRef).toMatch(/^STY\d{15}$/)
    expect(online.body.payment.paymentUrl).toBe(
      `http://127.0.0.1:8787/api/v1/payments/mock/${online.body.payment.txnRef}`,
    )
    expect(await prisma.payment.count()).toBe(1)

    await fillCart()
    const cod = await checkout('COD', OTHER_CLIENT_ORDER_ID)
    expect(cod.body.payment).toBeNull()
    expect(await prisma.payment.count()).toBe(1)
  })

  it('replays the still-pending attempt on a repeated clientOrderId without touching it', async () => {
    const attempt = await placeOnlineOrder()

    const repeat = await checkout('ONLINE')
    expect(repeat.status).toBe(200)
    expect(repeat.body.order.id).toBe(1)
    expect(repeat.body.payment.txnRef).toBe(attempt.txnRef)
    expect(repeat.body.payment.status).toBe('PENDING')
    expect(await prisma.payment.count()).toBe(1)
    expect(await prisma.productVariant.findUniqueOrThrow({ where: { id: variantId } })).toMatchObject(
      { stock: 9 },
    )
  })

  it('returns a null payment on replay once the attempt has failed', async () => {
    const attempt = await placeOnlineOrder()
    await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'failed' })
      .expect(200)

    const repeat = await checkout('ONLINE')
    expect(repeat.status).toBe(200)
    expect(repeat.body.payment).toBeNull()
  })
})

describe('POST /orders/:id/payments', () => {
  it('requires an access token', async () => {
    const attempt = await placeOnlineOrder()
    const response = await request(app).post(`/api/v1/orders/${attempt.id}/payments`)
    expect(response.status).toBe(401)
    expect(response.body.error.code).toBe('UNAUTHORIZED')
  })

  it('returns the same attempt to a double tap inside fifteen minutes', async () => {
    const attempt = await placeOnlineOrder()

    const replay = await request(app)
      .post('/api/v1/orders/1/payments')
      .set(auth(customerToken))
      .expect(200)

    expect(replay.body.id).toBe(attempt.id)
    expect(replay.body.txnRef).toBe(attempt.txnRef)
    expect(await prisma.payment.count()).toBe(1)
  })

  it('creates one attempt when two taps race after a failure', async () => {
    const first = await placeOnlineOrder()
    await request(app)
      .post(`/api/v1/payments/mock/${first.txnRef}/complete`)
      .type('form')
      .send({ result: 'failed' })
      .expect(200)

    const start = () =>
      request(app).post('/api/v1/orders/1/payments').set(auth(customerToken))

    const responses = await Promise.all([start(), start()])
    expect(responses.map((response) => response.status).sort()).toEqual([200, 201])
    expect(new Set(responses.map((response) => response.body.id)).size).toBe(1)
    expect(responses[0].body.id).not.toBe(first.id)
    expect(await prisma.payment.count()).toBe(2)
  })

  it('expires an attempt older than fifteen minutes and creates a fresh one', async () => {
    const attempt = await placeOnlineOrder()
    await prisma.payment.update({
      where: { id: attempt.id },
      data: { createdAt: new Date(Date.now() - 16 * 60 * 1000) },
    })

    const fresh = await request(app)
      .post('/api/v1/orders/1/payments')
      .set(auth(customerToken))
      .expect(201)

    expect(fresh.body.id).not.toBe(attempt.id)
    expect(fresh.body.txnRef).not.toBe(attempt.txnRef)
    expect(await prisma.payment.count()).toBe(2)
    expect(await prisma.payment.findUniqueOrThrow({ where: { id: attempt.id } })).toMatchObject({
      status: 'FAILED',
      failureReason: 'EXPIRED',
    })
  })

  it('rejects a COD order', async () => {
    await fillCart()
    const cod = await checkout('COD')

    const response = await request(app)
      .post(`/api/v1/orders/${cod.body.order.id}/payments`)
      .set(auth(customerToken))
    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('PAYMENT_NOT_ALLOWED')
  })

  it('rejects an order that is already paid', async () => {
    const attempt = await placeOnlineOrder()
    await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'success' })
      .expect(200)

    const response = await request(app)
      .post('/api/v1/orders/1/payments')
      .set(auth(customerToken))
    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('ORDER_ALREADY_PAID')
  })

  it('rejects a cancelled order', async () => {
    await placeOnlineOrder()
    await prisma.order.update({ where: { id: 1 }, data: { status: 'CANCELLED' } })

    const response = await request(app)
      .post('/api/v1/orders/1/payments')
      .set(auth(customerToken))
    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('INVALID_ORDER_STATE')
  })

  it('hides another customer’s order', async () => {
    await placeOnlineOrder()
    const response = await request(app)
      .post('/api/v1/orders/1/payments')
      .set(auth(otherToken))
    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
  })
})

describe('mock gateway', () => {
  it('renders the Vietnamese page with the order code and amount, no auth needed', async () => {
    const attempt = await placeOnlineOrder()

    const page = await request(app).get(`/api/v1/payments/mock/${attempt.txnRef}`).expect(200)
    expect(page.headers['content-type']).toContain('text/html')
    expect(page.text).toContain('STY-')
    expect(page.text).toContain('229.000')
    expect(page.text).toContain('value="success"')
    expect(page.text).toContain('value="failed"')
  })

  it('settles the order once and acknowledges every repeat', async () => {
    const attempt = await placeOnlineOrder()

    const first = await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'success' })
      .expect(200)
    expect(first.text).toContain('Thanh toán thành công')

    const paidOrder = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(paidOrder.paidAt).not.toBeNull()
    expect(paidOrder.status).toBe('CONFIRMED')
    expect(await prisma.payment.findUniqueOrThrow({ where: { id: attempt.id } })).toMatchObject({
      status: 'SUCCEEDED',
      failureReason: null,
    })

    const again = await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'success' })
      .expect(200)
    expect(again.text).toContain('Thanh toán thành công')

    const reloaded = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(reloaded.paidAt?.toISOString()).toBe(paidOrder.paidAt?.toISOString())
    expect(await prisma.payment.count()).toBe(1)

    const page = await request(app).get(`/api/v1/payments/mock/${attempt.txnRef}`).expect(200)
    expect(page.text).toContain('Thanh toán thành công')
  })

  it('keeps a SUCCEEDED attempt terminal when the browser submits a later failure', async () => {
    const attempt = await placeOnlineOrder()
    await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'success' })
      .expect(200)

    const late = await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'failed' })
      .expect(200)
    expect(late.text).toContain('Thanh toán thành công')
    expect(await prisma.payment.findUniqueOrThrow({ where: { id: attempt.id } })).toMatchObject({
      status: 'SUCCEEDED',
    })
  })

  it('changes state once when two completes race', async () => {
    const attempt = await placeOnlineOrder()
    const complete = () =>
      request(app)
        .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
        .type('form')
        .send({ result: 'success' })

    const responses = await Promise.all([complete(), complete()])
    for (const response of responses) {
      expect(response.status).toBe(200)
      expect(response.text).toContain('Thanh toán thành công')
    }

    const order = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(order.status).toBe('CONFIRMED')
    expect(order.paidAt).not.toBeNull()
    expect(await prisma.payment.count()).toBe(1)
  })

  it('marks a failed attempt and leaves the order retryable', async () => {
    const attempt = await placeOnlineOrder()

    const failed = await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'failed' })
      .expect(200)
    expect(failed.text).toContain('Thanh toán thất bại')

    expect(await prisma.payment.findUniqueOrThrow({ where: { id: attempt.id } })).toMatchObject({
      status: 'FAILED',
      failureReason: 'MOCK_FAILED',
    })
    const order = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(order.paidAt).toBeNull()

    const retry = await request(app)
      .post('/api/v1/orders/1/payments')
      .set(auth(customerToken))
      .expect(201)
    expect(retry.body.id).not.toBe(attempt.id)

    await request(app)
      .post(`/api/v1/payments/mock/${retry.body.txnRef}/complete`)
      .type('form')
      .send({ result: 'success' })
      .expect(200)

    const paid = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(paid.status).toBe('CONFIRMED')
    expect(paid.paidAt).not.toBeNull()
    expect(await prisma.payment.count({ where: { orderId: 1, status: 'SUCCEEDED' } })).toBe(1)
  })

  it('404s for an unknown reference and for an attempt the mock did not create', async () => {
    const attempt = await placeOnlineOrder()
    await prisma.payment.create({
      data: {
        orderId: 1,
        provider: 'VNPAY',
        amount: TOTAL,
        txnRef: 'STYVNPAYATTEMPT01',
      },
    })

    await request(app).get('/api/v1/payments/mock/STYKHONGCO').expect(404)
    await request(app)
      .post('/api/v1/payments/mock/STYKHONGCO/complete')
      .type('form')
      .send({ result: 'success' })
      .expect(404)

    await request(app).get('/api/v1/payments/mock/STYVNPAYATTEMPT01').expect(404)
    await request(app).get(`/api/v1/payments/mock/${attempt.txnRef}`).expect(200)
  })

  it('rejects a submit whose result field is missing or unknown', async () => {
    const attempt = await placeOnlineOrder()
    const response = await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'maybe' })
    expect(response.status).toBe(400)
    expect(response.body.error.code).toBe('VALIDATION_FAILED')
    expect(await prisma.order.findUniqueOrThrow({ where: { id: 1 } })).toMatchObject({
      paidAt: null,
    })
  })
})

describe('GET /payments/:txnRef', () => {
  it('returns the attempt to its owner and to an admin', async () => {
    const attempt = await placeOnlineOrder()

    const mine = await request(app)
      .get(`/api/v1/payments/${attempt.txnRef}`)
      .set(auth(customerToken))
      .expect(200)
    expect(mine.body).toMatchObject({
      txnRef: attempt.txnRef,
      provider: 'MOCK',
      status: 'PENDING',
      amount: TOTAL,
      orderId: 1,
      orderCode: expect.stringMatching(/^STY-\d{6}-[A-Z0-9]{4}$/),
      failureReason: null,
    })

    const admin = await request(app)
      .get(`/api/v1/payments/${attempt.txnRef}`)
      .set(auth(adminToken))
      .expect(200)
    expect(admin.body.txnRef).toBe(attempt.txnRef)

    const stranger = await request(app)
      .get(`/api/v1/payments/${attempt.txnRef}`)
      .set(auth(otherToken))
    expect(stranger.status).toBe(404)

    const anonymous = await request(app).get(`/api/v1/payments/${attempt.txnRef}`)
    expect(anonymous.status).toBe(401)
  })
})

describe('confirm service', () => {
  it('fails the attempt on an amount mismatch and keeps the order unpaid', async () => {
    const attempt = await placeOnlineOrder()

    const result = await confirmPayment({ txnRef: attempt.txnRef, amount: TOTAL - 1 })
    expect(result).toEqual({ outcome: 'AMOUNT_MISMATCH', amount: TOTAL })

    expect(await prisma.payment.findUniqueOrThrow({ where: { id: attempt.id } })).toMatchObject({
      status: 'FAILED',
      failureReason: 'AMOUNT_MISMATCH',
    })
    const order = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(order.paidAt).toBeNull()
    expect(order.status).toBe('PENDING')
  })

  it('acknowledges a late callback without touching a settled attempt', async () => {
    const attempt = await placeOnlineOrder()
    await confirmPayment({ txnRef: attempt.txnRef, amount: TOTAL })
    const paid = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })

    const late = await confirmPayment({ txnRef: attempt.txnRef, amount: TOTAL })
    expect(late.outcome).toBe('ALREADY_SUCCEEDED')

    const reloaded = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(reloaded.paidAt?.toISOString()).toBe(paid.paidAt?.toISOString())
  })

  it('records the money on a cancelled order without reviving it', async () => {
    const attempt = await placeOnlineOrder()
    await prisma.order.update({ where: { id: 1 }, data: { status: 'CANCELLED' } })

    await request(app)
      .post(`/api/v1/payments/mock/${attempt.txnRef}/complete`)
      .type('form')
      .send({ result: 'success' })
      .expect(200)

    const order = await prisma.order.findUniqueOrThrow({ where: { id: 1 } })
    expect(order.status).toBe('CANCELLED')
    expect(order.paidAt).not.toBeNull()
  })

  it('reports an unknown reference as NOT_FOUND', async () => {
    const result = await confirmPayment({ txnRef: 'STYKHONGCO', amount: TOTAL })
    expect(result).toEqual({ outcome: 'NOT_FOUND', amount: null })
  })
})
