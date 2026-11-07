package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.allowedOrderTransitions
import com.stylo.shop.feature.admin.ui.orders.AdminOrderDetailViewModel
import com.stylo.shop.feature.orders.model.OrderStatus
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminOrderDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun viewModel(repository: FakeAdminOrdersRepository): AdminOrderDetailViewModel =
        AdminOrderDetailViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("orderId" to 123L)),
        )

    @Test
    fun `loads the order named by the route`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = viewModel(repository)
        advance()

        assertEquals(listOf(123L), repository.requestedIds)
        assertEquals(testAdminOrderDetail(), (viewModel.uiState.value.orderState as UiState.Content).data)
    }

    @Test
    fun `the transition matrix mirrors the server table`() {
        val expected = mapOf(
            OrderStatus.PENDING to listOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED to listOf(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING to listOf(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED to emptyList(),
            OrderStatus.CANCELLED to emptyList(),
        )

        expected.forEach { (status, targets) ->
            assertEquals(targets, allowedOrderTransitions(status, paid = false))
        }
        // A paid order cannot be cancelled any more; the server answers ORDER_ALREADY_PAID.
        assertEquals(listOf(OrderStatus.CONFIRMED), allowedOrderTransitions(OrderStatus.PENDING, paid = true))
        assertEquals(listOf(OrderStatus.SHIPPING), allowedOrderTransitions(OrderStatus.CONFIRMED, paid = true))
        assertEquals(listOf(OrderStatus.DELIVERED), allowedOrderTransitions(OrderStatus.SHIPPING, paid = true))
    }

    @Test
    fun `a pending order offers confirm and cancel`() = runTest {
        val viewModel = viewModel(FakeAdminOrdersRepository())
        advance()

        assertEquals(
            listOf(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            viewModel.uiState.value.allowedTargets,
        )
    }

    @Test
    fun `a delivered order offers nothing`() = runTest {
        val repository = FakeAdminOrdersRepository(
            detail = ApiResult.Success(testAdminOrderDetail(status = OrderStatus.DELIVERED)),
        )
        val viewModel = viewModel(repository)
        advance()

        assertTrue(viewModel.uiState.value.allowedTargets.isEmpty())
    }

    @Test
    fun `a transition posts the target and swaps in the returned order`() = runTest {
        val repository = FakeAdminOrdersRepository(
            updateResult = ApiResult.Success(testAdminOrderDetail(status = OrderStatus.CONFIRMED)),
        )
        val viewModel = viewModel(repository)
        advance()

        val effect = async { viewModel.effects.first() }
        viewModel.transition(OrderStatus.CONFIRMED)
        advance()

        assertEquals(listOf(123L to OrderStatus.CONFIRMED), repository.updates)
        assertEquals(OrderStatus.CONFIRMED, viewModel.uiState.value.order?.status)
        assertTrue(effect.await() is UiEffect.ShowMessageRes)
    }

    @Test
    fun `an illegal target is never posted`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = viewModel(repository)
        advance()

        viewModel.transition(OrderStatus.DELIVERED)
        advance()

        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun `a rejected transition shows the server error and refetches`() = runTest {
        val repository = FakeAdminOrdersRepository(
            updateResult = ApiResult.Failure(
                ApiError(kind = ApiErrorKind.HTTP, code = "INVALID_ORDER_STATE", httpStatus = 409),
            ),
        )
        val viewModel = viewModel(repository)
        advance()

        val effect = async { viewModel.effects.first() }
        viewModel.transition(OrderStatus.CONFIRMED)
        advance()

        assertTrue(effect.await() is UiEffect.ShowError)
        // Initial load plus the refetch that reveals the current server state.
        assertEquals(2, repository.requestedIds.size)
        assertTrue(!viewModel.uiState.value.isUpdating)
    }

    @Test
    fun `a second tap while updating does not post twice`() = runTest {
        val repository = FakeAdminOrdersRepository()
        val viewModel = viewModel(repository)
        advance()

        viewModel.transition(OrderStatus.CONFIRMED)
        viewModel.transition(OrderStatus.CONFIRMED)
        advance()

        assertEquals(1, repository.updates.size)
    }
}
