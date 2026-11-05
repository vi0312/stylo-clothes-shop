import { randomUUID } from 'node:crypto'
import { beforeAll, describe, expect, it } from 'vitest'
import { prisma } from '../../src/lib/prisma.js'
import { resetDb } from '../helpers/resetDb.js'

let sequence = 0

async function catalogFixture() {
  sequence += 1
  const category = await prisma.category.create({ data: { name: `Category ${sequence}` } })
  const brand = await prisma.brand.create({ data: { name: `Brand ${sequence}` } })
  const product = await prisma.product.create({
    data: {
      name: `Product ${sequence}`,
      categoryId: category.id,
      brandId: brand.id,
    },
  })
  return { category, brand, product }
}

async function customerFixture() {
  sequence += 1
  return prisma.user.create({
    data: {
      email: `customer-${sequence}@stylo.test`,
      passwordHash: 'hash',
      fullName: `Customer ${sequence}`,
    },
  })
}

async function orderFixture(userId: number) {
  sequence += 1
  return prisma.order.create({
    data: {
      code: `STY-250101-${String(sequence).padStart(4, '0')}`,
      userId,
      clientOrderId: randomUUID(),
      paymentMethod: 'COD',
      recipientName: 'Người nhận',
      recipientPhone: '0900000000',
      shippingAddress: '1 Đường Mẫu, Quận 1',
      subtotal: 199_000,
      shippingFee: 30_000,
      total: 229_000,
    },
  })
}

beforeAll(async () => {
  await resetDb()
})

describe('product_variant constraints', () => {
  it('rejects a non-positive price', async () => {
    const { product } = await catalogFixture()

    await expect(
      prisma.productVariant.create({
        data: {
          productId: product.id,
          sku: 'CHECK-PRICE-0',
          size: 'M',
          color: 'Đen',
          price: 0,
        },
      }),
    ).rejects.toThrow()
  })

  it('rejects negative stock', async () => {
    const { product } = await catalogFixture()

    await expect(
      prisma.productVariant.create({
        data: {
          productId: product.id,
          sku: 'CHECK-STOCK-NEG',
          size: 'M',
          color: 'Đen',
          price: 199_000,
          stock: -1,
        },
      }),
    ).rejects.toThrow()
  })

  it('enforces system-wide unique SKU', async () => {
    const first = await catalogFixture()
    const second = await catalogFixture()

    await prisma.productVariant.create({
      data: { productId: first.product.id, sku: 'SKU-DUP', size: 'M', color: 'Đen', price: 1 },
    })

    await expect(
      prisma.productVariant.create({
        data: { productId: second.product.id, sku: 'SKU-DUP', size: 'L', color: 'Trắng', price: 1 },
      }),
    ).rejects.toMatchObject({ code: 'P2002' })
  })

  it('enforces unique size and color per product', async () => {
    const { product } = await catalogFixture()

    await prisma.productVariant.create({
      data: { productId: product.id, sku: 'SKU-A', size: 'M', color: 'Đen', price: 1 },
    })

    await expect(
      prisma.productVariant.create({
        data: { productId: product.id, sku: 'SKU-B', size: 'M', color: 'Đen', price: 1 },
      }),
    ).rejects.toMatchObject({ code: 'P2002' })
  })
})

describe('referential rules', () => {
  it('cascades variants and images when a product is deleted', async () => {
    const { product } = await catalogFixture()
    await prisma.productVariant.create({
      data: { productId: product.id, sku: 'CASCADE-SKU', size: 'M', color: 'Đen', price: 1 },
    })
    await prisma.productImage.create({
      data: { productId: product.id, url: '/uploads/products/cascade.jpg', position: 0 },
    })

    await prisma.product.delete({ where: { id: product.id } })

    await expect(prisma.productVariant.count({ where: { productId: product.id } })).resolves.toBe(0)
    await expect(prisma.productImage.count({ where: { productId: product.id } })).resolves.toBe(0)
  })

  it('restricts deleting a user who has an order', async () => {
    const user = await customerFixture()
    await orderFixture(user.id)

    await expect(prisma.user.delete({ where: { id: user.id } })).rejects.toMatchObject({
      code: 'P2003',
    })
  })
})

describe('order idempotency', () => {
  it('enforces one client_order_id per user', async () => {
    const user = await customerFixture()
    const order = await orderFixture(user.id)

    await expect(
      prisma.order.create({
        data: {
          code: `STY-250101-${String(++sequence).padStart(4, '0')}`,
          userId: user.id,
          clientOrderId: order.clientOrderId,
          paymentMethod: 'COD',
          recipientName: 'Người nhận',
          recipientPhone: '0900000000',
          shippingAddress: '1 Đường Mẫu, Quận 1',
          subtotal: 199_000,
          shippingFee: 30_000,
          total: 229_000,
        },
      }),
    ).rejects.toMatchObject({ code: 'P2002' })
  })

  it('supports raw SQL on the reserved table name', async () => {
    const user = await customerFixture()
    await orderFixture(user.id)

    const [rows] = await prisma.$queryRaw<{ total: bigint }[]>`SELECT COUNT(*) AS total FROM \`order\``

    expect(Number(rows.total)).toBe(await prisma.order.count())
  })
})
