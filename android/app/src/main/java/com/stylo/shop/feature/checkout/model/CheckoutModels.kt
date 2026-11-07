package com.stylo.shop.feature.checkout.model

import com.stylo.shop.core.network.ApiError
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.OrderPaymentMethod

const val CHECKOUT_NOTE_MAX = 255

data class CheckoutDraft(
    val clientOrderId: String,
    val addressId: Long,
    val paymentMethod: OrderPaymentMethod,
    val note: String?,
)

data class PlacedOrder(
    val order: Order,
    /** Set when the order still needs a browser round-trip (ONLINE); `null` for COD. */
    val paymentUrl: String?,
)

data class StockShortfall(
    val sku: String,
    val requested: Int,
    val available: Int,
)

data class UnavailableLine(
    val sku: String,
    /** `PRODUCT_INACTIVE` or `VARIANT_MISSING`, per the server's `ITEM_UNAVAILABLE` details. */
    val reason: String,
)

/** What the submit button has to say when checkout is rejected, kept finer than a snackbar. */
sealed interface SubmitError {
    data class Stock(val lines: List<StockShortfall>) : SubmitError
    data class Unavailable(val lines: List<UnavailableLine>) : SubmitError
    data object CartEmpty : SubmitError
    data class Other(val error: ApiError) : SubmitError
}
