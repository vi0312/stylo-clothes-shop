package com.stylo.shop.feature.catalog.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.catalog.model.CatalogFilters
import com.stylo.shop.feature.catalog.model.CatalogQuery
import com.stylo.shop.feature.catalog.model.PaginatedProducts
import com.stylo.shop.feature.catalog.model.ProductDetail
import javax.inject.Inject

interface CatalogRepository {
    suspend fun listProducts(query: CatalogQuery): ApiResult<PaginatedProducts>
    suspend fun getProductDetail(id: Long): ApiResult<ProductDetail>
    suspend fun getFilters(): ApiResult<CatalogFilters>
}

class DefaultCatalogRepository @Inject constructor(
    private val api: CatalogApi,
) : CatalogRepository {

    override suspend fun listProducts(query: CatalogQuery): ApiResult<PaginatedProducts> = apiCall {
        api.listProducts(
            q = query.q?.takeIf { it.isNotBlank() },
            categoryId = query.categoryId,
            brandId = query.brandId,
            color = query.color,
            size = query.size,
            minPrice = query.minPrice,
            maxPrice = query.maxPrice,
            sort = query.sort,
            page = query.page,
            limit = query.limit,
        ).toModel()
    }

    override suspend fun getProductDetail(id: Long): ApiResult<ProductDetail> = apiCall {
        api.getProductDetail(id).toModel()
    }

    override suspend fun getFilters(): ApiResult<CatalogFilters> = apiCall {
        api.getFilters().toModel()
    }
}
