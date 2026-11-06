import { notFound } from '../../utils/errors.js'
import type { CatalogFilters, PaginatedResult, ProductCard, ProductDetail } from './catalog.dto.js'
import type { ListProductsQuery } from './catalog.schema.js'
import * as repository from './catalog.repository.js'

export async function listProducts(
  query: ListProductsQuery,
): Promise<PaginatedResult<ProductCard>> {
  const { ids, total } = await repository.listProductIdsAndTotal(query)
  const items = await repository.findProductsForCards(ids)

  return {
    items,
    page: query.page,
    limit: query.limit,
    total,
  }
}

export async function getProductDetail(id: number): Promise<ProductDetail> {
  const product = await repository.findProductDetailById(id)
  if (!product) {
    throw notFound('NOT_FOUND', 'Product not found')
  }
  return product
}

export function getFilters(): Promise<CatalogFilters> {
  return repository.getCatalogFilters()
}
