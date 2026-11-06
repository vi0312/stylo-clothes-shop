package com.stylo.shop.feature.admin.data

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminCatalogApi {

    @GET("admin/products")
    suspend fun listProducts(
        @Query("q") q: String? = null,
        @Query("status") status: String? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("brandId") brandId: Long? = null,
        @Query("sort") sort: String = "newest",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): AdminPaginatedProductsDto

    @GET("admin/products/{id}")
    suspend fun getProduct(@Path("id") id: Long): AdminProductDetailDto

    @POST("admin/products")
    suspend fun createProduct(@Body body: CreateProductRequest): AdminProductDetailDto

    @PATCH("admin/products/{id}")
    suspend fun updateProduct(
        @Path("id") id: Long,
        @Body body: UpdateProductRequest,
    ): AdminProductDetailDto

    @DELETE("admin/products/{id}")
    suspend fun deleteProduct(@Path("id") id: Long)

    @POST("admin/products/{id}/variants")
    suspend fun createVariant(
        @Path("id") productId: Long,
        @Body body: CreateVariantRequest,
    ): AdminVariantDto

    @PATCH("admin/variants/{id}")
    suspend fun updateVariant(
        @Path("id") id: Long,
        @Body body: UpdateVariantRequest,
    ): AdminVariantDto

    @DELETE("admin/variants/{id}")
    suspend fun deleteVariant(@Path("id") id: Long)

    @Multipart
    @POST("admin/products/{id}/images")
    suspend fun uploadImage(
        @Path("id") productId: Long,
        @Part file: MultipartBody.Part,
    ): AdminProductImageDto

    @DELETE("admin/products/{id}/images/{imageId}")
    suspend fun deleteImage(
        @Path("id") productId: Long,
        @Path("imageId") imageId: Long,
    )

    @GET("admin/categories")
    suspend fun listCategories(
        @Query("q") q: String? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): AdminPaginatedTaxonomyDto

    @POST("admin/categories")
    suspend fun createCategory(@Body body: CreateTaxonomyRequest): AdminTaxonomyDto

    @PATCH("admin/categories/{id}")
    suspend fun updateCategory(
        @Path("id") id: Long,
        @Body body: UpdateTaxonomyRequest,
    ): AdminTaxonomyDto

    @DELETE("admin/categories/{id}")
    suspend fun deleteCategory(@Path("id") id: Long)

    @GET("admin/brands")
    suspend fun listBrands(
        @Query("q") q: String? = null,
        @Query("status") status: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): AdminPaginatedTaxonomyDto

    @POST("admin/brands")
    suspend fun createBrand(@Body body: CreateTaxonomyRequest): AdminTaxonomyDto

    @PATCH("admin/brands/{id}")
    suspend fun updateBrand(
        @Path("id") id: Long,
        @Body body: UpdateTaxonomyRequest,
    ): AdminTaxonomyDto

    @DELETE("admin/brands/{id}")
    suspend fun deleteBrand(@Path("id") id: Long)
}
