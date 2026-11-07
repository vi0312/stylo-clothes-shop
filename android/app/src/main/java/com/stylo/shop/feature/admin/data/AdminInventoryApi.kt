package com.stylo.shop.feature.admin.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminInventoryApi {

    @GET("admin/inventory-receipts")
    suspend fun listReceipts(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): AdminReceiptPageDto

    @GET("admin/inventory-receipts/{id}")
    suspend fun getReceipt(@Path("id") id: Long): AdminReceiptDetailDto

    @POST("admin/inventory-receipts")
    suspend fun createReceipt(@Body body: CreateReceiptRequest): AdminReceiptDetailDto

    @GET("admin/variants")
    suspend fun searchVariants(
        @Query("q") q: String? = null,
        @Query("limit") limit: Int = 20,
    ): AdminVariantSearchPageDto
}

@Serializable
data class AdminReceiptPageDto(
    val items: List<AdminReceiptSummaryDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
)

@Serializable
data class AdminReceiptSummaryDto(
    val id: Long,
    val code: String,
    val supplier: String,
    val note: String? = null,
    val totalCost: Long,
    val itemCount: Int,
    val createdBy: AdminReceiptCreatorDto,
    val createdAt: String,
)

@Serializable
data class AdminReceiptDetailDto(
    val id: Long,
    val code: String,
    val supplier: String,
    val note: String? = null,
    val totalCost: Long,
    val itemCount: Int,
    val createdBy: AdminReceiptCreatorDto,
    val items: List<AdminReceiptItemDto> = emptyList(),
    val createdAt: String,
)

@Serializable
data class AdminReceiptCreatorDto(
    val id: Long,
    val fullName: String,
)

@Serializable
data class AdminReceiptItemDto(
    val id: Long,
    val variantId: Long,
    val sku: String,
    val productName: String,
    val size: String,
    val color: String,
    val quantity: Int,
    val unitCost: Long,
    val lineTotal: Long,
)

@Serializable
data class CreateReceiptRequest(
    val supplier: String,
    val note: String? = null,
    val items: List<CreateReceiptItemRequest>,
)

@Serializable
data class CreateReceiptItemRequest(
    val variantId: Long,
    val quantity: Int,
    val unitCost: Long,
)

@Serializable
data class AdminVariantSearchPageDto(
    val items: List<AdminVariantSearchItemDto> = emptyList(),
)

@Serializable
data class AdminVariantSearchItemDto(
    val id: Long,
    val productId: Long,
    val productName: String,
    val sku: String,
    val size: String,
    val color: String,
    val price: Long,
    val stock: Int,
)
