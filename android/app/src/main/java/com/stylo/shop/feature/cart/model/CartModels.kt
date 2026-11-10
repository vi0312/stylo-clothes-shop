package com.stylo.shop.feature.cart.model

data class CartItem(
    val id: Long,
    val variantId: Long,
    val sku: String,
    val productId: Long,
    val productName: String,
    val imageUrl: String?,
    val size: String,
    val color: String,
    val unitPrice: Long,
    val quantity: Int,
    val lineTotal: Long,
    val availableStock: Int,
    val available: Boolean,
)

data class Cart(
    val id: Long?,
    val items: List<CartItem>,
    val itemCount: Int,
    val subtotal: Long,
    /** Server-computed preview for this subtotal; the order recomputes it at placement. */
    val shippingFee: Long,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val total: Long get() = subtotal + shippingFee
}

/**
 * Applies a quantity optimistically with the same total rules as the server: `itemCount`
 * counts every line, `subtotal` counts only available ones.
 */
fun Cart.withItemQuantity(itemId: Long, quantity: Int): Cart {
    val items = items.map { item ->
        if (item.id == itemId) {
            item.copy(quantity = quantity, lineTotal = item.unitPrice * quantity)
        } else {
            item
        }
    }
    return copy(
        items = items,
        itemCount = items.sumOf { it.quantity },
        subtotal = items.filter { it.available }.sumOf { it.lineTotal },
    )
}

fun Cart.withoutItem(itemId: Long): Cart {
    val items = items.filterNot { it.id == itemId }
    return copy(
        items = items,
        itemCount = items.sumOf { it.quantity },
        subtotal = items.filter { it.available }.sumOf { it.lineTotal },
    )
}
