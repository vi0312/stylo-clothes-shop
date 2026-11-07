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

const SUPPLIER = 'Xưởng may Bình Tân'
const NOTE = 'Lô thu đông'
const DEFAULT_LINE = '12 Nguyễn Trãi, P. Bến Thành, Q.1, TP.HCM'

let adminId: number
let adminToken: string
let customerId: number
let customerToken: string
let product: { name: string; variants: { id: number; sku: string }[] }

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

function variant(sku: string) {
  const found = product.variants.find((entry) => entry.sku === sku)
  if (!found) throw new Error(`missing variant ${sku}`)
  return found
}

function receiptBody(
  items: Record<string, unknown>[],
  overrides: Record<string, unknown> = {},
) {
  return { supplier: SUPPLIER, note: NOTE, items, ...overrides }
}

function postReceipt(body: Record<string, unknown>) {
  return request(app).post('/api/v1/admin/inventory-receipts').set(auth(adminToken)).send(body)
}

function getReceipts(query = '') {
  return request(app).get(`/api/v1/admin/inventory-receipts${query}`).set(auth(adminToken))
}

beforeEach(async () => {
  await resetDb()
  const admin = await createUser({
    email: 'admin@example.com',
    fullName: 'Quản trị viên',
    role: 'ADMIN',
  })
  const customer = await createUser({ email: 'an@example.com', fullName: 'Nguyễn Văn An' })
  adminId = admin.id
  adminToken = signAccessToken(admin.id, admin.role)
  customerId = customer.id
  customerToken = signAccessToken(customer.id, customer.role)

  // Stock starts at 0 so the ledger reconciliation query has nothing to excuse.
  product = await createProduct({
    variants: [
      { sku: 'AT-DEN-M', size: 'M', color: 'Đen', price: 199_000, stock: 0 },
      { sku: 'AT-DEN-L', size: 'L', color: 'Đen', price: 199_000, stock: 0 },
    ],
  })
})

describe('inventory receipts', () => {
  it('requires an ADMIN access token', async () => {
    expect((await request(app).get('/api/v1/admin/inventory-receipts')).status).toBe(401)
    expect((await request(app).post('/api/v1/admin/inventory-receipts').send({})).status).toBe(401)

    const forbidden = await request(app)
      .get('/api/v1/admin/inventory-receipts')
      .set(auth(customerToken))
    expect(forbidden.status).toBe(403)
    expect(forbidden.body.error.code).toBe('FORBIDDEN')
  })

  it('creates a receipt, increments stock and appends RECEIPT movements', async () => {
    const tee = variant('AT-DEN-M')
    const response = await postReceipt(
      receiptBody([{ variantId: tee.id, quantity: 20, unitCost: 120_000 }]),
    )

    expect(response.status).toBe(201)
    expect(response.body).toMatchObject({
      code: expect.stringMatching(/^PN-\d{6}-[A-Z0-9]{4}$/),
      supplier: SUPPLIER,
      note: NOTE,
      totalCost: 2_400_000,
      itemCount: 1,
      createdBy: { id: adminId, fullName: 'Quản trị viên' },
    })
    expect(new Date(response.body.createdAt).toISOString()).toBe(response.body.createdAt)
    expect(response.body.items).toEqual([
      expect.objectContaining({
        variantId: tee.id,
        sku: 'AT-DEN-M',
        productName: product.name,
        size: 'M',
        color: 'Đen',
        quantity: 20,
        unitCost: 120_000,
        lineTotal: 2_400_000,
      }),
    ])

    const stored = await prisma.productVariant.findUniqueOrThrow({ where: { id: tee.id } })
    expect(stored.stock).toBe(20)

    const movements = await prisma.stockMovement.findMany({ orderBy: { id: 'asc' } })
    expect(movements).toEqual([
      expect.objectContaining({
        variantId: tee.id,
        delta: 20,
        reason: 'RECEIPT',
        refType: 'RECEIPT',
        refId: response.body.id,
        unitCost: 120_000,
      }),
    ])
  })

  it('accepts the same variant on two lines at different costs', async () => {
    const tee = variant('AT-DEN-M')
    const response = await postReceipt(
      receiptBody([
        { variantId: tee.id, quantity: 10, unitCost: 100_000 },
        { variantId: tee.id, quantity: 10, unitCost: 120_000 },
      ]),
    )

    expect(response.status).toBe(201)
    expect(response.body.totalCost).toBe(2_200_000)
    expect(response.body.itemCount).toBe(2)
    expect(response.body.items).toHaveLength(2)
    expect(response.body.items.map((item: { lineTotal: number }) => item.lineTotal)).toEqual([
      1_000_000, 1_200_000,
    ])

    const stored = await prisma.productVariant.findUniqueOrThrow({ where: { id: tee.id } })
    expect(stored.stock).toBe(20)
    expect(await prisma.stockMovement.count({ where: { variantId: tee.id } })).toBe(2)

    // The weighted average checkout snapshots as order_item.unit_cost.
    const [row] = await prisma.$queryRaw<{ avgCost: unknown }[]>`
      SELECT ROUND(SUM(quantity * unit_cost) / SUM(quantity)) AS avgCost
      FROM inventory_receipt_item
      WHERE variant_id = ${tee.id}
    `
    expect(Number(row.avgCost)).toBe(110_000)
  })

  it('rejects an unknown variant and leaves no receipt behind', async () => {
    const tee = variant('AT-DEN-M')
    const response = await postReceipt(
      receiptBody([
        { variantId: tee.id, quantity: 5, unitCost: 100_000 },
        { variantId: 999_999, quantity: 5, unitCost: 100_000 },
      ]),
    )

    expect(response.status).toBe(404)
    expect(response.body.error.code).toBe('NOT_FOUND')
    expect(await prisma.inventoryReceipt.count()).toBe(0)
    expect(await prisma.stockMovement.count()).toBe(0)
    const stored = await prisma.productVariant.findUniqueOrThrow({ where: { id: tee.id } })
    expect(stored.stock).toBe(0)
  })

  it('lists summaries without items, newest first, and paginates', async () => {
    const tee = variant('AT-DEN-M')
    const hoodie = variant('AT-DEN-L')
    const first = await postReceipt(
      receiptBody([{ variantId: tee.id, quantity: 1, unitCost: 100_000 }], {
        supplier: 'Nhà cung cấp A',
      }),
    )
    const second = await postReceipt(
      receiptBody(
        [
          { variantId: tee.id, quantity: 2, unitCost: 100_000 },
          { variantId: hoodie.id, quantity: 1, unitCost: 100_000 },
        ],
        { supplier: 'Nhà cung cấp B' },
      ),
    )

    const response = await getReceipts()
    expect(response.status).toBe(200)
    expect(response.body).toMatchObject({ page: 1, limit: 20, total: 2 })
    expect(response.body.items.map((item: { id: number }) => item.id)).toEqual([
      second.body.id,
      first.body.id,
    ])
    expect(response.body.items[0]).toEqual(
      expect.objectContaining({ supplier: 'Nhà cung cấp B', itemCount: 2 }),
    )
    expect(response.body.items[0]).not.toHaveProperty('items')

    const pageTwo = await getReceipts('?limit=1&page=2')
    expect(pageTwo.body.items.map((item: { id: number }) => item.id)).toEqual([first.body.id])

    const oldest = await getReceipts('?sort=oldest')
    expect(oldest.body.items.map((item: { id: number }) => item.id)).toEqual([
      first.body.id,
      second.body.id,
    ])
  })

  it('filters by inclusive Asia/Ho_Chi_Minh calendar days', async () => {
    const tee = variant('AT-DEN-M')
    const line = [{ variantId: tee.id, quantity: 1, unitCost: 100_000 }]
    const startOfDay = await postReceipt(receiptBody(line))
    const endOfDay = await postReceipt(receiptBody(line))
    const nextDay = await postReceipt(receiptBody(line))

    const at = (id: number, createdAt: string) =>
      prisma.inventoryReceipt.update({ where: { id }, data: { createdAt: new Date(createdAt) } })
    await at(startOfDay.body.id, '2026-11-06T17:30:00.000Z') // 2026-11-07 00:30 VN
    await at(endOfDay.body.id, '2026-11-07T16:59:59.999Z') // 2026-11-07 23:59 VN
    await at(nextDay.body.id, '2026-11-07T17:00:00.000Z') // 2026-11-08 00:00 VN

    const response = await getReceipts('?from=2026-11-07&to=2026-11-07&sort=oldest')
    expect(response.body.items.map((item: { id: number }) => item.id)).toEqual([
      startOfDay.body.id,
      endOfDay.body.id,
    ])

    const invalid = await getReceipts('?from=2026-11-08&to=2026-11-07')
    expect(invalid.status).toBe(400)
    expect(invalid.body.error.code).toBe('VALIDATION_FAILED')
  })

  it('returns the whole receipt on detail and 404 for an unknown id', async () => {
    const tee = variant('AT-DEN-M')
    const hoodie = variant('AT-DEN-L')
    const created = await postReceipt(
      receiptBody([
        { variantId: tee.id, quantity: 2, unitCost: 100_000 },
        { variantId: hoodie.id, quantity: 1, unitCost: 250_000 },
      ]),
    )

    const detail = await request(app)
      .get(`/api/v1/admin/inventory-receipts/${created.body.id}`)
      .set(auth(adminToken))
    expect(detail.status).toBe(200)
    expect(detail.body).toEqual(created.body)

    const missing = await request(app)
      .get('/api/v1/admin/inventory-receipts/999999')
      .set(auth(adminToken))
    expect(missing.status).toBe(404)
    expect(missing.body.error.code).toBe('NOT_FOUND')
  })

  it('is immutable: no PATCH and no DELETE', async () => {
    const tee = variant('AT-DEN-M')
    const created = await postReceipt(
      receiptBody([{ variantId: tee.id, quantity: 1, unitCost: 100_000 }]),
    )

    for (const method of ['patch', 'delete'] as const) {
      const response = await request(app)
        [method](`/api/v1/admin/inventory-receipts/${created.body.id}`)
        .set(auth(adminToken))
        .send({ supplier: 'Đổi nhà cung cấp' })
      expect(response.status).toBe(404)
      expect(response.body.error.code).toBe('NOT_FOUND')
    }

    const stored = await prisma.inventoryReceipt.findUniqueOrThrow({
      where: { id: created.body.id },
    })
    expect(stored.supplier).toBe(SUPPLIER)
  })

  it('rejects invalid bodies without touching stock', async () => {
    const tee = variant('AT-DEN-M')
    const valid = [{ variantId: tee.id, quantity: 1, unitCost: 100_000 }]
    const bodies = [
      receiptBody([]),
      receiptBody([{ variantId: tee.id, quantity: 0, unitCost: 100_000 }]),
      receiptBody([{ variantId: tee.id, quantity: 1, unitCost: -1 }]),
      receiptBody([{ variantId: tee.id, quantity: 1, unitCost: 1.5 }]),
      receiptBody(valid, { supplier: '' }),
      receiptBody(valid, { note: 'x'.repeat(256) }),
      receiptBody(valid, { unexpected: true }),
      receiptBody([{ variantId: tee.id, quantity: 1, unitCost: 1, extra: 1 }]),
      receiptBody(Array.from({ length: 101 }, () => valid[0])),
    ]

    for (const body of bodies) {
      const response = await postReceipt(body)
      expect(response.status).toBe(400)
      expect(response.body.error.code).toBe('VALIDATION_FAILED')
    }

    expect(await prisma.inventoryReceipt.count()).toBe(0)
    expect(await prisma.stockMovement.count()).toBe(0)
  })

  it('keeps the stock ledger reconciled through receipt, checkout and cancel', async () => {
    const tee = variant('AT-DEN-M')
    expect(
      (await postReceipt(receiptBody([{ variantId: tee.id, quantity: 5, unitCost: 100_000 }])))
        .status,
    ).toBe(201)

    const address = await prisma.address.create({
      data: {
        userId: customerId,
        recipientName: 'Nguyễn Văn An',
        phone: '0901234567',
        line: DEFAULT_LINE,
      },
    })
    expect(
      (
        await request(app)
          .post('/api/v1/cart/items')
          .set(auth(customerToken))
          .send({ variantId: tee.id, quantity: 2 })
      ).status,
    ).toBe(200)

    const order = await request(app)
      .post('/api/v1/orders')
      .set(auth(customerToken))
      .send({ clientOrderId: randomUUID(), addressId: address.id, paymentMethod: 'COD' })
    expect(order.status).toBe(201)

    const cancelled = await request(app)
      .post(`/api/v1/orders/${order.body.order.id}/cancel`)
      .set(auth(customerToken))
    expect(cancelled.status).toBe(200)

    // docs/erd.md reconciliation query: stock must equal the sum of its ledger.
    const drift = await prisma.$queryRaw`
      SELECT v.id, v.sku, v.stock, COALESCE(SUM(m.delta), 0) AS ledger
      FROM product_variant v
      LEFT JOIN stock_movement m ON m.variant_id = v.id
      GROUP BY v.id, v.sku, v.stock
      HAVING v.stock <> ledger
    `
    expect(drift).toEqual([])
    const stored = await prisma.productVariant.findUniqueOrThrow({ where: { id: tee.id } })
    expect(stored.stock).toBe(5)
  })
})
