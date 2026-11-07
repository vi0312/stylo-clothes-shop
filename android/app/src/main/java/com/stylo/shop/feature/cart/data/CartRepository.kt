package com.stylo.shop.feature.cart.data

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.cart.model.Cart
import com.stylo.shop.feature.cart.model.CartItem
import javax.inject.Inject

interface CartRepository {
    suspend fun getCart(): ApiResult<Cart>
    suspend fun addItem(variantId: Long, quantity: Int): ApiResult<Cart>
    suspend fun updateQuantity(itemId: Long, quantity: Int): ApiResult<Cart>
    suspend fun removeItem(itemId: Long): ApiResult<Cart>
    suspend fun clear(): ApiResult<Cart>
}

class DefaultCartRepository @Inject constructor(
    private val api: CartApi,
) : CartRepository {

    override suspend fun getCart(): ApiResult<Cart> = apiCall {
        api.getCart().toCart()
    }

    override suspend fun addItem(variantId: Long, quantity: Int): ApiResult<Cart> = apiCall {
        api.addItem(AddCartItemRequest(variantId = variantId, quantity = quantity)).toCart()
    }

    override suspend fun updateQuantity(itemId: Long, quantity: Int): ApiResult<Cart> = apiCall {
        api.updateItem(itemId, UpdateCartItemRequest(quantity = quantity)).toCart()
    }

    override suspend fun removeItem(itemId: Long): ApiResult<Cart> = apiCall {
        api.removeItem(itemId).toCart()
    }

    override suspend fun clear(): ApiResult<Cart> = apiCall {
        api.clearCart().toCart()
    }
}

fun CartDto.toCart(): Cart = Cart(
    id = id,
    items = items.map { it.toCartItem() },
    itemCount = itemCount,
    subtotal = subtotal,
)

private fun CartItemDto.toCartItem(): CartItem = CartItem(
    id = id,
    variantId = variantId,
    sku = sku,
    productId = productId,
    productName = productName,
    imageUrl = imageUrl,
    size = size,
    color = color,
    unitPrice = unitPrice,
    quantity = quantity,
    lineTotal = lineTotal,
    availableStock = availableStock,
    available = available,
)
