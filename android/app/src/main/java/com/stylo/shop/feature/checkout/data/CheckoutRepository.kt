package com.stylo.shop.feature.checkout.data

import com.stylo.shop.BuildConfig
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.checkout.model.CheckoutDraft
import com.stylo.shop.feature.checkout.model.PlacedOrder
import com.stylo.shop.feature.orders.data.toOrder
import javax.inject.Inject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

interface CheckoutRepository {
    suspend fun placeOrder(draft: CheckoutDraft): ApiResult<PlacedOrder>
}

class DefaultCheckoutRepository @Inject constructor(
    private val api: CheckoutApi,
) : CheckoutRepository {

    override suspend fun placeOrder(draft: CheckoutDraft): ApiResult<PlacedOrder> = apiCall {
        val response = api.createOrder(
            CreateOrderRequest(
                clientOrderId = draft.clientOrderId,
                addressId = draft.addressId,
                paymentMethod = draft.paymentMethod.apiValue,
                note = draft.note,
            ),
        )
        PlacedOrder(
            order = response.order.toOrder(),
            paymentUrl = devicePaymentUrl(
                paymentUrl = response.payment?.paymentUrl,
                provider = response.payment?.provider,
                apiBaseUrl = BuildConfig.API_BASE_URL,
            ),
        )
    }
}

/**
 * The mock page is served by this API, but the server builds its URL from `PUBLIC_BASE_URL`,
 * which may name a host the device cannot reach. Re-anchor MOCK urls on the origin the app
 * talks to; a real gateway url (VNPay) must stay exactly as issued.
 */
internal fun devicePaymentUrl(paymentUrl: String?, provider: String?, apiBaseUrl: String): String? {
    if (paymentUrl == null) return null
    if (provider != "MOCK") return paymentUrl
    val parsed = paymentUrl.toHttpUrlOrNull() ?: return paymentUrl
    val query = parsed.encodedQuery?.let { "?$it" }.orEmpty()
    return ApiClient.absoluteUrl(apiBaseUrl, parsed.encodedPath + query)
}
