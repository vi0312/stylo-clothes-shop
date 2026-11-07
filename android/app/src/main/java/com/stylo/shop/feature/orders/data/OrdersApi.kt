package com.stylo.shop.feature.orders.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OrdersApi {

    @GET("orders")
    suspend fun listOrders(
        @Query("status") status: String? = null,
        @Query("sort") sort: String = "newest",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): OrderPageDto

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): OrderDto

    @POST("orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") id: Long): OrderDto
}

@Serializable
data class OrderPageDto(
    val items: List<OrderSummaryDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
)

@Serializable
data class OrderSummaryDto(
    val id: Long,
    val code: String,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val total: Long,
    val itemCount: Int,
    val createdAt: String,
)

@Serializable
data class OrderDto(
    val id: Long,
    val code: String,
    val status: String,
    val paymentMethod: String,
    val paymentStatus: String,
    val subtotal: Long,
    val shippingFee: Long,
    val total: Long,
    val note: String? = null,
    val recipientName: String,
    val recipientPhone: String,
    val shippingAddress: String,
    val items: List<OrderItemDto> = emptyList(),
    val paidAt: String? = null,
    val createdAt: String,
    val updatedAt: String? = null,
)

@Serializable
data class OrderItemDto(
    val id: Long,
    val productId: Long,
    val variantId: Long,
    val productName: String,
    val sku: String,
    val size: String,
    val color: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
)
