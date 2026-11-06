package com.stylo.shop.feature.catalog.data

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CatalogApi {

    @GET("products")
    suspend fun listProducts(
        @Query("q") q: String? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("brandId") brandId: Long? = null,
        @Query("color") color: String? = null,
        @Query("size") size: String? = null,
        @Query("minPrice") minPrice: Long? = null,
        @Query("maxPrice") maxPrice: Long? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null,
        @Query("limit") limit: Int? = null,
    ): PaginatedProductsDto

    @GET("products/{id}")
    suspend fun getProductDetail(
        @Path("id") id: Long,
    ): ProductDetailDto

    @GET("catalog/filters")
    suspend fun getFilters(): CatalogFiltersDto
}
