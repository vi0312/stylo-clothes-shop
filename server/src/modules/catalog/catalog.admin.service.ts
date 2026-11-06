import { conflict, notFound } from '../../utils/errors.js'
import type { CatalogStatus } from '../../generated/prisma/client.js'
import type {
  AdminVariantDto,
  AdminProductDetail,
  AdminProductSummary,
  BrandDto,
  CategoryDto,
} from './catalog.dto.js'
import type {
  AdminListQuery,
  CreateBrandInput,
  CreateCategoryInput,
  CreateProductInput,
  CreateVariantInput,
} from './catalog.schema.js'
import type {
  AdminProductSummaryRow,
  AdminVariantRow,
  AdminVariantRowWithProduct,
  ProductRow,
  TaxonomyWithCount,
} from './catalog.admin.repository.js'
import * as repository from './catalog.admin.repository.js'

type TaxonomyKind = 'category' | 'brand'

function taxonomyNotFound(kind: TaxonomyKind, id: number): string {
  return `${kind === 'category' ? 'Category' : 'Brand'} ${id} not found`
}

function taxonomyTaken(kind: TaxonomyKind): string {
  return `${kind === 'category' ? 'Category' : 'Brand'} name already taken`
}

async function requireTaxonomy(
  kind: TaxonomyKind,
  id: number,
): Promise<{ id: number; name: string; status: CatalogStatus }> {
  const row =
    kind === 'category' ? await repository.findCategoryById(id) : await repository.findBrandById(id)
  if (!row) {
    throw notFound('NOT_FOUND', taxonomyNotFound(kind, id))
  }
  return row
}

function toTaxonomyDto(row: TaxonomyWithCount & { productCount?: number }): CategoryDto & BrandDto {
  const productCount = row.productCount ?? row._count?.products ?? 0
  return {
    id: row.id,
    name: row.name,
    status: row.status,
    productCount,
    createdAt: row.createdAt.toISOString(),
    updatedAt: row.updatedAt.toISOString(),
  }
}

export async function listTaxonomy(
  kind: TaxonomyKind,
  query: AdminListQuery,
): Promise<{ items: CategoryDto[]; page: number; limit: number; total: number }> {
  const result =
    kind === 'category'
      ? await repository.listAdminCategories(query)
      : await repository.listAdminBrands(query)

  return {
    items: result.items.map(toTaxonomyDto),
    page: query.page,
    limit: query.limit,
    total: result.total,
  }
}

export async function createTaxonomy(
  kind: TaxonomyKind,
  input: CreateCategoryInput | CreateBrandInput,
): Promise<CategoryDto & BrandDto> {
  const name = input.name
  const existing =
    kind === 'category'
      ? await repository.findCategoryByName(name)
      : await repository.findBrandByName(name)
  if (existing) {
    throw conflict('NAME_TAKEN', taxonomyTaken(kind))
  }

  const row =
    kind === 'category'
      ? await repository.insertCategory({ name, status: 'ACTIVE' })
      : await repository.insertBrand({ name, status: 'ACTIVE' })

  return toTaxonomyDto({ ...row, _count: { products: 0 } })
}

export async function patchTaxonomy(
  kind: TaxonomyKind,
  id: number,
  patch: { name?: string; status?: CatalogStatus },
): Promise<CategoryDto & BrandDto> {
  await requireTaxonomy(kind, id)

  if (patch.name !== undefined) {
    const owner =
      kind === 'category'
        ? await repository.findCategoryByName(patch.name)
        : await repository.findBrandByName(patch.name)
    if (owner && owner.id !== id) {
      throw conflict('NAME_TAKEN', taxonomyTaken(kind))
    }
  }

  const updated =
    kind === 'category'
      ? await repository.updateCategory(id, patch)
      : await repository.updateBrand(id, patch)

  const productCount =
    kind === 'category'
      ? await repository.countProductsForCategory(id)
      : await repository.countProductsForBrand(id)

  return toTaxonomyDto({ ...updated, _count: { products: productCount } })
}

export async function deleteTaxonomy(kind: TaxonomyKind, id: number): Promise<void> {
  await requireTaxonomy(kind, id)

  const referenced =
    kind === 'category'
      ? await repository.countProductsForCategory(id)
      : await repository.countProductsForBrand(id)
  if (referenced > 0) {
    throw conflict('IN_USE', `${kind} is still referenced by products`, { entity: kind, id })
  }

  try {
    if (kind === 'category') {
      await repository.deleteCategory(id)
    } else {
      await repository.deleteBrand(id)
    }
  } catch (error) {
    // FK from product.category_id/brand_id is Restrict; the pre-check above is the normal path
    if (isForeignKeyError(error)) {
      throw conflict('IN_USE', `${kind} is still referenced by products`, { entity: kind, id })
    }
    throw error
  }
}

interface KeyedError {
  code?: string
  message?: string
}

/**
 * The MariaDB adapter surfaces FK violations as driver errors (`ER_ROW_IS_REFERENCED_2`),
 * not Prisma P2003, so both shapes are checked.
 */
function isForeignKeyError(error: unknown): boolean {
  if (error === null || typeof error !== 'object') {
    return false
  }
  const candidate: KeyedError = error
  return (
    candidate.code === 'P2003' ||
    candidate.code === 'ER_ROW_IS_REFERENCED_2' ||
    candidate.code === 'ER_NO_REFERENCED_ROW_2'
  )
}

function isUniqueKeyError(
  error: unknown,
  key: 'sku' | 'variant',
): error is { code: string; message?: string } {
  if (error === null || typeof error !== 'object') {
    return false
  }
  const candidate: KeyedError = error
  if (candidate.code !== 'P2002' && candidate.code !== 'ER_DUP_ENTRY') {
    return false
  }
  if (candidate.code === 'P2002') {
    // Prisma P2002 meta.target names the index; match on the raw message otherwise
    return true
  }
  const text = (candidate.message ?? '').toLowerCase()
  return key === 'sku' ? text.includes('sku') : text.includes('size')
}

// ─── Products ────────────────────────────────────────────────────────────

export function mapAdminVariant(variant: AdminVariantRow): AdminVariantDto {
  return {
    id: variant.id,
    productId: variant.productId,
    sku: variant.sku,
    size: variant.size,
    color: variant.color,
    price: variant.price,
    stock: variant.stock,
    createdAt: variant.createdAt.toISOString(),
    updatedAt: variant.updatedAt.toISOString(),
  }
}

function toAdminProductDetail(product: ProductRow): AdminProductDetail {
  const prices = product.variants.map((v: { price: number }) => v.price)

  return {
    id: product.id,
    name: product.name,
    description: product.description,
    status: product.status,
    category: { id: product.category.id, name: product.category.name },
    brand: { id: product.brand.id, name: product.brand.name },
    images: product.images.map((i) => ({ id: i.id, url: i.url, position: i.position })),
    variants: product.variants.map(mapAdminVariant),
    minPrice: prices.length > 0 ? Math.min(...prices) : null,
    maxPrice: prices.length > 0 ? Math.max(...prices) : null,
    createdAt: product.createdAt.toISOString(),
    updatedAt: product.updatedAt.toISOString(),
  }
}

function toAdminProductSummary(product: AdminProductSummaryRow): AdminProductSummary {
  const stats = repository.mapAdminVariantStats(product)

  return {
    id: product.id,
    name: product.name,
    description: product.description,
    status: product.status,
    category: product.category,
    brand: product.brand,
    coverUrl: product.images[0]?.url ?? null,
    ...stats,
    createdAt: product.createdAt.toISOString(),
    updatedAt: product.updatedAt.toISOString(),
  }
}

export async function listAdminProducts(query: AdminListQuery): Promise<{
  items: AdminProductSummary[]
  page: number
  limit: number
  total: number
}> {
  const { products, total } = await repository.listAdminProducts(query)
  return {
    items: products.map(toAdminProductSummary),
    page: query.page,
    limit: query.limit,
    total,
  }
}

export async function getAdminProductDetail(id: number): Promise<AdminProductDetail> {
  const product = await repository.findAdminProductDetail(id)
  if (!product) {
    throw notFound('NOT_FOUND', `Product ${id} not found`)
  }
  return toAdminProductDetail(product)
}

export async function createProduct(input: CreateProductInput): Promise<AdminProductDetail> {
  await requireTaxonomy('category', input.categoryId)
  await requireTaxonomy('brand', input.brandId)

  const created = await repository.insertProduct(input)
  return toAdminProductDetail(created)
}

export async function patchProduct(
  id: number,
  patch: {
    name?: string
    description?: string | null
    categoryId?: number
    brandId?: number
    status?: CatalogStatus
  },
): Promise<AdminProductDetail> {
  const existing = await repository.findProductById(id)
  if (!existing) {
    throw notFound('NOT_FOUND', `Product ${id} not found`)
  }

  if (patch.categoryId !== undefined) {
    await requireTaxonomy('category', patch.categoryId)
  }
  if (patch.brandId !== undefined) {
    await requireTaxonomy('brand', patch.brandId)
  }

  const updated: ProductRow = await repository.updateProduct(id, patch)
  return toAdminProductDetail(updated)
}

export async function deleteProductSafely(id: number): Promise<void> {
  const product = await repository.findProductById(id)
  if (!product) {
    throw notFound('NOT_FOUND', `Product ${id} not found`)
  }

  if (await repository.productHasLedgerReferences(id)) {
    throw conflict('IN_USE', 'Product is referenced by orders, receipts or stock movements', {
      entity: 'product',
      id,
    })
  }

  await repository.deleteProduct(id)
}

// ─── Variants ────────────────────────────────────────────────────────────

export async function createVariant(
  productId: number,
  input: CreateVariantInput,
): Promise<AdminVariantDto> {
  const product = await repository.findProductById(productId)
  if (!product) {
    throw notFound('NOT_FOUND', `Product ${productId} not found`)
  }

  const skuOwner = await repository.findSkuOwner(input.sku)
  if (skuOwner) {
    throw conflict('SKU_TAKEN', 'SKU already taken', { sku: input.sku })
  }

  const twin = await repository.findVariantByNaturalKey(productId, input.size, input.color)
  if (twin) {
    throw conflict(
      'VARIANT_EXISTS',
      'A variant with the same size and color already exists on this product',
      { size: input.size, color: input.color },
    )
  }

  try {
    const created: AdminVariantRow = await repository.insertVariant({ productId, ...input })
    return mapAdminVariant(created)
  } catch (error) {
    // Concurrent POST can still win the unique races pre-checked above
    if (isUniqueKeyError(error, 'sku')) {
      throw conflict('SKU_TAKEN', 'SKU already taken', { sku: input.sku })
    }
    if (isUniqueKeyError(error, 'variant')) {
      throw conflict('VARIANT_EXISTS', 'Variant already exists on this product', {
        size: input.size,
        color: input.color,
      })
    }
    throw error
  }
}

export async function patchVariant(
  id: number,
  patch: { sku?: string; size?: string; color?: string; price?: number },
): Promise<AdminVariantDto> {
  const variant = await repository.findVariantWithProduct(id)
  if (!variant) {
    throw notFound('NOT_FOUND', `Variant ${id} not found`)
  }

  if (patch.sku !== undefined && patch.sku !== variant.sku) {
    const owner = await repository.findSkuOwner(patch.sku)
    if (owner && owner.id !== id) {
      throw conflict('SKU_TAKEN', 'SKU already taken', { sku: patch.sku })
    }
  }

  const nextSize = patch.size ?? variant.size
  const nextColor = patch.color ?? variant.color
  if (nextSize !== variant.size || nextColor !== variant.color) {
    const twin = await repository.findVariantByNaturalKey(variant.productId, nextSize, nextColor)
    if (twin && twin.id !== id) {
      throw conflict(
        'VARIANT_EXISTS',
        'A variant with the same size and color already exists on this product',
        { size: nextSize, color: nextColor },
      )
    }
  }

  try {
    const updated: AdminVariantRow = await repository.updateVariant(id, patch)
    return mapAdminVariant(updated)
  } catch (error) {
    if (isUniqueKeyError(error, 'sku')) {
      throw conflict('SKU_TAKEN', 'SKU already taken', { sku: patch.sku })
    }
    if (isUniqueKeyError(error, 'variant')) {
      throw conflict(
        'VARIANT_EXISTS',
        'A variant with the same size and color already exists on this product',
        { size: patch.size ?? variant.size, color: patch.color ?? variant.color },
      )
    }
    throw error
  }
}

export async function deleteVariantSafely(id: number): Promise<void> {
  const variant = await repository.findVariantWithProduct(id)
  if (!variant) {
    throw notFound('NOT_FOUND', `Variant ${id} not found`)
  }

  if (await repository.variantHasLedgerReferences(id)) {
    throw conflict('IN_USE', 'Variant is referenced by orders, receipts or stock movements', {
      entity: 'variant',
      id,
    })
  }

  await repository.deleteVariant(id)
}
