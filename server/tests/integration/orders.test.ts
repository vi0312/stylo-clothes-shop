import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { prisma } from '../../src/lib/prisma.js'
import { createProduct } from '../factories/catalog.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()

const SHIPPING_FEE = 30_000
const CLIENT_ORDER_ID = '3f0c2b7e-8a41-4f6d-9c2e-1b5a7d9e0f12'
const OTHER_CLIENT_ORDER_ID = '9b6d1f3a-2c7e-4a58-8d41-0f2e6b7c9a13'
const DEFAULT_LINE = '12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM'

let customerId: number
let otherId: number
let customerToken: string
let otherToken: string
let receiptSequence = 0

beforeEach(async () => {
  await resetDb()
  const customer = await createUser({ email: 'an@example.com' })
  const other = await createUser({ email: 'binh@example.com' })
  customerId = customer.id
  otherId = other.id
  customerToken = signAccessToken(customer.id, customer.role)
  otherToken = signAccessToken(other.id, other.role)
})

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

async function createAddress(
  userId: number,
  overrides: { recipientName?: string; phone?: string; line?: string } = {},
) {
  return prisma.address.create({
    data: {
      userId,
      recipientName: overrides.recipientName ?? 'Nguyễn Văn An',
      phone: overrides.phone ?? '0901234567',
      line: overrides.line ?? DEFAULT_LINE,
    },
  })
}

async function addToCart(token: string, variantId: number, quantity: number) {
  const response = await request(app)
    .post('/api/v1/cart/items')
    .set(auth(token))
    .send({ variantId, quantity })
  expect(response.status).toBe(200)
}

async function checkout(token: string, body: Record<string, unknown>) {
  return request(app).post('/api/v1/orders').set(auth(token)).send(body)
}

function variantBySku(product: Awaited<ReturnType<typeof createProduct>>, sku: string) {
  const found = product.variants.find((variant) => variant.sku === sku)
  if (!found) throw new Error(`missing variant ${sku}`)
  return found
}

async function bookReceipt(
  createdBy: number,
  items: { variantId: number; quantity: number; unitCost: number }[],
) {
  receiptSequence += 1
  return prisma.inventoryReceipt.create({
    data: {
      code: `PN-TEST-${receiptSequence}`,
      supplier: 'Nhà cung cấp thử nghiệm',
      totalCost: items.reduce((sum, item) => sum + item.quantity * item.unitCost, 0),
      createdBy,
      items: { create: items },
    },
  })
}

describe('checkout', () => {
  it('requires an access token', async () => {
    const response = await request(app).post('/api/v1/orders').send({
      clientOrderId: CLIENT_ORDER_ID,
      addressId: 1,
      paymentMethod: 'COD',
    })

    expect(response.status).toBe(401)
    expect(response.body.error.code).toBe('UNAUTHORIZED')
  })

  it('rejects client-sent items, prices and a malformed clientOrderId', async () => {
    const shipAddress = await createAddress(customerId)

    const withItems = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
      items: [{ variantId: 1, quantity: 1 }],
      total: 1,
    })
    expect(withItems.status).toBe(400)
    expect(withItems.body.error.code).toBe('VALIDATION_FAILED')

    const malformedId = await checkout(customerToken, {
      clientOrderId: 'khong-phai-uuid',
      addressId: shipAddress.id,
      paymentMethod: 'COD',
    })
    expect(malformedId.status).toBe(400)
    expect(malformedId.body.error.code).toBe('VALIDATION_FAILED')
    expect(await prisma.order.count()).toBe(0)
  })

  it('rejects an empty cart', async () => {
    const response = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: 1,
      paymentMethod: 'COD',
    })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('CART_EMPTY')
  })

  it('rejects an address owned by another customer and keeps the cart', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 3 }],
    })
    await addToCart(customerToken, product.variants[0].id, 1)
    const otherAddress = await createAddress(otherId)

    const response = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: otherAddress.id,
      paymentMethod: 'COD',
    })

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
    expect(await prisma.order.count()).toBe(0)

    const cart = await request(app).get('/api/v1/cart').set(auth(customerToken))
    expect(cart.body.items).toHaveLength(1)
  })

  it('creates one order with snapshots, cost, stock deduction and a sale movement', async () => {
    const product = await createProduct({
      name: 'Áo thun cotton',
      variants: [
        { sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 199_000, stock: 5 },
        { sku: 'AT-TRANG-L', size: 'L', color: 'Trắng', price: 120_000, stock: 4 },
      ],
    })
    const tee = variantBySku(product, 'AT-DEN-M')
    const shirt = variantBySku(product, 'AT-TRANG-L')
    const admin = await createUser({ email: 'admin@stylo.test', role: 'ADMIN' })
    // 10 × 100,000 + 10 × 120,000 → weighted average 110,000. The shirt was never received.
    await bookReceipt(admin.id, [
      { variantId: tee.id, quantity: 10, unitCost: 100_000 },
      { variantId: tee.id, quantity: 10, unitCost: 120_000 },
    ])
    const shipAddress = await createAddress(customerId)
    await addToCart(customerToken, tee.id, 2)
    await addToCart(customerToken, shirt.id, 1)

    const response = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
      note: 'Giao giờ hành chính',
    })

    expect(response.status).toBe(201)
    expect(response.body.payment).toBeNull()

    const order = response.body.order
    expect(order).toMatchObject({
      id: expect.any(Number),
      code: expect.stringMatching(/^STY-\d{6}-[A-Z0-9]{4}$/),
      status: 'PENDING',
      paymentMethod: 'COD',
      paymentStatus: 'UNPAID',
      subtotal: 398_000 + 120_000,
      shippingFee: SHIPPING_FEE,
      total: 398_000 + 120_000 + SHIPPING_FEE,
      note: 'Giao giờ hành chính',
      recipientName: 'Nguyễn Văn An',
      recipientPhone: '0901234567',
      shippingAddress: DEFAULT_LINE,
      paidAt: null,
      createdAt: expect.stringMatching(/Z$/),
      updatedAt: expect.stringMatching(/Z$/),
    })
    expect(order.items).toEqual([
      {
        id: expect.any(Number),
        productId: product.id,
        variantId: tee.id,
        productName: 'Áo thun cotton',
        sku: 'AT-DEN-M',
        size: 'M',
        color: 'Đen',
        unitPrice: 199_000,
        quantity: 2,
        lineTotal: 398_000,
      },
      {
        id: expect.any(Number),
        productId: product.id,
        variantId: shirt.id,
        productName: 'Áo thun cotton',
        sku: 'AT-TRANG-L',
        size: 'L',
        color: 'Trắng',
        unitPrice: 120_000,
        quantity: 1,
        lineTotal: 120_000,
      },
    ])

    const costs = await prisma.orderItem.findMany({
      where: { orderId: order.id },
      orderBy: { id: 'asc' },
      select: { unitCost: true },
    })
    expect(costs).toEqual([{ unitCost: 110_000 }, { unitCost: null }])

    const stocks = await prisma.productVariant.findMany({
      where: { id: { in: [tee.id, shirt.id] } },
      orderBy: { id: 'asc' },
      select: { id: true, stock: true },
    })
    expect(stocks).toEqual([
      { id: tee.id, stock: 3 },
      { id: shirt.id, stock: 3 },
    ])

    const movements = await prisma.stockMovement.findMany({
      where: { refType: 'ORDER', refId: order.id },
      orderBy: { variantId: 'asc' },
    })
    expect(movements).toEqual([
      expect.objectContaining({
        variantId: tee.id,
        delta: -2,
        reason: 'SALE',
        unitCost: null,
      }),
      expect.objectContaining({
        variantId: shirt.id,
        delta: -1,
        reason: 'SALE',
        unitCost: null,
      }),
    ])

    // erd.md reconciliation: stock == opening stock + Σ movement deltas.
    const ledger = await prisma.stockMovement.aggregate({
      where: { variantId: tee.id },
      _sum: { delta: true },
    })
    expect(stocks[0].stock).toBe(5 + (ledger._sum.delta ?? 0))

    const cart = await request(app).get('/api/v1/cart').set(auth(customerToken))
    expect(cart.body).toEqual({ id: expect.any(Number), items: [], itemCount: 0, subtotal: 0 })
  })

  it('stores an empty note as null', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 2 }],
    })
    await addToCart(customerToken, product.variants[0].id, 1)
    const shipAddress = await createAddress(customerId)

    const response = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
      note: '',
    })

    expect(response.status).toBe(201)
    expect(response.body.order.note).toBeNull()
  })

  it('returns the existing order on a repeated clientOrderId without deducting stock twice', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 3 }],
    })
    const variant = product.variants[0]
    const shipAddress = await createAddress(customerId)
    await addToCart(customerToken, variant.id, 1)

    const first = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
    })
    expect(first.status).toBe(201)

    const second = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
    })
    expect(second.status).toBe(200)
    expect(second.body.order).toEqual(first.body.order)

    expect(await prisma.order.count()).toBe(1)
    expect(await prisma.stockMovement.count({ where: { refType: 'ORDER' } })).toBe(1)
    const stock = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(stock.stock).toBe(2)
  })

  it('scopes the idempotency key to the customer', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 4 }],
    })
    const variant = product.variants[0]
    const anAddress = await createAddress(customerId)
    const binhAddress = await createAddress(otherId)
    await addToCart(customerToken, variant.id, 1)
    await addToCart(otherToken, variant.id, 1)

    const first = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: anAddress.id,
      paymentMethod: 'COD',
    })
    const second = await checkout(otherToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: binhAddress.id,
      paymentMethod: 'COD',
    })

    expect(first.status).toBe(201)
    expect(second.status).toBe(201)
    expect(second.body.order.id).not.toBe(first.body.order.id)
    expect(await prisma.order.count()).toBe(2)
    const stock = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(stock.stock).toBe(2)
  })

  it('rejects an item whose product was deactivated after it entered the cart', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AO-CU-S', size: 'S', color: 'Đen', price: 100_000, stock: 5 }],
    })
    const variant = product.variants[0]
    const shipAddress = await createAddress(customerId)
    await addToCart(customerToken, variant.id, 1)
    await prisma.product.update({ where: { id: product.id }, data: { status: 'INACTIVE' } })

    const response = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
    })

    expect(response.status).toBe(409)
    expect(response.body.error).toMatchObject({
      code: 'ITEM_UNAVAILABLE',
      details: [{ sku: 'AO-CU-S', reason: 'PRODUCT_INACTIVE' }],
    })
    expect(await prisma.order.count()).toBe(0)
    expect(await prisma.stockMovement.count()).toBe(0)
    const stock = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(stock.stock).toBe(5)

    const cart = await request(app).get('/api/v1/cart').set(auth(customerToken))
    expect(cart.body.items).toHaveLength(1)
  })

  it('rejects an out-of-stock cart, lists every short variant and writes nothing', async () => {
    const product = await createProduct({
      variants: [
        { sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 5 },
        { sku: 'AT-TRANG-L', size: 'L', color: 'Trắng', price: 120_000, stock: 5 },
      ],
    })
    const tee = variantBySku(product, 'AT-DEN-M')
    const shirt = variantBySku(product, 'AT-TRANG-L')
    const shipAddress = await createAddress(customerId)
    await addToCart(customerToken, tee.id, 2)
    await addToCart(customerToken, shirt.id, 1)
    await prisma.productVariant.update({ where: { id: tee.id }, data: { stock: 1 } })
    await prisma.productVariant.update({ where: { id: shirt.id }, data: { stock: 0 } })

    const response = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
    })

    expect(response.status).toBe(409)
    expect(response.body.error.code).toBe('OUT_OF_STOCK')
    expect(response.body.error.details).toEqual([
      { sku: 'AT-DEN-M', requested: 2, available: 1 },
      { sku: 'AT-TRANG-L', requested: 1, available: 0 },
    ])
    expect(await prisma.order.count()).toBe(0)
    expect(await prisma.stockMovement.count()).toBe(0)

    const cart = await request(app).get('/api/v1/cart').set(auth(customerToken))
    expect(cart.body.items).toHaveLength(2)
  })

  it('sells the last unit once when two customers check out concurrently', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 1 }],
    })
    const variant = product.variants[0]
    const anAddress = await createAddress(customerId)
    const binhAddress = await createAddress(otherId)
    await addToCart(customerToken, variant.id, 1)
    await addToCart(otherToken, variant.id, 1)

    const [first, second] = await Promise.all([
      checkout(customerToken, {
        clientOrderId: CLIENT_ORDER_ID,
        addressId: anAddress.id,
        paymentMethod: 'COD',
      }),
      checkout(otherToken, {
        clientOrderId: OTHER_CLIENT_ORDER_ID,
        addressId: binhAddress.id,
        paymentMethod: 'COD',
      }),
    ])

    expect([first.status, second.status].sort((left, right) => left - right)).toEqual([201, 409])
    const rejected = first.status === 409 ? first : second
    expect(rejected.body.error.code).toBe('OUT_OF_STOCK')
    expect(await prisma.order.count()).toBe(1)
    expect(await prisma.stockMovement.count({ where: { refType: 'ORDER' } })).toBe(1)
    const stock = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(stock.stock).toBe(0)
  })

  it('collapses simultaneous retries with one clientOrderId into a single order', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 4 }],
    })
    const variant = product.variants[0]
    const shipAddress = await createAddress(customerId)
    await addToCart(customerToken, variant.id, 2)

    const body = {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: shipAddress.id,
      paymentMethod: 'COD',
    }
    const [first, second] = await Promise.all([
      checkout(customerToken, body),
      checkout(customerToken, body),
    ])

    expect([first.status, second.status].sort((left, right) => left - right)).toEqual([200, 201])
    expect(first.body.order.id).toBe(second.body.order.id)
    expect(await prisma.order.count()).toBe(1)
    expect(await prisma.stockMovement.count({ where: { refType: 'ORDER' } })).toBe(1)
    const stock = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(stock.stock).toBe(2)
  })
})
