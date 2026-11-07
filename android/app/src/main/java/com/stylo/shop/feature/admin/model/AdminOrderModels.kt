package com.stylo.shop.feature.admin.model

import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import java.time.LocalDate
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.PaymentStatus

/** Payments are attempts with their own lifecycle, unrelated to the derived order payment status. */
enum class PaymentAttemptStatus(val apiValue: String) {
    PENDING("PENDING"),
    SUCCEEDED("SUCCEEDED"),
    FAILED("FAILED"),
    ;

    companion object {
        fun fromApi(value: String): PaymentAttemptStatus =
            entries.firstOrNull { it.apiValue == value } ?: PENDING
    }
}

enum class AdminOrderRange {
    ALL,
    TODAY,
    LAST_7,
    LAST_30,
}

data class AdminOrderSummary(
    val id: Long,
    val code: String,
    val status: OrderStatus,
    val paymentMethod: OrderPaymentMethod,
    val paymentStatus: PaymentStatus,
    val total: Long,
    val itemCount: Int,
    val customerName: String,
    val recipientName: String,
    val recipientPhone: String,
    val createdAt: String,
)

data class AdminOrderCustomer(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String?,
)

data class AdminOrderItem(
    val id: Long,
    val productId: Long,
    val variantId: Long,
    val productName: String,
    val sku: String,
    val size: String,
    val color: String,
    val unitPrice: Long,
    val unitCost: Long?,
    val quantity: Int,
    val lineTotal: Long,
)

data class AdminPayment(
    val id: Long,
    val txnRef: String,
    val provider: String,
    val status: PaymentAttemptStatus,
    val amount: Long,
    val failureReason: String?,
    val createdAt: String,
)

data class AdminOrderDetail(
    val id: Long,
    val code: String,
    val status: OrderStatus,
    val paymentMethod: OrderPaymentMethod,
    val paymentStatus: PaymentStatus,
    val customer: AdminOrderCustomer,
    val subtotal: Long,
    val shippingFee: Long,
    val total: Long,
    val note: String?,
    val recipientName: String,
    val recipientPhone: String,
    val shippingAddress: String,
    val items: List<AdminOrderItem>,
    val payments: List<AdminPayment>,
    val paidAt: String?,
    val createdAt: String,
)

data class AdminOrderQuery(
    val status: OrderStatus? = null,
    val q: String? = null,
    val from: String? = null,
    val to: String? = null,
    val sort: String = "newest",
    val page: Int = 1,
    val limit: Int = ORDER_PAGE_SIZE,
)

const val ORDER_PAGE_SIZE = 20

/** Calendar dates in Asia/Ho_Chi_Minh; the server treats them as whole business days. */
fun AdminOrderRange.window(today: LocalDate): Pair<String?, String?> = when (this) {
    AdminOrderRange.ALL -> null to null
    AdminOrderRange.TODAY -> today.toString() to today.toString()
    AdminOrderRange.LAST_7 -> today.minusDays(6).toString() to today.toString()
    AdminOrderRange.LAST_30 -> today.minusDays(29).toString() to today.toString()
}

/**
 * Mirrors the server's `ORDER_TRANSITIONS` table; the server stays the authority and answers
 * `INVALID_ORDER_STATE`/`ORDER_ALREADY_PAID` for anything else.
 */
fun allowedOrderTransitions(status: OrderStatus, paid: Boolean): List<OrderStatus> = when (status) {
    OrderStatus.PENDING -> if (paid) listOf(OrderStatus.CONFIRMED) else listOf(
        OrderStatus.CONFIRMED,
        OrderStatus.CANCELLED,
    )
    OrderStatus.CONFIRMED -> if (paid) listOf(OrderStatus.SHIPPING) else listOf(
        OrderStatus.SHIPPING,
        OrderStatus.CANCELLED,
    )
    OrderStatus.SHIPPING -> listOf(OrderStatus.DELIVERED)
    OrderStatus.DELIVERED, OrderStatus.CANCELLED -> emptyList()
}
