package com.stylo.shop.feature.checkout

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.cart.FakeCartRepository
import com.stylo.shop.feature.cart.testCart
import com.stylo.shop.feature.cart.testCartItem
import com.stylo.shop.feature.checkout.model.CHECKOUT_NOTE_MAX
import com.stylo.shop.feature.checkout.model.PlacedOrder
import com.stylo.shop.feature.checkout.model.StockShortfall
import com.stylo.shop.feature.checkout.model.SubmitError
import com.stylo.shop.feature.checkout.ui.CheckoutEffect
import com.stylo.shop.feature.checkout.ui.CheckoutViewModel
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.orders.testOrder
import com.stylo.shop.feature.profile.FakeProfileRepository
import com.stylo.shop.feature.profile.testDefaultAddress
import com.stylo.shop.feature.profile.testSecondAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun viewModel(
        checkout: FakeCheckoutRepository = FakeCheckoutRepository(),
        cart: FakeCartRepository = FakeCartRepository(),
        profile: FakeProfileRepository = FakeProfileRepository(
            listResult = ApiResult.Success(listOf(testSecondAddress, testDefaultAddress)),
        ),
    ): CheckoutViewModel = CheckoutViewModel(checkout, cart, profile, SavedStateHandle())

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `load preselects the default address next to the cart subtotal`() = runTest {
        val viewModel = viewModel()
        advance()

        val state = viewModel.uiState.value
        assertEquals(testDefaultAddress.id, state.selectedAddressId)
        assertEquals(testCart().subtotal, state.cart?.subtotal)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `the total adds the server shipping fee to the subtotal`() = runTest {
        val below = viewModel(cart = FakeCartRepository(getResult = ApiResult.Success(testCart())))
        advance()
        assertEquals(testCart().subtotal + 30_000L, below.uiState.value.total)

        val free = viewModel(
            cart = FakeCartRepository(
                getResult = ApiResult.Success(testCart(listOf(testCartItem(quantity = 3)))),
            ),
        )
        advance()
        assertEquals(597_000L, free.uiState.value.total)
    }

    @Test
    fun `submit sends one draft and emits the placed order`() = runTest {
        val checkout = FakeCheckoutRepository(
            result = ApiResult.Success(PlacedOrder(order = testOrder(id = 5), paymentUrl = null)),
        )
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.submit()
        advance()

        val draft = checkout.drafts.single()
        assertEquals(testDefaultAddress.id, draft.addressId)
        assertEquals(OrderPaymentMethod.COD, draft.paymentMethod)
        assertNull(draft.note)
        assertEquals(CheckoutEffect.Placed(orderId = 5, paymentUrl = null), viewModel.effects.first())
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `an online order carries the payment url into the effect`() = runTest {
        val url = "http://127.0.0.1:8787/api/v1/payments/mock/STY1"
        val checkout = FakeCheckoutRepository(
            result = ApiResult.Success(
                PlacedOrder(
                    order = testOrder(id = 9, paymentMethod = OrderPaymentMethod.ONLINE),
                    paymentUrl = url,
                ),
            ),
        )
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.selectPaymentMethod(OrderPaymentMethod.ONLINE)
        viewModel.submit()
        advance()

        assertEquals(OrderPaymentMethod.ONLINE, checkout.drafts.single().paymentMethod)
        assertEquals(CheckoutEffect.Placed(orderId = 9, paymentUrl = url), viewModel.effects.first())
    }

    @Test
    fun `a retry after a failure reuses the client order id`() = runTest {
        val checkout = FakeCheckoutRepository(
            result = ApiResult.Failure(ApiError(kind = ApiErrorKind.NETWORK)),
        )
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.submit()
        advance()
        assertTrue(viewModel.uiState.value.submitError is SubmitError.Other)

        checkout.result = ApiResult.Success(PlacedOrder(order = testOrder(), paymentUrl = null))
        viewModel.submit()
        advance()

        assertEquals(2, checkout.drafts.size)
        assertEquals(checkout.drafts[0].clientOrderId, checkout.drafts[1].clientOrderId)
        assertNull(viewModel.uiState.value.submitError)
    }

    @Test
    fun `a second tap while submitting places no second order`() = runTest {
        val checkout = FakeCheckoutRepository()
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.submit()
        viewModel.submit()
        advance()

        assertEquals(1, checkout.drafts.size)
    }

    @Test
    fun `out of stock details become per line messages`() = runTest {
        val details = buildJsonArray {
            add(
                buildJsonObject {
                    put("sku", "AT-DEN-M")
                    put("requested", 3)
                    put("available", 2)
                },
            )
        }
        val checkout = FakeCheckoutRepository(
            result = ApiResult.Failure(
                ApiError(kind = ApiErrorKind.HTTP, code = "OUT_OF_STOCK", httpStatus = 409, details = details),
            ),
        )
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.submit()
        advance()

        assertEquals(
            SubmitError.Stock(listOf(StockShortfall(sku = "AT-DEN-M", requested = 3, available = 2))),
            viewModel.uiState.value.submitError,
        )
    }

    @Test
    fun `an emptied cart is reported as its own failure`() = runTest {
        val checkout = FakeCheckoutRepository(
            result = ApiResult.Failure(
                ApiError(kind = ApiErrorKind.HTTP, code = "CART_EMPTY", httpStatus = 409),
            ),
        )
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.submit()
        advance()

        assertEquals(SubmitError.CartEmpty, viewModel.uiState.value.submitError)
    }

    @Test
    fun `submit without an address places nothing`() = runTest {
        val checkout = FakeCheckoutRepository()
        val viewModel = viewModel(
            checkout = checkout,
            profile = FakeProfileRepository(listResult = ApiResult.Success(emptyList())),
        )
        advance()

        viewModel.submit()
        advance()

        assertTrue(checkout.drafts.isEmpty())
        assertFalse(viewModel.uiState.value.canSubmit)
    }

    @Test
    fun `the note is capped and a blank one is sent as null`() = runTest {
        val checkout = FakeCheckoutRepository()
        val viewModel = viewModel(checkout = checkout)
        advance()

        viewModel.updateNote("x".repeat(CHECKOUT_NOTE_MAX + 20))
        assertEquals(CHECKOUT_NOTE_MAX, viewModel.uiState.value.note.length)

        viewModel.updateNote("   ")
        viewModel.selectAddress(testSecondAddress.id)
        viewModel.submit()
        advance()

        val draft = checkout.drafts.single()
        assertEquals(testSecondAddress.id, draft.addressId)
        assertNull(draft.note)
    }
}
