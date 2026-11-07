import { randomUUID } from 'node:crypto'
import request from 'supertest'
import { beforeEach, describe, expect, it } from 'vitest'
import { createApp } from '../../src/app.js'
import { signAccessToken } from '../../src/lib/jwt.js'
import { prisma } from '../../src/lib/prisma.js'
import type { OrderStatus, PaymentMethod } from '../../src/generated/prisma/client.js'
import { createProduct } from '../factories/catalog.js'
import { createUser } from '../factories/user.js'
import { resetDb } from '../helpers/resetDb.js'

const app = createApp()

let adminId: number
let adminToken: string
let customerId: number
let customerToken: string
let orderSeq = 0

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

function getReport(path: string, token = adminToken) {
  return request(app).get(`/api/v1/admin/reports/${path}`).set(auth(token))
}

interface ItemOptions {
  productId: number
  variantId: number
  productName: string
  variantSku: string
  size?: string
  color?: string
  unitPrice: number
  unitCost: number | null
  quantity: number
}

interface OrderOptions {
  status?: OrderStatus
  paymentMethod?: PaymentMethod
  paidAt?: Date | null
  createdAt?: Date
  total?: number
  items?: ItemOptions[]
}

async function createOrder(options: OrderOptions = {}) {
  orderSeq += 1
  const items = options.items ?? []
  const subtotal = items.reduce((sum, item) => sum + item.unitPrice * item.quantity, 0)

  return prisma.order.create({
    data: {
      code: `RPT-${String(orderSeq).padStart(4, '0')}`,
      clientOrderId: randomUUID(),
      userId: customerId,
      status: options.status ?? 'CONFIRMED',
      paymentMethod: options.paymentMethod ?? 'ONLINE',
      paidAt: options.paidAt ?? null,
      recipientName: 'Nguyễn Văn An',
      recipientPhone: '0901234567',
      shippingAddress: '12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM',
      subtotal,
      shippingFee: 0,
      total: options.total ?? subtotal,
      ...(options.createdAt ? { createdAt: options.createdAt } : {}),
      ...(items.length > 0
        ? {
            items: {
              create: items.map((item) => ({
                ...item,
                size: item.size ?? 'M',
                color: item.color ?? 'Đen',
              })),
            },
          }
        : {}),
    },
  })
}

async function createReceipt(createdAt: Date, lines: { variantId: number; quantity: number; unitCost: number }[]) {
  const totalCost = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0)
  return prisma.inventoryReceipt.create({
    data: {
      code: `PN-261101-${randomUUID().slice(0, 4).toUpperCase()}`,
      supplier: 'Xưởng may Bình Tân',
      totalCost,
      createdBy: adminId,
      createdAt,
      items: { create: lines },
    },
  })
}

/** docs/reports.md: a paid item without a cost snapshot must not exist in any seeded state. */
async function offendingPaidItems(): Promise<number> {
  const rows = await prisma.$queryRaw<{ offending: unknown }[]>`
    SELECT COUNT(*) AS offending
    FROM order_item oi
    JOIN \`order\` o ON o.id = oi.order_id
    WHERE o.paid_at IS NOT NULL AND oi.unit_cost IS NULL
  `
  return Number(rows[0]?.offending ?? 0)
}

async function seedProductWithVariant(name = 'Áo thun cotton', price = 199_000) {
  const product = await createProduct({
    name,
    variants: [{ sku: `SKU-${randomUUID().slice(0, 8)}`, size: 'M', color: 'Đen', price, stock: 50 }],
  })
  const variant = product.variants[0]
  return {
    productId: product.id,
    variantId: variant.id,
    productName: product.name,
    variantSku: variant.sku,
  }
}

beforeEach(async () => {
  await resetDb()
  orderSeq = 0
  const admin = await createUser({ email: 'admin@example.com', fullName: 'Quản trị viên', role: 'ADMIN' })
  const customer = await createUser({ email: 'an@example.com', fullName: 'Nguyễn Văn An' })
  adminId = admin.id
  adminToken = signAccessToken(admin.id, admin.role)
  customerId = customer.id
  customerToken = signAccessToken(customer.id, customer.role)
})

describe('access control', () => {
  it('rejects a customer with 403 FORBIDDEN and a missing token with 401', async () => {
    const forbidden = await getReport('revenue', customerToken)
    expect(forbidden.status).toBe(403)
    expect(forbidden.body.error.code).toBe('FORBIDDEN')

    const anonymous = await request(app).get('/api/v1/admin/reports/revenue')
    expect(anonymous.status).toBe(401)
    expect(anonymous.body.error.code).toBe('UNAUTHORIZED')
  })
})

describe('revenue', () => {
  it('buckets by Vietnamese paid date, zero-fills every day and excludes unpaid orders', async () => {
    const line = await seedProductWithVariant()
    const base = { items: [{ ...line, unitPrice: 100_000, unitCost: 60_000, quantity: 1 }] }

    await createOrder({ ...base, paidAt: new Date('2026-10-31T17:00:00.000Z'), total: 350_000 })
    await createOrder({ ...base, paidAt: new Date('2026-11-02T10:00:00.000Z'), total: 500_000 })
    await createOrder({ ...base, paidAt: new Date('2026-11-03T16:59:59.999Z'), total: 750_000 })
    await createOrder({
      ...base,
      status: 'DELIVERED',
      paymentMethod: 'COD',
      paidAt: new Date('2026-11-03T12:00:00.000Z'),
      total: 500_000,
    })

    // Outside the window on both ends, plus orders that never got paid.
    await createOrder({ ...base, paidAt: new Date('2026-10-31T16:59:59.999Z'), total: 999_000 })
    await createOrder({ ...base, paidAt: new Date('2026-11-03T17:00:00.000Z'), total: 999_000 })
    await createOrder({ ...base, status: 'PENDING', paidAt: null, total: 111_000 })
    await createOrder({ ...base, status: 'CANCELLED', paidAt: null, total: 222_000 })

    const response = await getReport('revenue?from=2026-11-01&to=2026-11-03')
    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      from: '2026-11-01',
      to: '2026-11-03',
      groupBy: 'day',
      totalRevenue: 2_100_000,
      totalOrders: 4,
      series: [
        { period: '2026-11-01', revenue: 350_000, orders: 1 },
        { period: '2026-11-02', revenue: 500_000, orders: 1 },
        { period: '2026-11-03', revenue: 1_250_000, orders: 2 },
      ],
    })
  })

  it('buckets by Vietnamese calendar month with empty months present', async () => {
    const line = await seedProductWithVariant()
    const base = { items: [{ ...line, unitPrice: 100_000, unitCost: 60_000, quantity: 1 }] }

    await createOrder({ ...base, paidAt: new Date('2026-09-20T05:00:00.000Z'), total: 200_000 })
    await createOrder({ ...base, paidAt: new Date('2026-11-05T16:00:00.000Z'), total: 300_000 })
    await createOrder({ ...base, paidAt: new Date('2026-11-05T17:00:00.000Z'), total: 999_000 })
    await createOrder({ ...base, paidAt: new Date('2026-09-14T16:59:59.999Z'), total: 999_000 })

    const response = await getReport('revenue?from=2026-09-15&to=2026-11-05&groupBy=month')
    expect(response.status).toBe(200)
    expect(response.body.series).toEqual([
      { period: '2026-09', revenue: 200_000, orders: 1 },
      { period: '2026-10', revenue: 0, orders: 0 },
      { period: '2026-11', revenue: 300_000, orders: 1 },
    ])
    expect(response.body.totalRevenue).toBe(500_000)
    expect(response.body.groupBy).toBe('month')
  })

  it('defaults to the last 30 Vietnamese days ending today', async () => {
    const response = await getReport('revenue')
    expect(response.status).toBe(200)
    expect(response.body.groupBy).toBe('day')
    expect(response.body.series).toHaveLength(30)
    expect(response.body.series[29].period).toBe(response.body.to)

    const dayMs = 24 * 60 * 60 * 1000
    const fromMs = Date.parse(`${response.body.from}T00:00:00.000Z`)
    const toMs = Date.parse(`${response.body.to}T00:00:00.000Z`)
    expect(toMs - fromMs).toBe(29 * dayMs)
  })

  it('rejects an inverted range and an unknown groupBy', async () => {
    const inverted = await getReport('revenue?from=2026-11-05&to=2026-11-01')
    expect(inverted.status).toBe(400)
    expect(inverted.body.error.code).toBe('VALIDATION_FAILED')

    const invalidGroup = await getReport('revenue?groupBy=week')
    expect(invalidGroup.status).toBe(400)
    expect(invalidGroup.body.error.code).toBe('VALIDATION_FAILED')
  })
})

describe('top products', () => {
  it('sorts by quantity then snapshot revenue, keeps inactive products and old names', async () => {
    const big = await createProduct({
      name: 'Quần jean',
      variants: [{ sku: 'QJ-XANH-32', size: '32', color: 'Xanh', price: 400_000, stock: 60 }],
    })
    const small = await createProduct({
      name: 'Áo thun cotton',
      variants: [{ sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 199_000, stock: 60 }],
    })
    const tie = await createProduct({
      name: 'Mũ lưỡi trai',
      variants: [{ sku: 'MLT-DEN', size: 'F', color: 'Đen', price: 100_000, stock: 60 }],
    })
    const retired = await createProduct({
      name: 'Áo khoác cũ',
      status: 'INACTIVE',
      variants: [{ sku: 'AK-XAM-L', size: 'L', color: 'Xám', price: 250_000, stock: 60 }],
    })

    const paidAt = new Date('2026-11-02T03:00:00.000Z')
    await createOrder({
      paidAt,
      items: [
        { productId: big.id, variantId: big.variants[0].id, productName: big.name, variantSku: big.variants[0].sku, unitPrice: 400_000, unitCost: 250_000, quantity: 12 },
        { productId: small.id, variantId: small.variants[0].id, productName: 'Áo thun cotton (cũ)', variantSku: small.variants[0].sku, unitPrice: 199_000, unitCost: 120_000, quantity: 6 },
        { productId: tie.id, variantId: tie.variants[0].id, productName: tie.name, variantSku: tie.variants[0].sku, unitPrice: 100_000, unitCost: 50_000, quantity: 6 },
        { productId: retired.id, variantId: retired.variants[0].id, productName: retired.name, variantSku: retired.variants[0].sku, unitPrice: 250_000, unitCost: 150_000, quantity: 1 },
      ],
    })

    // Later edits must not rewrite the snapshot name or price.
    await prisma.product.update({ where: { id: small.id }, data: { name: 'Áo thun cotton mới' } })
    await prisma.productVariant.update({ where: { id: small.variants[0].id }, data: { price: 999_000 } })

    const response = await getReport('top-products?from=2026-11-01&to=2026-11-03')
    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      from: '2026-11-01',
      to: '2026-11-03',
      items: [
        { productId: big.id, name: 'Quần jean', quantitySold: 12, revenue: 4_800_000 },
        { productId: small.id, name: 'Áo thun cotton (cũ)', quantitySold: 6, revenue: 1_194_000 },
        { productId: tie.id, name: 'Mũ lưỡi trai', quantitySold: 6, revenue: 600_000 },
        { productId: retired.id, name: 'Áo khoác cũ', quantitySold: 1, revenue: 250_000 },
      ],
    })
  })

  it('clamps limit to 1–50 and defaults to 10', async () => {
    const line = await seedProductWithVariant()
    await createOrder({
      paidAt: new Date('2026-11-02T03:00:00.000Z'),
      items: [{ ...line, unitPrice: 100_000, unitCost: 60_000, quantity: 3 }],
    })

    const limited = await getReport('top-products?limit=1')
    expect(limited.status).toBe(200)
    expect(limited.body.items).toHaveLength(1)

    expect((await getReport('top-products?limit=51')).status).toBe(400)
    expect((await getReport('top-products?limit=0')).status).toBe(400)
  })
})

describe('orders by status', () => {
  it('counts orders created in the range and always returns all five statuses', async () => {
    const statuses: { status: OrderStatus; total: number; createdAt: string }[] = [
      { status: 'PENDING', total: 150_000, createdAt: '2026-11-01T01:00:00.000Z' },
      { status: 'CONFIRMED', total: 240_000, createdAt: '2026-11-01T18:00:00.000Z' },
      { status: 'CONFIRMED', total: 260_000, createdAt: '2026-11-02T16:59:59.999Z' },
      { status: 'SHIPPING', total: 300_000, createdAt: '2026-11-02T02:00:00.000Z' },
      { status: 'DELIVERED', total: 450_000, createdAt: '2026-11-01T23:30:00.000Z' },
      { status: 'CANCELLED', total: 120_000, createdAt: '2026-11-01T23:59:59.999Z' },
    ]
    for (const entry of statuses) {
      await createOrder({
        status: entry.status,
        total: entry.total,
        createdAt: new Date(entry.createdAt),
      })
    }

    // 2026-10-31 23:59:59.999 in Vietnam and 2026-11-03 00:00 in Vietnam.
    await createOrder({ status: 'PENDING', total: 999_000, createdAt: new Date('2026-10-31T16:59:59.999Z') })
    await createOrder({ status: 'PENDING', total: 999_000, createdAt: new Date('2026-11-02T17:00:00.000Z') })

    const response = await getReport('orders-by-status?from=2026-11-01&to=2026-11-02')
    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      from: '2026-11-01',
      to: '2026-11-02',
      items: [
        { status: 'PENDING', count: 1, total: 150_000 },
        { status: 'CONFIRMED', count: 2, total: 500_000 },
        { status: 'SHIPPING', count: 1, total: 300_000 },
        { status: 'DELIVERED', count: 1, total: 450_000 },
        { status: 'CANCELLED', count: 1, total: 120_000 },
      ],
    })

    const empty = await getReport('orders-by-status?from=2026-11-05&to=2026-11-05')
    expect(empty.body.items).toEqual([
      { status: 'PENDING', count: 0, total: 0 },
      { status: 'CONFIRMED', count: 0, total: 0 },
      { status: 'SHIPPING', count: 0, total: 0 },
      { status: 'DELIVERED', count: 0, total: 0 },
      { status: 'CANCELLED', count: 0, total: 0 },
    ])
  })
})

describe('low stock', () => {
  it('reports active variants at or below the threshold, sorted by stock then sku', async () => {
    const active = await createProduct({
      name: 'Áo thun cotton',
      variants: [
        { sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 199_000, stock: 2 },
        { sku: 'AT-DEN-L', size: 'L', color: 'Đen', price: 199_000, stock: 0 },
        { sku: 'AT-TRANG-M', size: 'M', color: 'Trắng', price: 199_000, stock: 5 },
        { sku: 'AT-TRANG-L', size: 'L', color: 'Trắng', price: 199_000, stock: 6 },
      ],
    })
    await createProduct({
      name: 'Áo khoác ngưng bán',
      status: 'INACTIVE',
      variants: [{ sku: 'AK-XAM-L', size: 'L', color: 'Xám', price: 250_000, stock: 1 }],
    })

    const bySku = new Map(active.variants.map((variant) => [variant.sku, variant.id]))
    const variantId = (sku: string) => bySku.get(sku)

    const defaulted = await getReport('low-stock')
    expect(defaulted.status).toBe(200)
    expect(defaulted.body.threshold).toBe(5)
    expect(defaulted.body.items).toEqual([
      { variantId: variantId('AT-DEN-L'), sku: 'AT-DEN-L', productName: 'Áo thun cotton', size: 'L', color: 'Đen', stock: 0 },
      { variantId: variantId('AT-DEN-M'), sku: 'AT-DEN-M', productName: 'Áo thun cotton', size: 'M', color: 'Đen', stock: 2 },
      { variantId: variantId('AT-TRANG-M'), sku: 'AT-TRANG-M', productName: 'Áo thun cotton', size: 'M', color: 'Trắng', stock: 5 },
    ])

    const zero = await getReport('low-stock?threshold=0')
    expect(zero.body.threshold).toBe(0)
    expect(zero.body.items.map((item: { sku: string }) => item.sku)).toEqual(['AT-DEN-L'])

    expect((await getReport('low-stock?threshold=1001')).status).toBe(400)
  })
})

describe('inventory cost', () => {
  it('sums receipts and paid-order cost snapshots inside the window, treating a null cost as 0', async () => {
    const line = await seedProductWithVariant()

    await createReceipt(new Date('2026-11-02T05:00:00.000Z'), [
      { variantId: line.variantId, quantity: 10, unitCost: 200_000 },
      { variantId: line.variantId, quantity: 5, unitCost: 150_000 },
    ])
    // Starts exactly at 2026-11-04 00:00 Vietnam, so it is outside the requested range.
    await createReceipt(new Date('2026-11-03T17:00:00.000Z'), [
      { variantId: line.variantId, quantity: 100, unitCost: 100_000 },
    ])

    await createOrder({
      paidAt: new Date('2026-11-02T08:00:00.000Z'),
      total: 800_000,
      items: [
        { ...line, unitPrice: 250_000, unitCost: 150_000, quantity: 2 },
        { ...line, unitPrice: 300_000, unitCost: null, quantity: 1 },
      ],
    })
    await createOrder({
      paidAt: null,
      total: 999_000,
      items: [{ ...line, unitPrice: 100_000, unitCost: 900_000, quantity: 9 }],
    })

    const response = await getReport('inventory-cost?from=2026-11-01&to=2026-11-03')
    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      from: '2026-11-01',
      to: '2026-11-03',
      receiptsCost: 2_750_000,
      cogs: 300_000,
      revenue: 800_000,
      grossProfit: 500_000,
    })
  })

  it('keeps every paid order item costed in a seeded state', async () => {
    const line = await seedProductWithVariant()
    await createReceipt(new Date('2026-11-01T05:00:00.000Z'), [
      { variantId: line.variantId, quantity: 20, unitCost: 120_000 },
    ])
    await createOrder({
      status: 'DELIVERED',
      paymentMethod: 'COD',
      paidAt: new Date('2026-11-02T08:00:00.000Z'),
      items: [{ ...line, unitPrice: 199_000, unitCost: 120_000, quantity: 2 }],
    })

    expect(await offendingPaidItems()).toBe(0)
  })

  it('defaults to the last 30 days when the range is omitted', async () => {
    const response = await getReport('inventory-cost')
    expect(response.status).toBe(200)
    expect(response.body).toEqual({
      from: response.body.from,
      to: response.body.to,
      receiptsCost: 0,
      cogs: 0,
      revenue: 0,
      grossProfit: 0,
    })
  })
})
