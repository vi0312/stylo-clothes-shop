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
    private val badge: CartBadgeStore,
) : CartRepository {

    private suspend fun request(block: suspend () -> CartDto): ApiResult<Cart> =
        apiCall { block().toCart() }.also { result ->
            if (result is ApiResult.Success) badge.update(result.data.itemCount)
        }

    override suspend fun getCart(): ApiResult<Cart> = request { api.getCart() }

    override suspend fun addItem(variantId: Long, quantity: Int): ApiResult<Cart> = request {
        api.addItem(AddCartItemRequest(variantId = variantId, quantity = quantity))
    }

    override suspend fun updateQuantity(itemId: Long, quantity: Int): ApiResult<Cart> = request {
        api.updateItem(itemId, UpdateCartItemRequest(quantity = quantity))
    }

    override suspend fun removeItem(itemId: Long): ApiResult<Cart> = request {
        api.removeItem(itemId)
    }

    override suspend fun clear(): ApiResult<Cart> = request { api.clearCart() }
}

fun CartDto.toCart(): Cart = Cart(
    id = id,
    items = items.map { it.toCartItem() },
    itemCount = itemCount,
    subtotal = subtotal,
    shippingFee = shippingFee,
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
