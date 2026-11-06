import type { CatalogStatus } from '../../generated/prisma/client.js'

export interface CategoryRef {
  id: number
  name: string
}

export interface BrandRef {
  id: number
  name: string
}

export interface ProductCard {
  id: number
  name: string
  coverUrl: string | null
  minPrice: number | null
  maxPrice: number | null
  category: CategoryRef
  brand: BrandRef
}

export interface ProductImageDto {
  id: number
  url: string
  position: number
}

export interface VariantDto {
  id: number
  sku: string
  size: string
  color: string
  price: number
  stock: number
}

export interface ProductDetail {
  id: number
  name: string
  description: string | null
  category: CategoryRef
  brand: BrandRef
  images: ProductImageDto[]
  variants: VariantDto[]
  minPrice: number | null
  maxPrice: number | null
  createdAt: string
  updatedAt: string
}

export interface PriceRange {
  min: number | null
  max: number | null
}

export interface CatalogFilters {
  categories: CategoryRef[]
  brands: BrandRef[]
  colors: string[]
  sizes: string[]
  priceRange: PriceRange
}

export interface PaginatedResult<T> {
  items: T[]
  page: number
  limit: number
  total: number
}

export interface EntityRef {
  id: number
  name: string
}

export interface CategoryDto {
  id: number
  name: string
  status: CatalogStatus
  productCount: number
  createdAt: string
  updatedAt: string
}

export interface BrandDto {
  id: number
  name: string
  status: CatalogStatus
  productCount: number
  createdAt: string
  updatedAt: string
}

export interface AdminProductSummary {
  id: number
  name: string
  description: string | null
  status: CatalogStatus
  category: EntityRef
  brand: EntityRef
  coverUrl: string | null
  variantCount: number
  totalStock: number
  minPrice: number | null
  maxPrice: number | null
  createdAt: string
  updatedAt: string
}

export interface AdminVariantDto {
  id: number
  productId: number
  sku: string
  size: string
  color: string
  price: number
  stock: number
  createdAt: string
  updatedAt: string
}

export interface AdminProductDetail {
  id: number
  name: string
  description: string | null
  status: CatalogStatus
  category: EntityRef
  brand: EntityRef
  images: ProductImageDto[]
  variants: AdminVariantDto[]
  minPrice: number | null
  maxPrice: number | null
  createdAt: string
  updatedAt: string
}
