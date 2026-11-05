import argon2 from 'argon2'
import { getEnv } from '../src/config/env.js'
import { prisma } from '../src/lib/prisma.js'

interface SeedVariant {
  sku: string
  size: string
  color: string
  price: number
}

interface SeedProduct {
  name: string
  description: string
  category: string
  brand: string
  variants: SeedVariant[]
}

const CATEGORIES = ['Áo', 'Quần'] as const
const BRANDS = ['Stylo', 'Local Brand'] as const

const PRODUCTS: SeedProduct[] = [
  {
    name: 'Áo thun basic',
    description: 'Áo thun cotton mềm, form regular.',
    category: 'Áo',
    brand: 'Stylo',
    variants: [
      { sku: 'STY-AO-001-S', size: 'S', color: 'Đen', price: 199_000 },
      { sku: 'STY-AO-001-M', size: 'M', color: 'Đen', price: 199_000 },
      { sku: 'STY-AO-001-L', size: 'L', color: 'Trắng', price: 209_000 },
    ],
  },
  {
    name: 'Áo sơ mi linen',
    description: 'Sơ mi linen thoáng mát cho mùa hè.',
    category: 'Áo',
    brand: 'Stylo',
    variants: [
      { sku: 'STY-AO-002-M', size: 'M', color: 'Be', price: 359_000 },
      { sku: 'STY-AO-002-L', size: 'L', color: 'Be', price: 359_000 },
    ],
  },
  {
    name: 'Quần jeans straight',
    description: 'Jeans ống suông, chất denim dày vừa.',
    category: 'Quần',
    brand: 'Local Brand',
    variants: [
      { sku: 'STY-QU-001-29', size: '29', color: 'Xanh đậm', price: 459_000 },
      { sku: 'STY-QU-001-30', size: '30', color: 'Xanh đậm', price: 459_000 },
      { sku: 'STY-QU-001-31', size: '31', color: 'Xanh đậm', price: 459_000 },
    ],
  },
  {
    name: 'Quần short kaki',
    description: 'Short kaki túi hộp, lên form gọn.',
    category: 'Quần',
    brand: 'Local Brand',
    variants: [
      { sku: 'STY-QU-002-29', size: '29', color: 'Kem', price: 289_000 },
      { sku: 'STY-QU-002-31', size: '31', color: 'Kem', price: 289_000 },
    ],
  },
]

const SEED_RECEIPT_CODE = 'PN-250101-0001'
const SEED_RECEIPT_SUPPLIER = 'Xưởng may mẫu'

async function main(): Promise<void> {
  const env = getEnv()
  const adminEmail = env.SEED_ADMIN_EMAIL.toLowerCase()
  const passwordHash = await argon2.hash(env.SEED_ADMIN_PASSWORD)

  const admin = await prisma.user.upsert({
    where: { email: adminEmail },
    update: {
      fullName: env.SEED_ADMIN_NAME,
      passwordHash,
      role: 'ADMIN',
      status: 'ACTIVE',
    },
    create: {
      email: adminEmail,
      passwordHash,
      fullName: env.SEED_ADMIN_NAME,
      role: 'ADMIN',
      status: 'ACTIVE',
    },
  })

  const categories = new Map<string, number>()
  for (const name of CATEGORIES) {
    const category = await prisma.category.upsert({
      where: { name },
      update: {},
      create: { name },
    })
    categories.set(name, category.id)
  }

  const brands = new Map<string, number>()
  for (const name of BRANDS) {
    const brand = await prisma.brand.upsert({
      where: { name },
      update: {},
      create: { name },
    })
    brands.set(name, brand.id)
  }

  const variantsBySku = new Map<string, { id: number; sku: string }>()

  for (const entry of PRODUCTS) {
    const categoryId = categories.get(entry.category)
    const brandId = brands.get(entry.brand)
    if (categoryId === undefined || brandId === undefined) {
      throw new Error(`Seed product ${entry.name} references an unknown category or brand`)
    }

    let product = await prisma.product.findFirst({ where: { name: entry.name } })
    if (!product) {
      product = await prisma.product.create({
        data: {
          name: entry.name,
          description: entry.description,
          categoryId,
          brandId,
        },
      })
    }

    for (const variant of entry.variants) {
      const saved = await prisma.productVariant.upsert({
        where: { sku: variant.sku },
        update: {
          productId: product.id,
          size: variant.size,
          color: variant.color,
          price: variant.price,
        },
        create: {
          productId: product.id,
          sku: variant.sku,
          size: variant.size,
          color: variant.color,
          price: variant.price,
        },
      })
      variantsBySku.set(saved.sku, { id: saved.id, sku: saved.sku })
    }
  }

  const existingReceipt = await prisma.inventoryReceipt.findUnique({
    where: { code: SEED_RECEIPT_CODE },
  })

  if (!existingReceipt) {
    const received = [
      { sku: 'STY-AO-001-M', quantity: 20, unitCost: 110_000 },
      { sku: 'STY-QU-001-30', quantity: 15, unitCost: 260_000 },
    ]

    const items = received.map((item) => {
      const variant = variantsBySku.get(item.sku)
      if (!variant) throw new Error(`Seed receipt references unknown SKU ${item.sku}`)
      return { variantId: variant.id, quantity: item.quantity, unitCost: item.unitCost }
    })

    const totalCost = items.reduce((sum, item) => sum + item.quantity * item.unitCost, 0)

    await prisma.$transaction(async (tx) => {
      const receipt = await tx.inventoryReceipt.create({
        data: {
          code: SEED_RECEIPT_CODE,
          supplier: SEED_RECEIPT_SUPPLIER,
          note: 'Phiếu nhập khởi tạo dữ liệu demo',
          totalCost,
          createdBy: admin.id,
          items: {
            create: items.map((item) => ({
              variantId: item.variantId,
              quantity: item.quantity,
              unitCost: item.unitCost,
            })),
          },
        },
      })

      for (const item of items) {
        await tx.productVariant.update({
          where: { id: item.variantId },
          data: { stock: { increment: item.quantity } },
        })
        await tx.stockMovement.create({
          data: {
            variantId: item.variantId,
            delta: item.quantity,
            reason: 'RECEIPT',
            refType: 'RECEIPT',
            refId: receipt.id,
            unitCost: item.unitCost,
          },
        })
      }
    })
  }

  console.log(
    `Seed complete: 1 admin, ${CATEGORIES.length} categories, ${BRANDS.length} brands, ${PRODUCTS.length} products, ${variantsBySku.size} variants, 1 receipt`,
  )
}

main()
  .catch((error: unknown) => {
    console.error('Seed failed', error)
    process.exitCode = 1
  })
  .finally(async () => {
    await prisma.$disconnect()
  })
