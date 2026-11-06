import { Prisma } from '../../generated/prisma/client.js'
import { prisma } from '../../lib/prisma.js'
import type { CatalogStatus } from '../../generated/prisma/client.js'
import type { AdminListQuery, CreateProductInput, PatchProductInput } from './catalog.schema.js'
import { MAX_PRODUCT_IMAGES } from './catalog.constants.js'

const productInclude = {
  category: true,
  brand: true,
  images: { orderBy: { position: 'asc' as const } },
  variants: { orderBy: { id: 'asc' as const } },
} satisfies Prisma.ProductInclude

export type ProductRow = Prisma.ProductGetPayload<{ include: typeof productInclude }>

/** Summary rows keep only price and stock per variant; stats are computed in JS. */
export interface AdminProductSummaryRow {
  id: number
  name: string
  description: string | null
  status: CatalogStatus
  category: { id: number; name: string }
  brand: { id: number; name: string }
  images: { url: string }[]
  variants: { price: number; stock: number }[]
  createdAt: Date
  updatedAt: Date
}

export interface AdminVariantRowWithProduct extends AdminVariantRow {
  product: {
    id: number
    name: string
    status: CatalogStatus
  }
}

export type AdminVariantRow = Prisma.ProductVariantGetPayload<Record<string, never>>

export interface TaxonomyRow {
  id: number
  name: string
  status: CatalogStatus
  createdAt: Date
  updatedAt: Date
}

function catalogOrderBy(sort: AdminListQuery['sort']) {
  return sort === 'name_asc'
    ? [{ name: 'asc' as const }, { id: 'asc' as const }]
    : sort === 'oldest'
      ? [{ createdAt: 'asc' as const }, { id: 'asc' as const }]
      : [{ createdAt: 'desc' as const }, { id: 'desc' as const }]
}

export function listAdminProducts(
  query: AdminListQuery,
): Promise<{ products: AdminProductSummaryRow[]; total: number }> {
  const where = buildAdminProductWhere(query)

  return Promise.all([
    prisma.product.count({ where }),
    prisma.product.findMany({
      where,
      include: {
        category: { select: { id: true, name: true } },
        brand: { select: { id: true, name: true } },
        images: { where: { position: 0 }, select: { url: true } },
        variants: { select: { price: true, stock: true } },
      },
      orderBy: catalogOrderBy(query.sort),
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ]).then(([total, products]) => ({ products, total }))
}

function buildAdminProductWhere(query: AdminListQuery): Prisma.ProductWhereInput {
  const where: Prisma.ProductWhereInput = {}

  if (query.status !== undefined) {
    where.status = query.status
  }
  if (query.categoryId !== undefined) {
    where.categoryId = query.categoryId
  }
  if (query.brandId !== undefined) {
    where.brandId = query.brandId
  }
  if (query.q) {
    where.name = { contains: query.q }
  }

  return where
}

export async function findAdminProductDetail(
  id: number,
): Promise<ProductRow | null> {
  return prisma.product.findUnique({
    where: { id },
    include: productInclude,
  })
}

export async function insertCategory(data: { name: string; status: CatalogStatus }) {
  return prisma.category.create({ data })
}

export async function insertBrand(data: { name: string; status: CatalogStatus }) {
  return prisma.brand.create({ data })
}

export async function findCategoryByName(name: string) {
  return prisma.category.findUnique({ where: { name } })
}

export async function findBrandByName(name: string) {
  return prisma.brand.findUnique({ where: { name } })
}

export async function findCategoryById(id: number) {
  return prisma.category.findUnique({ where: { id } })
}

export async function findBrandById(id: number) {
  return prisma.brand.findUnique({ where: { id } })
}

export async function findProductById(id: number) {
  return prisma.product.findUnique({ where: { id } })
}

export async function countProductsForCategory(categoryId: number): Promise<number> {
  return prisma.product.count({ where: { categoryId } })
}

export async function countProductsForBrand(brandId: number): Promise<number> {
  return prisma.product.count({ where: { brandId } })
}

export interface TaxonomyWithCount {
  id: number
  name: string
  status: CatalogStatus
  createdAt: Date
  updatedAt: Date
  _count: { products: number }
}

export async function listAdminCategories(
  query: AdminListQuery,
): Promise<{ items: TaxonomyWithCount[]; total: number }> {
  const where: Prisma.CategoryWhereInput = {}
  if (query.status !== undefined) where.status = query.status
  if (query.q) where.name = { contains: query.q }

  const [total, items] = await Promise.all([
    prisma.category.count({ where }),
    prisma.category.findMany({
      where,
      include: { _count: { select: { products: true } } },
      orderBy: catalogOrderBy(query.sort),
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ])

  return { items, total }
}

export async function listAdminBrands(
  query: AdminListQuery,
): Promise<{ items: TaxonomyWithCount[]; total: number }> {
  const where: Prisma.BrandWhereInput = {}
  if (query.status !== undefined) where.status = query.status
  if (query.q) where.name = { contains: query.q }

  const [total, items] = await Promise.all([
    prisma.brand.count({ where }),
    prisma.brand.findMany({
      where,
      include: { _count: { select: { products: true } } },
      orderBy: catalogOrderBy(query.sort),
      skip: (query.page - 1) * query.limit,
      take: query.limit,
    }),
  ])

  return { items, total }
}

export async function insertProduct(data: CreateProductInput): Promise<ProductRow> {
  return prisma.product.create({
    data: {
      name: data.name,
      description: data.description ?? null,
      categoryId: data.categoryId,
      brandId: data.brandId,
      status: data.status,
    },
    include: productInclude,
  })
}

export async function updateProduct(
  id: number,
  data: PatchProductInput,
): Promise<ProductRow> {
  return prisma.product.update({
    where: { id },
    data: {
      ...(data.name !== undefined && { name: data.name }),
      ...(data.description !== undefined && { description: data.description ?? null }),
      ...(data.categoryId !== undefined && { categoryId: data.categoryId }),
      ...(data.brandId !== undefined && { brandId: data.brandId }),
      ...(data.status !== undefined && { status: data.status }),
    },
    include: productInclude,
  })
}

export function mapAdminVariantStats(product: {
  variants: { price: number; stock: number }[]
}): { variantCount: number; totalStock: number; minPrice: number | null; maxPrice: number | null } {
  if (product.variants.length === 0) {
    return { variantCount: 0, totalStock: 0, minPrice: null, maxPrice: null }
  }
  const prices = product.variants.map((v) => v.price)

  return {
    variantCount: product.variants.length,
    totalStock: product.variants.reduce((sum, v) => sum + v.stock, 0),
    minPrice: Math.min(...prices),
    maxPrice: Math.max(...prices),
  }
}

export async function deleteProduct(id: number): Promise<string[]> {
  return prisma.$transaction(async (tx) => {
    await tx.$queryRaw`SELECT id FROM product WHERE id = ${id} FOR UPDATE`

    const imageRows = await tx.productImage.findMany({
      where: { productId: id },
      select: { id: true, url: true },
    })
    await tx.product.delete({ where: { id } })

    return imageRows.map((row) => row.url)
  })
}

export interface ProductImageRow {
  id: number
  url: string
  position: number
}

export type InsertProductImageResult =
  | { status: 'created'; image: ProductImageRow }
  | { status: 'missing' }
  | { status: 'full' }

/**
 * Locks the product row so two concurrent uploads cannot claim the same free
 * position; `(product_id, position)` stays unique even under HTTP races.
 */
export function insertProductImage(
  productId: number,
  url: string,
): Promise<InsertProductImageResult> {
  return prisma.$transaction(async (tx) => {
    const product = await tx.$queryRaw<{ id: number }[]>`
      SELECT id FROM product WHERE id = ${productId} FOR UPDATE
    `
    if (product.length === 0) {
      return { status: 'missing' as const }
    }

    const images = await tx.productImage.findMany({
      where: { productId },
      select: { position: true },
    })
    if (images.length >= MAX_PRODUCT_IMAGES) {
      return { status: 'full' as const }
    }

    const used = new Set(images.map((image) => image.position))
    let position = 0
    while (used.has(position)) {
      position += 1
    }

    const image = await tx.productImage.create({
      data: { productId, url, position },
      select: { id: true, url: true, position: true },
    })
    return { status: 'created' as const, image }
  })
}

export function findProductImage(productId: number, imageId: number) {
  return prisma.productImage.findFirst({
    where: { id: imageId, productId },
    select: { id: true, url: true },
  })
}

export async function deleteProductImage(productId: number, imageId: number): Promise<boolean> {
  const result = await prisma.productImage.deleteMany({ where: { id: imageId, productId } })
  return result.count > 0
}

export async function insertVariant(variant: {
  productId: number
  sku: string
  size: string
  color: string
  price: number
}): Promise<AdminVariantRow> {
  return prisma.productVariant.create({ data: variant })
}

export async function updateVariant(
  id: number,
  data: { sku?: string; size?: string; color?: string; price?: number },
): Promise<AdminVariantRow> {
  return prisma.productVariant.update({ where: { id }, data })
}

export function deleteVariant(id: number): Promise<void> {
  return prisma.productVariant.delete({ where: { id } }).then(() => undefined)
}

export async function findVariantWithProduct(
  id: number,
): Promise<AdminVariantRowWithProduct | null> {
  return prisma.productVariant.findUnique({
    where: { id },
    include: { product: { select: { id: true, name: true, status: true } } },
  })
}

export async function findSkuOwner(sku: string) {
  return prisma.productVariant.findUnique({
    where: { sku },
    include: { product: { select: { id: true, name: true, status: true } } },
  })
}

export async function findVariantByNaturalKey(productId: number, size: string, color: string) {
  return prisma.productVariant.findUnique({
    where: { productId_size_color: { productId, size, color } },
  })
}

export async function variantHasLedgerReferences(variantId: number): Promise<boolean> {
  const [orderItems, receiptItems, movements] = await Promise.all([
    prisma.orderItem.count({ where: { variantId } }),
    prisma.inventoryReceiptItem.count({ where: { variantId } }),
    prisma.stockMovement.count({ where: { variantId } }),
  ])
  return orderItems > 0 || receiptItems > 0 || movements > 0
}

export async function productHasLedgerReferences(productId: number): Promise<boolean> {
  const relations = await prisma.product.findUnique({
    where: { id: productId },
    select: {
      _count: { select: { variants: true } },
      variants: {
        select: {
          _count: { select: { orderItems: true, receiptItems: true, stockMovements: true } },
        },
      },
    },
  })

  if (!relations) {
    return false
  }

  return relations.variants.some(
    (variant) =>
      variant._count.orderItems > 0 ||
      variant._count.receiptItems > 0 ||
      variant._count.stockMovements > 0,
  )
}

export function updateCategory(id: number, data: { name?: string; status?: CatalogStatus }) {
  return prisma.category.update({ where: { id }, data })
}

export function updateBrand(id: number, data: { name?: string; status?: CatalogStatus }) {
  return prisma.brand.update({ where: { id }, data })
}

export function deleteCategory(id: number): Promise<void> {
  return prisma.category.delete({ where: { id } }).then(() => undefined)
}

export function deleteBrand(id: number): Promise<void> {
  return prisma.brand.delete({ where: { id } }).then(() => undefined)
}

