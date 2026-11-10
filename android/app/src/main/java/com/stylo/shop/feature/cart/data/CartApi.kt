package com.stylo.shop.feature.cart.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface CartApi {

    @GET("cart")
    suspend fun getCart(): CartDto

    @POST("cart/items")
    suspend fun addItem(@Body body: AddCartItemRequest): CartDto

    @PATCH("cart/items/{itemId}")
    suspend fun updateItem(
        @Path("itemId") itemId: Long,
        @Body body: UpdateCartItemRequest,
    ): CartDto

    @DELETE("cart/items/{itemId}")
    suspend fun removeItem(@Path("itemId") itemId: Long): CartDto

    @DELETE("cart")
    suspend fun clearCart(): CartDto
}

@Serializable
data class AddCartItemRequest(
    val variantId: Long,
    val quantity: Int,
)

@Serializable
data class UpdateCartItemRequest(
    val quantity: Int,
)

@Serializable
data class CartDto(
    val id: Long? = null,
    val items: List<CartItemDto> = emptyList(),
    val itemCount: Int = 0,
    val subtotal: Long = 0,
    val shippingFee: Long = 0,
)

@Serializable
data class CartItemDto(
    val id: Long,
    val variantId: Long,
    val sku: String,
    val productId: Long,
    val productName: String,
    val imageUrl: String? = null,
    val size: String,
    val color: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
    val availableStock: Int,
    val available: Boolean,
)
