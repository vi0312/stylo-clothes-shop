package com.stylo.shop.feature.cart

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.cart.data.CartRepository
import com.stylo.shop.feature.cart.model.Cart
import com.stylo.shop.feature.cart.model.CartItem

fun testCartItem(
    id: Long = 1,
    variantId: Long = 44,
    sku: String = "AT-DEN-M",
    productId: Long = 9,
    productName: String = "Áo thun cotton",
    imageUrl: String? = null,
    size: String = "M",
    color: String = "Đen",
    unitPrice: Long = 199_000,
    quantity: Int = 2,
    availableStock: Int = 5,
    available: Boolean = true,
): CartItem = CartItem(
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
    lineTotal = unitPrice * quantity,
    availableStock = availableStock,
    available = available,
)

fun testCart(items: List<CartItem> = listOf(testCartItem())): Cart {
    val subtotal = items.filter { it.available }.sumOf { it.lineTotal }
    return Cart(
        id = if (items.isEmpty()) null else 7,
        items = items,
        itemCount = items.sumOf { it.quantity },
        subtotal = subtotal,
        // Mirrors the server: flat 30,000 under the 500,000 free-shipping threshold.
        shippingFee = if (items.any { it.available } && subtotal < 500_000) 30_000 else 0,
    )
}

class FakeCartRepository(
    var getResult: ApiResult<Cart> = ApiResult.Success(testCart()),
    var addResult: ApiResult<Cart> = ApiResult.Success(testCart()),
    var updateResult: ApiResult<Cart> = ApiResult.Success(testCart()),
    var removeResult: ApiResult<Cart> = ApiResult.Success(testCart(items = emptyList())),
    var clearResult: ApiResult<Cart> = ApiResult.Success(testCart(items = emptyList())),
) : CartRepository {

    val added = mutableListOf<Pair<Long, Int>>()
    val updated = mutableListOf<Pair<Long, Int>>()
    val removed = mutableListOf<Long>()
    var clearCalls = 0

    override suspend fun getCart(): ApiResult<Cart> = getResult

    override suspend fun addItem(variantId: Long, quantity: Int): ApiResult<Cart> {
        added += variantId to quantity
        return addResult
    }

    override suspend fun updateQuantity(itemId: Long, quantity: Int): ApiResult<Cart> {
        updated += itemId to quantity
        return updateResult
    }

    override suspend fun removeItem(itemId: Long): ApiResult<Cart> {
        removed += itemId
        return removeResult
    }

    override suspend fun clear(): ApiResult<Cart> {
        clearCalls += 1
        return clearResult
    }
}
