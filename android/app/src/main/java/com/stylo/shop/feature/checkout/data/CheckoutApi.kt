package com.stylo.shop.feature.checkout.data

import com.stylo.shop.feature.orders.data.OrderDto
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

interface CheckoutApi {

    @POST("orders")
    suspend fun createOrder(@Body body: CreateOrderRequest): CreateOrderResponseDto
}

/** Strict body: the server owns prices, totals and the cart contents. */
@Serializable
data class CreateOrderRequest(
    val clientOrderId: String,
    val addressId: Long,
    val paymentMethod: String,
    val note: String? = null,
)

@Serializable
data class CreateOrderResponseDto(
    val order: com.stylo.shop.feature.orders.data.OrderDto,
    val payment: CheckoutPaymentDto? = null,
)

@Serializable
data class CheckoutPaymentDto(
    val provider: String = "",
    val paymentUrl: String? = null,
)
