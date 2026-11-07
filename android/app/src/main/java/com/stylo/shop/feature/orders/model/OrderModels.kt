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

/**
 * Cancellation is allowed while the order has not left the shop and no money has landed yet;
 * the server enforces the same rule and answers `INVALID_ORDER_STATE`/`ORDER_ALREADY_PAID`.
 */
fun canCancelOrder(order: Order): Boolean =
    order.paidAt == null &&
        (order.status == OrderStatus.PENDING || order.status == OrderStatus.CONFIRMED)

data class OrderSummary(
    val id: Long,
    val code: String,
    val status: OrderStatus,
    val paymentMethod: OrderPaymentMethod,
    val paymentStatus: PaymentStatus,
    val total: Long,
    val itemCount: Int,
    val createdAt: String,
)

data class OrderPage(
    val items: List<OrderSummary>,
    val page: Int,
    val limit: Int,
    val total: Int,
) {
    val hasMore: Boolean
        get() = page * limit < total
}

data class OrdersQuery(
    val status: OrderStatus? = null,
    val page: Int = 1,
    val limit: Int = ORDER_PAGE_SIZE,
)

const val ORDER_PAGE_SIZE = 20
