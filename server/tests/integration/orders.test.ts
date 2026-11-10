import { randomUUID } from 'node:crypto'
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
let adminId: number
let customerToken: string
let otherToken: string
let adminToken: string
let receiptSequence = 0

beforeEach(async () => {
  await resetDb()
  const customer = await createUser({ email: 'an@example.com', fullName: 'Nguyễn Văn An' })
  const other = await createUser({ email: 'binh@example.com', fullName: 'Trần Bình' })
  const admin = await createUser({
    email: 'admin@example.com',
    fullName: 'Quản trị viên',
    role: 'ADMIN',
  })
  customerId = customer.id
  otherId = other.id
  adminId = admin.id
  customerToken = signAccessToken(customer.id, customer.role)
  otherToken = signAccessToken(other.id, other.role)
  adminToken = signAccessToken(admin.id, admin.role)
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
      // 518,000 reaches the free-shipping threshold from .env.test.
      shippingFee: 0,
      total: 398_000 + 120_000,
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
    expect(cart.body).toEqual({
      id: expect.any(Number),
      items: [],
      itemCount: 0,
      subtotal: 0,
      shippingFee: 0,
    })
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

function patchStatus(orderId: number, status: string) {
  return request(app)
    .patch(`/api/v1/admin/orders/${orderId}/status`)
    .set(auth(adminToken))
    .send({ status })
}

function cancelOrder(token: string, orderId: number) {
  return request(app).post(`/api/v1/orders/${orderId}/cancel`).set(auth(token))
}

async function placeOrder(
  token: string,
  addressId: number,
  variantId: number,
  quantity = 1,
  paymentMethod: 'COD' | 'ONLINE' = 'COD',
) {
  await addToCart(token, variantId, quantity)
  const response = await checkout(token, {
    clientOrderId: randomUUID(),
    addressId,
    paymentMethod,
  })
  expect(response.status).toBe(201)
  return response.body.order as { id: number; code: string; status: string; total: number }
}

async function singleVariantProduct(price = 100_000, stock = 10) {
  const product = await createProduct({
    variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price, stock }],
  })
  return product.variants[0]
}

describe('customer order history', () => {
  it('lists the caller orders with the derived status and the summed item count', async () => {
    const product = await createProduct({
      variants: [
        { sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 10 },
        { sku: 'AT-TRANG-L', size: 'L', color: 'Trắng', price: 120_000, stock: 10 },
      ],
    })
    const address = await createAddress(customerId)
    await addToCart(customerToken, variantBySku(product, 'AT-DEN-M').id, 2)
    await addToCart(customerToken, variantBySku(product, 'AT-TRANG-L').id, 1)
    const placed = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: address.id,
      paymentMethod: 'COD',
    })

    const list = await request(app).get('/api/v1/orders').set(auth(customerToken))

    expect(list.status).toBe(200)
    expect(list.body).toEqual({
      items: [
        {
          id: placed.body.order.id,
          code: placed.body.order.code,
          status: 'PENDING',
          paymentMethod: 'COD',
          paymentStatus: 'UNPAID',
          total: 320_000 + SHIPPING_FEE,
          itemCount: 3,
          createdAt: expect.any(String),
        },
      ],
      page: 1,
      limit: 20,
      total: 1,
    })

    const other = await request(app).get('/api/v1/orders').set(auth(otherToken))
    expect(other.body).toEqual({ items: [], page: 1, limit: 20, total: 0 })

    const delivered = await request(app)
      .get('/api/v1/orders?status=DELIVERED')
      .set(auth(customerToken))
    expect(delivered.body.total).toBe(0)
  })

  it('sorts newest first by default, oldest on request and pages stably', async () => {
    const variant = await singleVariantProduct(100_000, 10)
    const address = await createAddress(customerId)
    const first = await placeOrder(customerToken, address.id, variant.id)
    const second = await placeOrder(customerToken, address.id, variant.id)

    await prisma.order.update({
      where: { id: first.id },
      data: { createdAt: new Date('2026-10-01T00:00:00.000Z') },
    })
    await prisma.order.update({
      where: { id: second.id },
      data: { createdAt: new Date('2026-10-02T00:00:00.000Z') },
    })

    const newest = await request(app).get('/api/v1/orders').set(auth(customerToken))
    expect(newest.body.items.map((item: { id: number }) => item.id)).toEqual([second.id, first.id])

    const oldest = await request(app).get('/api/v1/orders?sort=oldest').set(auth(customerToken))
    expect(oldest.body.items.map((item: { id: number }) => item.id)).toEqual([first.id, second.id])

    const secondPage = await request(app)
      .get('/api/v1/orders?limit=1&page=2')
      .set(auth(customerToken))
    expect(secondPage.body).toEqual({
      items: [expect.objectContaining({ id: first.id })],
      page: 2,
      limit: 1,
      total: 2,
    })

    const pastTheEnd = await request(app)
      .get('/api/v1/orders?limit=1&page=9')
      .set(auth(customerToken))
    expect(pastTheEnd.body).toEqual({ items: [], page: 9, limit: 1, total: 2 })
  })

  it('returns the order detail with snapshots and hides another customer order', async () => {
    const variant = await singleVariantProduct(199_000, 5)
    const address = await createAddress(customerId)
    const placed = await placeOrder(customerToken, address.id, variant.id, 2)

    const own = await request(app)
      .get(`/api/v1/orders/${placed.id}`)
      .set(auth(customerToken))

    expect(own.status).toBe(200)
    expect(own.body).toEqual(
      expect.objectContaining({
        id: placed.id,
        code: placed.code,
        status: 'PENDING',
        paymentMethod: 'COD',
        paymentStatus: 'UNPAID',
        subtotal: 398_000,
        shippingFee: SHIPPING_FEE,
        total: 428_000,
        recipientName: 'Nguyễn Văn An',
        items: [
          expect.objectContaining({
            variantId: variant.id,
            sku: 'AT-DEN-M',
            size: 'M',
            color: 'Đen',
            unitPrice: 199_000,
            quantity: 2,
            lineTotal: 398_000,
          }),
        ],
      }),
    )
    expect(own.body.items[0]).not.toHaveProperty('unitCost')

    const foreign = await request(app)
      .get(`/api/v1/orders/${placed.id}`)
      .set(auth(otherToken))
    expect(foreign.status).toBe(404)
    expect(foreign.body.error.code).toBe('NOT_FOUND')
  })
})

describe('customer cancel', () => {
  it('restocks every line, appends CANCEL_RESTOCK movements and is applied once', async () => {
    const product = await createProduct({
      variants: [
        { sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 100_000, stock: 5 },
        { sku: 'AT-TRANG-L', size: 'L', color: 'Trắng', price: 120_000, stock: 4 },
      ],
    })
    const tee = variantBySku(product, 'AT-DEN-M')
    const shirt = variantBySku(product, 'AT-TRANG-L')
    const address = await createAddress(customerId)
    await addToCart(customerToken, tee.id, 2)
    await addToCart(customerToken, shirt.id, 1)
    const placed = await checkout(customerToken, {
      clientOrderId: CLIENT_ORDER_ID,
      addressId: address.id,
      paymentMethod: 'COD',
    })
    const orderId = placed.body.order.id

    const response = await cancelOrder(customerToken, orderId)

    expect(response.status).toBe(200)
    expect(response.body).toEqual(expect.objectContaining({ id: orderId, status: 'CANCELLED' }))

    const stocks = await prisma.productVariant.findMany({
      where: { id: { in: [tee.id, shirt.id] } },
      orderBy: { id: 'asc' },
      select: { id: true, stock: true },
    })
    expect(stocks).toEqual([
      { id: tee.id, stock: 5 },
      { id: shirt.id, stock: 4 },
    ])

    const restock = await prisma.stockMovement.findMany({
      where: { refType: 'ORDER', refId: orderId, reason: 'CANCEL_RESTOCK' },
      orderBy: { variantId: 'asc' },
    })
    expect(restock).toEqual([
      expect.objectContaining({ variantId: tee.id, delta: 2, unitCost: null }),
      expect.objectContaining({ variantId: shirt.id, delta: 1, unitCost: null }),
    ])

    const repeated = await cancelOrder(customerToken, orderId)
    expect(repeated.status).toBe(409)
    expect(repeated.body.error).toEqual({
      code: 'INVALID_ORDER_STATE',
      message: expect.any(String),
      details: { current: 'CANCELLED', target: 'CANCELLED' },
    })

    const movements = await prisma.stockMovement.count({
      where: { refType: 'ORDER', refId: orderId, reason: 'CANCEL_RESTOCK' },
    })
    expect(movements).toBe(2)
  })

  it('refuses a paid order and an order owned by somebody else', async () => {
    const variant = await singleVariantProduct()
    const address = await createAddress(customerId)
    const paid = await placeOrder(customerToken, address.id, variant.id)
    await prisma.order.update({ where: { id: paid.id }, data: { paidAt: new Date() } })

    const paidCancel = await cancelOrder(customerToken, paid.id)
    expect(paidCancel.status).toBe(409)
    expect(paidCancel.body.error.code).toBe('ORDER_ALREADY_PAID')

    const owned = await placeOrder(customerToken, address.id, variant.id)
    const foreign = await cancelOrder(otherToken, owned.id)
    expect(foreign.status).toBe(404)
    expect(foreign.body.error.code).toBe('NOT_FOUND')

    const stillPending = await prisma.order.findUniqueOrThrow({ where: { id: owned.id } })
    expect(stillPending.status).toBe('PENDING')
  })
})

describe('admin orders', () => {
  it('requires the admin role', async () => {
    const response = await request(app).get('/api/v1/admin/orders').set(auth(customerToken))
    expect(response.status).toBe(403)
    expect(response.body.error.code).toBe('FORBIDDEN')
  })

  it('filters summaries by status, code, recipient and Vietnam calendar day', async () => {
    const variant = await singleVariantProduct(100_000, 10)
    const anAddress = await createAddress(customerId, {
      recipientName: 'Nguyễn Văn An',
      phone: '0901234567',
    })
    const binhAddress = await createAddress(otherId, {
      recipientName: 'Trần Bình',
      phone: '0912345678',
    })
    const first = await placeOrder(customerToken, anAddress.id, variant.id)
    const second = await placeOrder(otherToken, binhAddress.id, variant.id)
    const third = await placeOrder(customerToken, anAddress.id, variant.id)
    const fourth = await placeOrder(customerToken, anAddress.id, variant.id)

    // Vietnam calendar day 2026-10-01 is [Sep 30 17:00Z, Oct 1 17:00Z): first and fourth
    // sit one millisecond outside; second and third sit exactly on the inclusive ends.
    await prisma.order.update({
      where: { id: first.id },
      data: { createdAt: new Date('2026-09-30T16:59:59.999Z') },
    })
    await prisma.order.update({
      where: { id: second.id },
      data: { createdAt: new Date('2026-09-30T17:00:00.000Z') },
    })
    await prisma.order.update({
      where: { id: third.id },
      data: { createdAt: new Date('2026-10-01T16:59:59.999Z') },
    })
    await prisma.order.update({
      where: { id: fourth.id },
      data: { createdAt: new Date('2026-10-01T17:00:00.000Z') },
    })

    const all = await request(app).get('/api/v1/admin/orders').set(auth(adminToken))
    expect(all.body.total).toBe(4)
    expect(all.body.items).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          id: third.id,
          customerName: 'Nguyễn Văn An',
          recipientName: 'Nguyễn Văn An',
          recipientPhone: '0901234567',
          itemCount: 1,
        }),
      ]),
    )

    const byCode = await request(app)
      .get(`/api/v1/admin/orders?q=${encodeURIComponent(second.code)}`)
      .set(auth(adminToken))
    expect(byCode.body.items.map((item: { id: number }) => item.id)).toEqual([second.id])

    const byPhone = await request(app)
      .get('/api/v1/admin/orders?q=0912345678')
      .set(auth(adminToken))
    expect(byPhone.body.items.map((item: { id: number }) => item.id)).toEqual([second.id])

    const byDay = await request(app)
      .get('/api/v1/admin/orders?from=2026-10-01&to=2026-10-01')
      .set(auth(adminToken))
    expect(byDay.body.items.map((item: { id: number }) => item.id)).toEqual([third.id, second.id])

    // Oct 2 in Vietnam starts at Oct 1 17:00Z, so only the fourth order lands there.
    const nextDay = await request(app)
      .get('/api/v1/admin/orders?from=2026-10-02&to=2026-10-02')
      .set(auth(adminToken))
    expect(nextDay.body.items.map((item: { id: number }) => item.id)).toEqual([fourth.id])

    const invalidWindow = await request(app)
      .get('/api/v1/admin/orders?from=2026-10-02&to=2026-10-01')
      .set(auth(adminToken))
    expect(invalidWindow.status).toBe(400)
    expect(invalidWindow.body.error.code).toBe('VALIDATION_FAILED')

    const confirmed = await patchStatus(third.id, 'CONFIRMED')
    expect(confirmed.status).toBe(200)

    const byStatus = await request(app)
      .get('/api/v1/admin/orders?status=CONFIRMED')
      .set(auth(adminToken))
    expect(byStatus.body.items.map((item: { id: number }) => item.id)).toEqual([third.id])

    const stillPending = await request(app)
      .get('/api/v1/admin/orders?status=PENDING')
      .set(auth(adminToken))
    expect(stillPending.body.items.map((item: { id: number }) => item.id)).toEqual([
      fourth.id,
      second.id,
      first.id,
    ])
  })

  it('returns the admin detail with customer, unit cost and every attempt', async () => {
    const product = await createProduct({
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 199_000, stock: 5 }],
    })
    const variant = product.variants[0]
    await bookReceipt(adminId, [{ variantId: variant.id, quantity: 10, unitCost: 110_000 }])
    const address = await createAddress(customerId)
    const placed = await placeOrder(customerToken, address.id, variant.id, 2, 'ONLINE')

    const response = await request(app)
      .get(`/api/v1/admin/orders/${placed.id}`)
      .set(auth(adminToken))

    expect(response.status).toBe(200)
    expect(response.body).toEqual(
      expect.objectContaining({
        id: placed.id,
        customer: {
          id: customerId,
          email: 'an@example.com',
          fullName: 'Nguyễn Văn An',
          phone: null,
        },
        items: [
          expect.objectContaining({
            variantId: variant.id,
            unitPrice: 199_000,
            unitCost: 110_000,
            quantity: 2,
          }),
        ],
        paidAt: null,
        paymentStatus: 'UNPAID',
      }),
    )
    expect(response.body.payments).toEqual([
      expect.objectContaining({ provider: 'MOCK', status: 'PENDING', amount: 428_000 }),
    ])
    expect(response.body.payments[0]).not.toHaveProperty('paymentUrl')

    const missing = await request(app).get('/api/v1/admin/orders/999999').set(auth(adminToken))
    expect(missing.status).toBe(404)
  })

  it('walks PENDING to DELIVERED, settling COD once at delivery and keeping DELIVERED terminal', async () => {
    const variant = await singleVariantProduct()
    const address = await createAddress(customerId)
    const placed = await placeOrder(customerToken, address.id, variant.id)

    const confirmed = await patchStatus(placed.id, 'CONFIRMED')
    expect(confirmed.status).toBe(200)
    expect(confirmed.body.status).toBe('CONFIRMED')
    expect(confirmed.body.paidAt).toBeNull()

    const shipping = await patchStatus(placed.id, 'SHIPPING')
    expect(shipping.status).toBe(200)
    expect(shipping.body.status).toBe('SHIPPING')

    const delivered = await patchStatus(placed.id, 'DELIVERED')
    expect(delivered.status).toBe(200)
    expect(delivered.body.status).toBe('DELIVERED')
    expect(delivered.body.paidAt).toEqual(expect.any(String))
    expect(delivered.body.paymentStatus).toBe('PAID')

    const terminal = await patchStatus(placed.id, 'CANCELLED')
    expect(terminal.status).toBe(409)
    expect(terminal.body.error).toEqual({
      code: 'INVALID_ORDER_STATE',
      message: expect.any(String),
      details: { current: 'DELIVERED', target: 'CANCELLED' },
    })
  })

  it('rejects an illegal transition and an unknown status', async () => {
    const variant = await singleVariantProduct()
    const address = await createAddress(customerId)
    const placed = await placeOrder(customerToken, address.id, variant.id)

    const skip = await patchStatus(placed.id, 'SHIPPING')
    expect(skip.status).toBe(409)
    expect(skip.body.error).toEqual({
      code: 'INVALID_ORDER_STATE',
      message: expect.any(String),
      details: { current: 'PENDING', target: 'SHIPPING' },
    })

    const same = await patchStatus(placed.id, 'PENDING')
    expect(same.status).toBe(409)

    const bogus = await patchStatus(placed.id, 'REFUNDED')
    expect(bogus.status).toBe(400)
    expect(bogus.body.error.code).toBe('VALIDATION_FAILED')

    const missing = await patchStatus(999999, 'CONFIRMED')
    expect(missing.status).toBe(404)
  })

  it('cancels from PENDING and CONFIRMED with a once-only restock and refuses a paid order', async () => {
    const variant = await singleVariantProduct(100_000, 5)
    const address = await createAddress(customerId)

    const fromPending = await placeOrder(customerToken, address.id, variant.id, 2)
    const pendingCancel = await patchStatus(fromPending.id, 'CANCELLED')
    expect(pendingCancel.status).toBe(200)
    expect(pendingCancel.body.status).toBe('CANCELLED')

    const fromConfirmed = await placeOrder(customerToken, address.id, variant.id, 1)
    const confirmed = await patchStatus(fromConfirmed.id, 'CONFIRMED')
    expect(confirmed.status).toBe(200)
    const confirmedCancel = await patchStatus(fromConfirmed.id, 'CANCELLED')
    expect(confirmedCancel.status).toBe(200)

    const stock = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(stock.stock).toBe(5)

    const again = await patchStatus(fromConfirmed.id, 'CANCELLED')
    expect(again.status).toBe(409)

    const pendingMovements = await prisma.stockMovement.findMany({
      where: { refType: 'ORDER', refId: fromPending.id, reason: 'CANCEL_RESTOCK' },
    })
    expect(pendingMovements).toEqual([
      expect.objectContaining({ delta: 2, variantId: variant.id }),
    ])
    const confirmedMovements = await prisma.stockMovement.findMany({
      where: { refType: 'ORDER', refId: fromConfirmed.id, reason: 'CANCEL_RESTOCK' },
    })
    expect(confirmedMovements).toEqual([
      expect.objectContaining({ delta: 1, variantId: variant.id }),
    ])

    const paid = await placeOrder(customerToken, address.id, variant.id)
    await prisma.order.update({ where: { id: paid.id }, data: { paidAt: new Date() } })
    const paidCancel = await patchStatus(paid.id, 'CANCELLED')
    expect(paidCancel.status).toBe(409)
    expect(paidCancel.body.error.code).toBe('ORDER_ALREADY_PAID')

    const afterReject = await prisma.productVariant.findUniqueOrThrow({ where: { id: variant.id } })
    expect(afterReject.stock).toBe(4)
    const untouched = await prisma.order.findUniqueOrThrow({ where: { id: paid.id } })
    expect(untouched.status).toBe('PENDING')
  })
})
