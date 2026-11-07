package com.stylo.shop.feature.orders.model

enum class OrderPaymentMethod(val apiValue: String) {
    COD("COD"),
    ONLINE("ONLINE"),
    ;

    companion object {
        fun fromApi(value: String): OrderPaymentMethod =
            entries.firstOrNull { it.apiValue == value } ?: COD
    }
}

enum class OrderStatus(val apiValue: String) {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    SHIPPING("SHIPPING"),
    DELIVERED("DELIVERED"),
    CANCELLED("CANCELLED"),
    ;

    companion object {
        fun fromApi(value: String): OrderStatus =
            entries.firstOrNull { it.apiValue == value } ?: PENDING
    }
}

/** Derived by the server from `paid_at` plus the latest attempt; never a stored column. */
enum class PaymentStatus(val apiValue: String) {
    UNPAID("UNPAID"),
    PAID("PAID"),
    FAILED("FAILED"),
    ;

    companion object {
        fun fromApi(value: String): PaymentStatus =
            entries.firstOrNull { it.apiValue == value } ?: UNPAID
    }
}

data class OrderItem(
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

/** Prices and names are snapshots taken at checkout, not live catalog reads. */
data class Order(
    val id: Long,
    val code: String,
    val status: OrderStatus,
    val paymentMethod: OrderPaymentMethod,
    val paymentStatus: PaymentStatus,
    val subtotal: Long,
    val shippingFee: Long,
    val total: Long,
    val note: String?,
    val recipientName: String,
    val recipientPhone: String,
    val shippingAddress: String,
    val items: List<OrderItem>,
    val paidAt: String?,
    val createdAt: String,
)
