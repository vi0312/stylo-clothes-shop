package com.stylo.shop.feature.cart

import app.cash.turbine.test
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.cart.ui.CartViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CartViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `load exposes the cart totals`() = runTest {
        val item = testCartItem(quantity = 2)
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(
                testCart(
                    listOf(
                        item,
                        testCartItem(
                            id = 2,
                            sku = "QJ-XANH-M",
                            productName = "Quần jeans",
                            unitPrice = 300_000,
                            quantity = 1,
                        ),
                    ),
                ),
            ),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value.cartState
        assertTrue(state is UiState.Content)
        val cart = (state as UiState.Content).data
        assertEquals(2, cart.items.size)
        assertEquals(3, cart.itemCount)
        assertEquals(698_000, cart.subtotal)
    }

    @Test
    fun `load failure exposes the error and retry reloads`() = runTest {
        val repository = FakeCartRepository(
            getResult = ApiResult.Failure(ApiError(ApiErrorKind.NETWORK)),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.cartState is UiState.Error)

        repository.getResult = ApiResult.Success(testCart())
        viewModel.retry()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value.cartState
        assertTrue(state is UiState.Content)
        assertEquals(1, (state as UiState.Content).data.items.size)
    }

    @Test
    fun `increase updates optimistically then applies the server cart`() = runTest {
        val item = testCartItem(quantity = 2, availableStock = 5)
        val serverCart = testCart(listOf(item.copy(quantity = 3, lineTotal = 597_000)))
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
            updateResult = ApiResult.Success(serverCart),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.increase(item)

        // Before the request completes the optimistic cart already shows the new quantity.
        val optimistic = (viewModel.uiState.value.cartState as UiState.Content).data
        assertEquals(3, optimistic.items[0].quantity)
        assertEquals(597_000, optimistic.subtotal)
        assertEquals(item.id, viewModel.uiState.value.mutatingItemId)

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(item.id to 3), repository.updated)
        val settled = (viewModel.uiState.value.cartState as UiState.Content).data
        assertEquals(3, settled.items[0].quantity)
        assertNull(viewModel.uiState.value.mutatingItemId)
    }

    @Test
    fun `increase is ignored at available stock`() = runTest {
        val item = testCartItem(quantity = 5, availableStock = 5)
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.increase(item)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
        assertEquals(5, (viewModel.uiState.value.cartState as UiState.Content).data.items[0].quantity)
    }

    @Test
    fun `decrease is ignored at one`() = runTest {
        val item = testCartItem(quantity = 1, availableStock = 5)
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.decrease(item)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.updated.isEmpty())
    }

    @Test
    fun `update failure rolls the quantity back and reports the error`() = runTest {
        val item = testCartItem(quantity = 2, availableStock = 5)
        val error = ApiError(ApiErrorKind.HTTP, code = "OUT_OF_STOCK", httpStatus = 409)
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
            updateResult = ApiResult.Failure(error),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.increase(item)
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiEffect.ShowError(error), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val cart = (viewModel.uiState.value.cartState as UiState.Content).data
        assertEquals(2, cart.items[0].quantity)
        assertEquals(398_000, cart.subtotal)
        assertNull(viewModel.uiState.value.mutatingItemId)
    }

    @Test
    fun `remove drops the line and restores it when the server fails`() = runTest {
        val item = testCartItem()
        val error = ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500)
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
            removeResult = ApiResult.Failure(error),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.remove(item)

            val optimistic = (viewModel.uiState.value.cartState as UiState.Content).data
            assertTrue(optimistic.isEmpty)

            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiEffect.ShowError(error), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(item.id), repository.removed)
        assertEquals(1, (viewModel.uiState.value.cartState as UiState.Content).data.items.size)
    }

    @Test
    fun `remove success shows the empty cart`() = runTest {
        val item = testCartItem()
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.remove(item)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value.cartState as UiState.Content).data
        assertTrue(cart.isEmpty)
        assertNull(cart.id)
        assertEquals(0, cart.itemCount)
        assertEquals(0, cart.subtotal)
    }

    @Test
    fun `clear empties the cart and a failure restores it`() = runTest {
        val item = testCartItem()
        val error = ApiError(ApiErrorKind.NETWORK)
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart(listOf(item))),
            clearResult = ApiResult.Failure(error),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.clear()

            assertTrue((viewModel.uiState.value.cartState as UiState.Content).data.isEmpty)
            assertTrue(viewModel.uiState.value.isClearing)

            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(UiEffect.ShowError(error), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(1, repository.clearCalls)
        assertFalse(viewModel.uiState.value.isClearing)
        assertEquals(1, (viewModel.uiState.value.cartState as UiState.Content).data.items.size)
    }

    @Test
    fun `clear success keeps the empty cart`() = runTest {
        val repository = FakeCartRepository(
            getResult = ApiResult.Success(testCart()),
            clearResult = ApiResult.Success(testCart(items = emptyList())),
        )
        val viewModel = CartViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.clear()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val cart = (viewModel.uiState.value.cartState as UiState.Content).data
        assertTrue(cart.isEmpty)
        assertNull(cart.id)
    }
}
