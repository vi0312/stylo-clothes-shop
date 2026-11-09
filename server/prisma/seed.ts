import argon2 from 'argon2'
import { copyFileSync, mkdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { getEnv } from '../src/config/env.js'
import { prisma } from '../src/lib/prisma.js'

type CatalogStatus = 'ACTIVE' | 'INACTIVE'
type UserStatus = 'ACTIVE' | 'DISABLED'
type OrderStatus = 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED'
type PaymentMethod = 'COD' | 'ONLINE'
type PaymentPlan = 'SUCCEEDED' | 'FAILED' | 'PENDING' | 'FAILED_THEN_SUCCEEDED'

interface SeedCategory {
  name: string
  status: CatalogStatus
}

interface SeedVariant {
  sku: string
  size: string
  color: string
  price: number
  /** Stock the demo should show after every seeded receipt and sale. */
  targetStock: number
}

interface SeedCatalogProduct {
  datasetId: number
  sourceName: string
  name: string
  description: string
  category: string
  brand: string
  status?: CatalogStatus
  image: string
  variants: SeedVariant[]
}

interface SeedCatalog {
  source: {
    name: string
    mirror: string
    original: string
    license: string
    retrievedAt: string
    selection: string
  }
  products: SeedCatalogProduct[]
}

interface SeedOrderItem {
  sku: string
  quantity: number
}

interface SeedOrder {
  code: string
  customer: number
  daysAgo: number
  status: OrderStatus
  method: PaymentMethod
  payment?: PaymentPlan
  note?: string
  items: SeedOrderItem[]
}

interface VariantRow {
  id: number
  productId: number
  productName: string
  sku: string
  size: string
  color: string
  price: number
}

interface ReceiptLinePlan {
  variant: VariantRow
  quantity: number
  unitCost: number
  share: 0 | 1 | 2
}

const DAY_MS = 86_400_000
const NOW = new Date()

/** Southeast Asia noon, expressed in UTC, keeps order dates on their intended VN day. */
function daysAgoAt(daysAgo: number, hourUtc: number): Date {
  const day = new Date(NOW.getTime() - daysAgo * DAY_MS)
  return new Date(Date.UTC(day.getUTCFullYear(), day.getUTCMonth(), day.getUTCDate(), hourUtc, 0, 0))
}

function orderTime(daysAgo: number): Date {
  return daysAgo === 0 ? new Date(NOW.getTime() - 60 * 60 * 1000) : daysAgoAt(daysAgo, 3)
}

function addHours(date: Date, hours: number): Date {
  return new Date(date.getTime() + hours * 3_600_000)
}

function earlier(first: Date, second: Date): Date {
  return first.getTime() <= second.getTime() ? first : second
}

/** YYMMDD in Asia/Ho_Chi_Minh, matching the order and receipt code prefixes. */
function vnDateCode(date: Date): string {
  const vn = new Date(date.getTime() + 7 * 3_600_000)
  const year = String(vn.getUTCFullYear() % 100).padStart(2, '0')
  const month = String(vn.getUTCMonth() + 1).padStart(2, '0')
  const day = String(vn.getUTCDate()).padStart(2, '0')
  return `${year}${month}${day}`
}

const CUSTOMER_PASSWORD = 'Customer@12345'
const CUSTOMER_EMAILS = Array.from({ length: 12 }, (_, index) => `customer${String(index + 1).padStart(2, '0')}@stylo.local`)
const CUSTOMER_NAMES = [
  'Nguyễn Văn An',
  'Trần Thị Bích',
  'Lê Hoàng Nam',
  'Phạm Thu Hà',
  'Hoàng Minh Tuấn',
  'Vũ Thị Lan',
  'Đặng Quốc Huy',
  'Bùi Thanh Mai',
  'Đỗ Anh Khoa',
  'Ngô Thị Hồng',
  'Dương Gia Bảo',
  'Lý Thị Ngọc',
]
const DISABLED_CUSTOMER_INDEX = 12

const CATEGORIES: SeedCategory[] = [
  { name: 'Áo', status: 'ACTIVE' },
  { name: 'Quần', status: 'ACTIVE' },
  { name: 'Áo khoác', status: 'ACTIVE' },
  // Inactive category with no active product: the catalog filter edge case.
  { name: 'Phụ kiện', status: 'INACTIVE' },
]

const BRANDS = ['Stylo', 'Local Brand', 'Urban Viet', 'Basic Lab']

function loadCatalog(): SeedCatalog {
  const filename = fileURLToPath(new URL('../seed-assets/catalog/catalog.json', import.meta.url))
  const catalog = JSON.parse(readFileSync(filename, 'utf8')) as SeedCatalog
  const names = new Set<string>()
  const skus = new Set<string>()
  const categories = new Set(CATEGORIES.map((entry) => entry.name))
  const brands = new Set(BRANDS)

  if (!Array.isArray(catalog.products) || catalog.products.length === 0) {
    throw new Error(`Seed catalog ${filename} has no products`)
  }

  for (const product of catalog.products) {
    if (names.has(product.name)) throw new Error(`Duplicate seed product name: ${product.name}`)
    names.add(product.name)
    if (!categories.has(product.category)) {
      throw new Error(`Seed product ${product.name} references unknown category ${product.category}`)
    }
    if (!brands.has(product.brand)) {
      throw new Error(`Seed product ${product.name} references unknown brand ${product.brand}`)
    }
    if (!product.image) throw new Error(`Seed product ${product.name} has no image`)
    if (product.variants.length === 0) throw new Error(`Seed product ${product.name} has no variants`)

    for (const variant of product.variants) {
      if (skus.has(variant.sku)) throw new Error(`Duplicate seed SKU: ${variant.sku}`)
      if (variant.price <= 0) throw new Error(`Seed variant ${variant.sku} has no price`)
      if (variant.targetStock < 0) throw new Error(`Seed variant ${variant.sku} has negative stock`)
      skus.add(variant.sku)
    }
  }

  return catalog
}

const CATALOG = loadCatalog()
const PRODUCTS = CATALOG.products

/**
 * The pinned demo product is created last so the public "newest" sort still
 * exposes it first; E2E journeys search for its name without scrolling.
 */
const PINNED_PRODUCT_NAME = 'Áo thun basic'
const CREATION_ORDER = [
  ...PRODUCTS.filter((product) => product.name !== PINNED_PRODUCT_NAME),
  ...PRODUCTS.filter((product) => product.name === PINNED_PRODUCT_NAME),
]

const RECEIPTS = [
  { daysAgo: 100, supplier: 'Xưởng may Hà Nội', note: 'Lô đầu mùa thu.', share: 0 as const },
  { daysAgo: 95, supplier: 'Kho tổng Sài Gòn', note: 'Bổ sung sizes bán chạy.', share: 1 as const },
  { daysAgo: 90, supplier: 'Nhà phân phối Đông Á', note: 'Nhập thêm với giá điều chỉnh.', share: 2 as const },
].map((plan) => ({
  ...plan,
  code: `PN-${vnDateCode(daysAgoAt(plan.daysAgo, 2))}-${String(plan.share + 1).padStart(4, '0')}`,
}))

function imageUrl(filename: string): string {
  return `/uploads/products/${filename}`
}

function clientOrderId(index: number): string {
  return `00000000-0000-4000-8000-${String(index).padStart(12, '0')}`
}

function addressFor(customerIndex: number): { defaultLine: string; secondaryLine: string; phone: string } {
  const phone = `09${String(1_000_000 + customerIndex).padStart(8, '0')}`
  return {
    defaultLine: `Số ${10 + customerIndex} Nguyễn Trãi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh`,
    secondaryLine: `Số ${20 + customerIndex} Lê Lợi, Phường Tân Định, Quận 3, TP. Hồ Chí Minh`,
    phone,
  }
}

function mulberry32(seed: number): () => number {
  let state = seed >>> 0
  return () => {
    state = (state + 0x6d2b79f5) >>> 0
    let value = state
    value = Math.imul(value ^ (value >>> 15), value | 1)
    value ^= value + Math.imul(value ^ (value >>> 7), value | 61)
    return ((value ^ (value >>> 14)) >>> 0) / 4_294_967_296
  }
}

const ORDER_COUNT = 140

function buildOrders(products: SeedCatalogProduct[]): SeedOrder[] {
  const sellable = products
    .filter((product) => (product.status ?? 'ACTIVE') === 'ACTIVE')
    .flatMap((product) => product.variants)
  if (sellable.length === 0) throw new Error('Seed catalog has no sellable variants')

  const pinned = products.find((product) => product.name === 'Áo thun basic')?.variants ?? []
  const random = mulberry32(20261107)
  // Delivered-heavy so revenue reports have history, with every order state represented.
  const statusPlan: OrderStatus[] = [
    'DELIVERED', 'SHIPPING', 'DELIVERED', 'PENDING', 'DELIVERED',
    'CONFIRMED', 'DELIVERED', 'CANCELLED', 'DELIVERED', 'SHIPPING',
    'DELIVERED', 'PENDING', 'DELIVERED', 'CONFIRMED', 'DELIVERED',
    'CANCELLED', 'DELIVERED', 'PENDING', 'DELIVERED', 'DELIVERED',
  ]

  const orders: SeedOrder[] = []
  for (let index = 0; index < ORDER_COUNT; index += 1) {
    const daysAgo = Math.floor((index * 89) / (ORDER_COUNT - 1))
    const createdAt = orderTime(daysAgo)
    const status: OrderStatus =
      index === 0 ? 'PENDING' : index === 1 ? 'CONFIRMED' : statusPlan[(index - 2) % statusPlan.length]

    const method: PaymentMethod =
      index === 0
        ? 'ONLINE'
        : index === 1
          ? 'COD'
          : status === 'PENDING'
            ? 'ONLINE'
            : status === 'DELIVERED'
              ? index % 3 === 0
                ? 'COD'
                : 'ONLINE'
              : index % 2 === 0
                ? 'ONLINE'
                : 'COD'

    let payment: PaymentPlan | undefined
    if (method === 'ONLINE') {
      if (index === 0) payment = 'PENDING'
      else if (status === 'PENDING') payment = index % 2 === 0 ? 'PENDING' : 'FAILED'
      else if (status === 'CANCELLED') payment = 'FAILED'
      else if (status === 'DELIVERED' && index % 9 === 0) payment = 'FAILED_THEN_SUCCEEDED'
      else payment = 'SUCCEEDED'
    }

    const items: SeedOrderItem[] = []
    const used = new Set<string>()
    if (index === 0 && pinned[0]) {
      items.push({ sku: pinned[0].sku, quantity: 2 })
      used.add(pinned[0].sku)
    }
    if (index === 1 && pinned[1]) {
      items.push({ sku: pinned[1].sku, quantity: 1 })
      used.add(pinned[1].sku)
    }

    const itemCount = 1 + Math.floor(random() * 3)
    let guard = 0
    while (items.length < itemCount && guard < 50) {
      guard += 1
      const variant = sellable[Math.floor(random() * sellable.length)]
      if (used.has(variant.sku)) continue
      used.add(variant.sku)
      items.push({ sku: variant.sku, quantity: 1 + Math.floor(random() * 2) })
    }

    orders.push({
      code: `STY-${vnDateCode(createdAt)}-${String(index + 1).padStart(4, '0')}`,
      customer: 1 + ((index * 7) % 11),
      daysAgo,
      status,
      method,
      payment,
      items,
    })
  }

  if (!orders.some((order) => order.payment === 'FAILED_THEN_SUCCEEDED')) {
    const target = orders.find((order) => order.status === 'DELIVERED' && order.method === 'ONLINE')
    if (target) target.payment = 'FAILED_THEN_SUCCEEDED'
  }

  return orders
}

const ORDERS = buildOrders(PRODUCTS)

function copySeedImages(): void {
  const assetsDir = fileURLToPath(new URL('../seed-assets/catalog/images/', import.meta.url))
  const uploadDir = path.resolve(getEnv().UPLOAD_DIR, 'products')
  mkdirSync(uploadDir, { recursive: true })

  for (const product of PRODUCTS) {
    copyFileSync(path.join(assetsDir, product.image), path.join(uploadDir, product.image))
  }
}

export async function seed(): Promise<void> {
  const env = getEnv()
  const adminEmail = env.SEED_ADMIN_EMAIL.toLowerCase()
  const adminPasswordHash = await argon2.hash(env.SEED_ADMIN_PASSWORD)
  const customerPasswordHash = await argon2.hash(CUSTOMER_PASSWORD)

  const admin = await prisma.user.upsert({
    where: { email: adminEmail },
    update: {
      fullName: env.SEED_ADMIN_NAME,
      passwordHash: adminPasswordHash,
      role: 'ADMIN',
      status: 'ACTIVE',
    },
    create: {
      email: adminEmail,
      passwordHash: adminPasswordHash,
      fullName: env.SEED_ADMIN_NAME,
      role: 'ADMIN',
      status: 'ACTIVE',
    },
  })

  const customers = new Map<number, { id: number; fullName: string }>()
  for (const [index, email] of CUSTOMER_EMAILS.entries()) {
    const number = index + 1
    const status: UserStatus = number === DISABLED_CUSTOMER_INDEX ? 'DISABLED' : 'ACTIVE'
    const user = await prisma.user.upsert({
      where: { email },
      update: { fullName: CUSTOMER_NAMES[index], passwordHash: customerPasswordHash, status },
      create: {
        email,
        passwordHash: customerPasswordHash,
        fullName: CUSTOMER_NAMES[index],
        phone: addressFor(number).phone,
        role: 'CUSTOMER',
        status,
      },
    })
    customers.set(number, { id: user.id, fullName: user.fullName })
  }

  for (const number of customers.keys()) {
    if (number === DISABLED_CUSTOMER_INDEX) continue
    const customer = customers.get(number)
    if (!customer) throw new Error(`Missing seeded customer ${number}`)
    const address = addressFor(number)
    const rows = [
      { line: address.defaultLine, isDefault: true },
      { line: address.secondaryLine, isDefault: false },
    ]
    for (const row of rows) {
      const existing = await prisma.address.findFirst({ where: { userId: customer.id, line: row.line } })
      if (existing) {
        await prisma.address.update({
          where: { id: existing.id },
          data: { recipientName: customer.fullName, phone: address.phone, isDefault: row.isDefault },
        })
      } else {
        await prisma.address.create({
          data: {
            userId: customer.id,
            recipientName: customer.fullName,
            phone: address.phone,
            line: row.line,
            isDefault: row.isDefault,
          },
        })
      }
    }
  }

  const categories = new Map<string, number>()
  for (const entry of CATEGORIES) {
    const category = await prisma.category.upsert({
      where: { name: entry.name },
      update: { status: entry.status },
      create: { name: entry.name, status: entry.status },
    })
    categories.set(entry.name, category.id)
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

  copySeedImages()

  const variantsBySku = new Map<string, VariantRow>()
  for (const entry of CREATION_ORDER) {
    const categoryId = categories.get(entry.category)
    const brandId = brands.get(entry.brand)
    if (categoryId === undefined || brandId === undefined) {
      throw new Error(`Seed product ${entry.name} references an unknown category or brand`)
    }

    let product = await prisma.product.findFirst({ where: { name: entry.name } })
    if (product) {
      product = await prisma.product.update({
        where: { id: product.id },
        data: {
          description: entry.description,
          categoryId,
          brandId,
          status: entry.status ?? 'ACTIVE',
        },
      })
    } else {
      product = await prisma.product.create({
        data: {
          name: entry.name,
          description: entry.description,
          categoryId,
          brandId,
          status: entry.status ?? 'ACTIVE',
        },
      })
    }

    await prisma.productImage.deleteMany({ where: { productId: product.id } })
    await prisma.productImage.create({
      data: { productId: product.id, position: 0, url: imageUrl(entry.image) },
    })

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
      variantsBySku.set(variant.sku, {
        id: saved.id,
        productId: product.id,
        productName: entry.name,
        sku: saved.sku,
        size: saved.size,
        color: saved.color,
        price: saved.price,
      })
    }
  }

  const soldBySku = new Map<string, number>()
  for (const order of ORDERS) {
    if (order.status === 'CANCELLED') continue
    for (const item of order.items) {
      soldBySku.set(item.sku, (soldBySku.get(item.sku) ?? 0) + item.quantity)
    }
  }

  const targetBySku = new Map<string, number>()
  for (const product of PRODUCTS) {
    for (const variant of product.variants) targetBySku.set(variant.sku, variant.targetStock)
  }

  const receiptLines: ReceiptLinePlan[] = []
  const costTotals = new Map<string, { quantity: number; cost: number }>()
  const variants = [...variantsBySku.values()]
  for (const [index, variant] of variants.entries()) {
    const quantity = (targetBySku.get(variant.sku) ?? 0) + (soldBySku.get(variant.sku) ?? 0)
    if (quantity <= 0) continue

    const costs = [
      Math.round(variant.price * 0.42),
      Math.round(variant.price * 0.48),
      Math.round(variant.price * 0.55),
    ] as const

    const third = index % 4 === 0 && quantity >= 4 ? 2 : 0
    const remaining = quantity - third
    const first = Math.ceil(remaining / 2)
    const second = remaining - first

    const shares: Array<[0 | 1 | 2, number]> = [
      [0, first],
      [1, second],
      [2, third],
    ]
    for (const [share, count] of shares) {
      if (count <= 0) continue
      const unitCost = costs[share]
      receiptLines.push({ variant, quantity: count, unitCost, share })
      const total = costTotals.get(variant.sku) ?? { quantity: 0, cost: 0 }
      total.quantity += count
      total.cost += count * unitCost
      costTotals.set(variant.sku, total)
    }
  }

  const averageCostBySku = new Map<string, number>()
  for (const [sku, total] of costTotals) {
    averageCostBySku.set(sku, Math.round(total.cost / total.quantity))
  }

  for (const receiptPlan of RECEIPTS) {
    const existing = await prisma.inventoryReceipt.findUnique({ where: { code: receiptPlan.code } })
    if (existing) continue

    const lines = receiptLines.filter((line) => line.share === receiptPlan.share)
    const totalCost = lines.reduce((sum, line) => sum + line.quantity * line.unitCost, 0)
    const createdAt = daysAgoAt(receiptPlan.daysAgo, 2)
    const receipt = await prisma.inventoryReceipt.create({
      data: {
        code: receiptPlan.code,
        supplier: receiptPlan.supplier,
        note: receiptPlan.note,
        totalCost,
        createdBy: admin.id,
        createdAt,
        items: {
          create: lines.map((line) => ({
            variantId: line.variant.id,
            quantity: line.quantity,
            unitCost: line.unitCost,
          })),
        },
      },
    })

    await prisma.stockMovement.createMany({
      data: lines.map((line) => ({
        variantId: line.variant.id,
        delta: line.quantity,
        reason: 'RECEIPT',
        refType: 'RECEIPT',
        refId: receipt.id,
        unitCost: line.unitCost,
        createdAt,
      })),
    })
  }

  for (const [index, orderPlan] of ORDERS.entries()) {
    const existing = await prisma.order.findUnique({ where: { code: orderPlan.code } })
    if (existing) continue

    const customer = customers.get(orderPlan.customer)
    if (!customer) throw new Error(`Seed order ${orderPlan.code} references an unknown customer`)

    const address = await prisma.address.findFirst({
      where: { userId: customer.id, isDefault: true },
    })
    if (!address) throw new Error(`Seed order ${orderPlan.code} needs a default address`)

    const createdAt = orderTime(orderPlan.daysAgo)
    const items = orderPlan.items.map((item) => {
      const variant = variantsBySku.get(item.sku)
      if (!variant) throw new Error(`Seed order ${orderPlan.code} references unknown SKU ${item.sku}`)
      return { ...item, variant }
    })
    const subtotal = items.reduce((sum, item) => sum + item.variant.price * item.quantity, 0)
    const shippingFee = env.SHIPPING_FEE
    const total = subtotal + shippingFee

    let paidAt: Date | null = null
    if (orderPlan.method === 'ONLINE') {
      if (orderPlan.payment === 'SUCCEEDED') paidAt = addHours(createdAt, 0.1)
      if (orderPlan.payment === 'FAILED_THEN_SUCCEEDED') paidAt = addHours(createdAt, 1.1)
    } else if (orderPlan.status === 'DELIVERED') {
      paidAt = earlier(addHours(createdAt, 48), new Date(NOW.getTime() - 60_000))
    }

    const order = await prisma.order.create({
      data: {
        code: orderPlan.code,
        userId: customer.id,
        clientOrderId: clientOrderId(index + 1),
        status: orderPlan.status,
        paymentMethod: orderPlan.method,
        paidAt,
        recipientName: address.recipientName,
        recipientPhone: address.phone,
        shippingAddress: address.line,
        subtotal,
        shippingFee,
        total,
        note: orderPlan.note,
        createdAt,
        items: {
          create: items.map((item) => ({
            productId: item.variant.productId,
            variantId: item.variant.id,
            productName: item.variant.productName,
            variantSku: item.variant.sku,
            size: item.variant.size,
            color: item.variant.color,
            unitPrice: item.variant.price,
            unitCost: averageCostBySku.get(item.variant.sku) ?? null,
            quantity: item.quantity,
          })),
        },
      },
    })

    await prisma.stockMovement.createMany({
      data: items.map((item) => ({
        variantId: item.variant.id,
        delta: -item.quantity,
        reason: 'SALE',
        refType: 'ORDER',
        refId: order.id,
        unitCost: null,
        createdAt,
      })),
    })

    if (orderPlan.status === 'CANCELLED') {
      await prisma.stockMovement.createMany({
        data: items.map((item) => ({
          variantId: item.variant.id,
          delta: item.quantity,
          reason: 'CANCEL_RESTOCK',
          refType: 'ORDER',
          refId: order.id,
          unitCost: null,
          createdAt: addHours(createdAt, 2),
        })),
      })
    }

    if (orderPlan.method === 'ONLINE' && orderPlan.payment) {
      const failureAt = addHours(createdAt, 0.05)
      const successAt = addHours(createdAt, 1.1)
      const attempts: Array<{ status: 'SUCCEEDED' | 'FAILED' | 'PENDING'; createdAt: Date; payDate: Date | null }> = []
      if (orderPlan.payment === 'SUCCEEDED') {
        attempts.push({ status: 'SUCCEEDED', createdAt: failureAt, payDate: failureAt })
      } else if (orderPlan.payment === 'FAILED') {
        attempts.push({ status: 'FAILED', createdAt: failureAt, payDate: null })
      } else if (orderPlan.payment === 'PENDING') {
        attempts.push({ status: 'PENDING', createdAt: failureAt, payDate: null })
      } else {
        attempts.push({ status: 'FAILED', createdAt: failureAt, payDate: null })
        attempts.push({ status: 'SUCCEEDED', createdAt: successAt, payDate: successAt })
      }

      for (const [attemptIndex, attempt] of attempts.entries()) {
        await prisma.payment.create({
          data: {
            orderId: order.id,
            provider: 'MOCK',
            status: attempt.status,
            amount: total,
            txnRef: `SEED-${orderPlan.code}-${attemptIndex + 1}`,
            providerTxnNo: attempt.status === 'SUCCEEDED' ? `MOCK-${orderPlan.code}-${attemptIndex + 1}` : null,
            bankCode: attempt.status === 'SUCCEEDED' ? 'NCB' : null,
            payDate: attempt.payDate,
            failureReason: attempt.status === 'FAILED' ? 'SEED_FAILED' : null,
            createdAt: attempt.createdAt,
          },
        })
      }
    }
  }

  // Make the ledger the single source of truth for stock, even when a partial seed is re-run.
  await prisma.$executeRaw`
    UPDATE product_variant v
    LEFT JOIN (
      SELECT variant_id, COALESCE(SUM(delta), 0) AS stock
      FROM stock_movement
      GROUP BY variant_id
    ) m ON m.variant_id = v.id
    SET v.stock = COALESCE(m.stock, 0)
  `
}

const isDirectRun = process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])

if (isDirectRun) {
  seed()
    .catch((error: unknown) => {
      console.error('Seed failed', error)
      process.exitCode = 1
    })
    .finally(async () => {
      await prisma.$disconnect()
    })
}
