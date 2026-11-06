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
