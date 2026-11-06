import type { CatalogStatus } from '../../src/generated/prisma/client.js'
import { prisma } from '../../src/lib/prisma.js'

let catalogSeq = 0

export async function createCategory(overrides: { name?: string; status?: CatalogStatus } = {}) {
  catalogSeq += 1
  return prisma.category.create({
    data: {
      name: overrides.name ?? `Category ${catalogSeq}`,
      status: overrides.status ?? 'ACTIVE',
    },
  })
}

export async function createBrand(overrides: { name?: string; status?: CatalogStatus } = {}) {
  catalogSeq += 1
  return prisma.brand.create({
    data: {
      name: overrides.name ?? `Brand ${catalogSeq}`,
      status: overrides.status ?? 'ACTIVE',
    },
  })
}

export interface CreateProductOptions {
  name?: string
  description?: string | null
  categoryId?: number
  brandId?: number
  status?: CatalogStatus
  images?: { url: string; position: number }[]
  variants?: {
    sku?: string
    size: string
    color: string
    price: number
    stock: number
  }[]
}

export async function createProduct(options: CreateProductOptions = {}) {
  catalogSeq += 1
  const categoryId = options.categoryId ?? (await createCategory()).id
  const brandId = options.brandId ?? (await createBrand()).id

  return prisma.product.create({
    data: {
      name: options.name ?? `Product ${catalogSeq}`,
      description: options.description ?? `Description for product ${catalogSeq}`,
      categoryId,
      brandId,
      status: options.status ?? 'ACTIVE',
      images: {
        create: options.images ?? [],
      },
      variants: {
        create: (options.variants ?? [
          {
            sku: `SKU-${catalogSeq}-M`,
            size: 'M',
            color: 'Đen',
            price: 100_000,
            stock: 10,
          },
        ]).map((v, i) => ({
          sku: v.sku ?? `SKU-${catalogSeq}-${i}`,
          size: v.size,
          color: v.color,
          price: v.price,
          stock: v.stock,
        })),
      },
    },
    include: {
      category: true,
      brand: true,
      images: true,
      variants: true,
    },
  })
}
