import { Prisma } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import type { CatalogFilters, ProductCard, ProductDetail } from './catalog.dto.js'
import type { ListProductsQuery } from './catalog.schema.js'

function escapeLike(text: string): string {
  return text.replace(/[%_\\]/g, '\\$&')
}

export async function listProductIdsAndTotal(
  query: ListProductsQuery,
): Promise<{ ids: number[]; total: number }> {
  const conditions: Prisma.Sql[] = [Prisma.sql`p.status = 'ACTIVE'`]

  const variantConditions: Prisma.Sql[] = [Prisma.sql`fv.stock > 0`]
  if (query.color) {
    variantConditions.push(Prisma.sql`fv.color = ${query.color}`)
  }
  if (query.size) {
    variantConditions.push(Prisma.sql`fv.size = ${query.size}`)
  }
  if (query.minPrice !== undefined) {
    variantConditions.push(Prisma.sql`fv.price >= ${query.minPrice}`)
  }
  if (query.maxPrice !== undefined) {
    variantConditions.push(Prisma.sql`fv.price <= ${query.maxPrice}`)
  }

  conditions.push(Prisma.sql`EXISTS (
    SELECT 1 FROM product_variant fv
    WHERE fv.product_id = p.id
      AND ${Prisma.join(variantConditions, ' AND ')}
  )`)

  if (query.categoryId !== undefined) {
    conditions.push(Prisma.sql`p.category_id = ${query.categoryId}`)
  }
  if (query.brandId !== undefined) {
    conditions.push(Prisma.sql`p.brand_id = ${query.brandId}`)
  }
  if (query.q) {
    const pattern = `%${escapeLike(query.q)}%`
    conditions.push(Prisma.sql`(
      p.name LIKE ${pattern}
      OR b.name LIKE ${pattern}
      OR c.name LIKE ${pattern}
    )`)
  }

  const totalRows = await prisma.$queryRaw<{ total: bigint }[]>`
    SELECT COUNT(DISTINCT p.id) AS total
    FROM product p
    JOIN brand b ON p.brand_id = b.id
    JOIN category c ON p.category_id = c.id
    WHERE ${Prisma.join(conditions, ' AND ')}
  `
  const total = Number(totalRows[0]?.total ?? 0)
  if (total === 0) {
    return { ids: [], total: 0 }
  }

  const offset = (query.page - 1) * query.limit
  if (offset >= total) {
    return { ids: [], total }
  }

  let rows: { id: number }[]
  switch (query.sort) {
    case 'name_asc':
      rows = await prisma.$queryRaw<{ id: number }[]>`
        SELECT p.id
        FROM product p
        JOIN brand b ON p.brand_id = b.id
        JOIN category c ON p.category_id = c.id
        WHERE ${Prisma.join(conditions, ' AND ')}
        ORDER BY p.name ASC, p.id ASC
        LIMIT ${query.limit} OFFSET ${offset}
      `
      break
    case 'price_asc':
      rows = await prisma.$queryRaw<{ id: number }[]>`
        SELECT p.id, MIN(spv.price) AS min_price
        FROM product p
        JOIN brand b ON p.brand_id = b.id
        JOIN category c ON p.category_id = c.id
        JOIN product_variant spv ON p.id = spv.product_id AND spv.stock > 0
        WHERE ${Prisma.join(conditions, ' AND ')}
        GROUP BY p.id
        ORDER BY min_price ASC, p.id ASC
        LIMIT ${query.limit} OFFSET ${offset}
      `
      break
    case 'price_desc':
      rows = await prisma.$queryRaw<{ id: number }[]>`
        SELECT p.id, MIN(spv.price) AS min_price
        FROM product p
        JOIN brand b ON p.brand_id = b.id
        JOIN category c ON p.category_id = c.id
        JOIN product_variant spv ON p.id = spv.product_id AND spv.stock > 0
        WHERE ${Prisma.join(conditions, ' AND ')}
        GROUP BY p.id
        ORDER BY min_price DESC, p.id DESC
        LIMIT ${query.limit} OFFSET ${offset}
      `
      break
    case 'newest':
    default:
      rows = await prisma.$queryRaw<{ id: number }[]>`
        SELECT p.id
        FROM product p
        JOIN brand b ON p.brand_id = b.id
        JOIN category c ON p.category_id = c.id
        WHERE ${Prisma.join(conditions, ' AND ')}
        ORDER BY p.created_at DESC, p.id DESC
        LIMIT ${query.limit} OFFSET ${offset}
      `
      break
  }

  return { ids: rows.map((r) => r.id), total }
}

export async function findProductsForCards(ids: number[]): Promise<ProductCard[]> {
  if (ids.length === 0) {
    return []
  }

  const products = await prisma.product.findMany({
    where: { id: { in: ids } },
    include: {
      category: { select: { id: true, name: true } },
      brand: { select: { id: true, name: true } },
      images: {
        where: { position: 0 },
        select: { url: true },
      },
      variants: {
        where: { stock: { gt: 0 } },
        select: { price: true },
      },
    },
  })

  const productMap = new Map(products.map((p) => [p.id, p]))

  const cards: ProductCard[] = []
  for (const id of ids) {
    const product = productMap.get(id)
    if (!product) continue
    const inStockPrices = product.variants.map((v) => v.price)
    cards.push({
      id: product.id,
      name: product.name,
      coverUrl: product.images[0]?.url ?? null,
      minPrice: inStockPrices.length > 0 ? Math.min(...inStockPrices) : null,
      maxPrice: inStockPrices.length > 0 ? Math.max(...inStockPrices) : null,
      category: { id: product.category.id, name: product.category.name },
      brand: { id: product.brand.id, name: product.brand.name },
    })
  }
  return cards
}

export async function findProductDetailById(id: number): Promise<ProductDetail | null> {
  const product = await prisma.product.findUnique({
    where: { id },
    include: {
      category: { select: { id: true, name: true } },
      brand: { select: { id: true, name: true } },
      images: {
        orderBy: { position: 'asc' },
        select: { id: true, url: true, position: true },
      },
      variants: {
        orderBy: { id: 'asc' },
        select: { id: true, sku: true, size: true, color: true, price: true, stock: true },
      },
    },
  })

  if (!product || product.status !== 'ACTIVE') {
    return null
  }

  const inStockPrices = product.variants
    .filter((v) => v.stock > 0)
    .map((v) => v.price)

  return {
    id: product.id,
    name: product.name,
    description: product.description,
    category: { id: product.category.id, name: product.category.name },
    brand: { id: product.brand.id, name: product.brand.name },
    images: product.images,
    variants: product.variants,
    minPrice: inStockPrices.length > 0 ? Math.min(...inStockPrices) : null,
    maxPrice: inStockPrices.length > 0 ? Math.max(...inStockPrices) : null,
    createdAt: product.createdAt.toISOString(),
    updatedAt: product.updatedAt.toISOString(),
  }
}

export async function getCatalogFilters(): Promise<CatalogFilters> {
  const [categories, brands, colors, sizes, priceRangeRows] = await Promise.all([
    prisma.$queryRaw<{ id: number; name: string }[]>`
      SELECT DISTINCT c.id, c.name
      FROM category c
      JOIN product p ON p.category_id = c.id
      JOIN product_variant pv ON pv.product_id = p.id
      WHERE c.status = 'ACTIVE'
        AND p.status = 'ACTIVE'
        AND pv.stock > 0
      ORDER BY c.name ASC, c.id ASC
    `,
    prisma.$queryRaw<{ id: number; name: string }[]>`
      SELECT DISTINCT b.id, b.name
      FROM brand b
      JOIN product p ON p.brand_id = b.id
      JOIN product_variant pv ON pv.product_id = p.id
      WHERE b.status = 'ACTIVE'
        AND p.status = 'ACTIVE'
        AND pv.stock > 0
      ORDER BY b.name ASC, b.id ASC
    `,
    prisma.$queryRaw<{ color: string }[]>`
      SELECT DISTINCT pv.color
      FROM product_variant pv
      JOIN product p ON p.id = pv.product_id
      WHERE p.status = 'ACTIVE'
        AND pv.stock > 0
      ORDER BY pv.color ASC
    `,
    prisma.$queryRaw<{ size: string }[]>`
      SELECT DISTINCT pv.size
      FROM product_variant pv
      JOIN product p ON p.id = pv.product_id
      WHERE p.status = 'ACTIVE'
        AND pv.stock > 0
      ORDER BY pv.size ASC
    `,
    prisma.$queryRaw<{ minPrice: number | null; maxPrice: number | null }[]>`
      SELECT
        MIN(pv.price) AS minPrice,
        MAX(pv.price) AS maxPrice
      FROM product_variant pv
      JOIN product p ON p.id = pv.product_id
      WHERE p.status = 'ACTIVE'
        AND pv.stock > 0
    `,
  ])

  const row = priceRangeRows[0]
  const priceRange = {
    min: row?.minPrice !== null && row?.minPrice !== undefined ? Number(row.minPrice) : null,
    max: row?.maxPrice !== null && row?.maxPrice !== undefined ? Number(row.maxPrice) : null,
  }

  return {
    categories: categories.map((c) => ({ id: c.id, name: c.name })),
    brands: brands.map((b) => ({ id: b.id, name: b.name })),
    colors: colors.map((c) => c.color),
    sizes: sizes.map((s) => s.size),
    priceRange,
  }
}
