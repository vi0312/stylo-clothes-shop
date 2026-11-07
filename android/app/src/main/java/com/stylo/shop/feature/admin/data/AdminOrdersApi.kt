package com.stylo.shop.feature.admin.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminOrdersApi {

    @GET("admin/orders")
    suspend fun listOrders(
        @Query("status") status: String? = null,
        @Query("q") q: String? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): AdminOrderPageDto

    @GET("admin/orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): AdminOrderDetailDto

    @PATCH("admin/orders/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: Long,
        @Body body: UpdateAdminOrderStatusRequest,
    ): AdminOrderDetailDto
}

@Serializable
data class UpdateAdminOrderStatusRequest(val status: String)

@Serializable
data class AdminOrderPageDto(
    val items: List<AdminOrderSummaryDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
)

@Serializable
data class AdminOrderSummaryDto(
    val id: Long,
    val code: String,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val total: Long,
    val itemCount: Int,
    val customerName: String,
    val recipientName: String,
    val recipientPhone: String,
    val createdAt: String,
)

@Serializable
data class AdminOrderDetailDto(
    val id: Long,
    val code: String,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val customer: AdminOrderCustomerDto,
    val subtotal: Long,
    val shippingFee: Long,
    val total: Long,
    val note: String? = null,
    val recipientName: String,
    val recipientPhone: String,
    val shippingAddress: String,
    val items: List<AdminOrderItemDto> = emptyList(),
    val payments: List<AdminPaymentDto> = emptyList(),
    val paidAt: String? = null,
    val createdAt: String,
)

@Serializable
data class AdminOrderCustomerDto(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String? = null,
)

@Serializable
data class AdminOrderItemDto(
    val id: Long,
    val productId: Long,
    val variantId: Long,
    val productName: String,
    val sku: String,
    val size: String,
    val color: String,
    val unitPrice: Long,
    val unitCost: Long? = null,
    val quantity: Int,
    val lineTotal: Long,
)

@Serializable
data class AdminPaymentDto(
    val id: Long,
    val txnRef: String,
    val provider: String,
    val status: String,
    val amount: Long,
    val failureReason: String? = null,
    val createdAt: String,
)
