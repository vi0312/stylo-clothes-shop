package com.stylo.shop.feature.orders.data

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Path

interface OrdersApi {

    @GET("orders/{id}")
    suspend fun getOrder(@Path("id") id: Long): OrderDto
}

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
