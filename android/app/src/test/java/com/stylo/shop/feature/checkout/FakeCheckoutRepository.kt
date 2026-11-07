package com.stylo.shop.feature.checkout

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.checkout.data.CheckoutRepository
import com.stylo.shop.feature.checkout.model.CheckoutDraft
import com.stylo.shop.feature.checkout.model.PlacedOrder
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.testOrder

class FakeCheckoutRepository(
    var result: ApiResult<PlacedOrder> = ApiResult.Success(
        PlacedOrder(
            order = testOrder(paymentMethod = OrderPaymentMethod.ONLINE),
            paymentUrl = null,
        ),
    ),
) : CheckoutRepository {

    val drafts = mutableListOf<CheckoutDraft>()

    override suspend fun placeOrder(draft: CheckoutDraft): ApiResult<PlacedOrder> {
        drafts += draft
        return result
    }
}
