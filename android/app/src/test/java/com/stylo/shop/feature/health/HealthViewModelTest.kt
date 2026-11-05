package com.stylo.shop.feature.health

import app.cash.turbine.test
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.health.data.HealthDto
import com.stylo.shop.feature.health.data.HealthRepository
import com.stylo.shop.feature.health.ui.HealthViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `exposes content when the check succeeds`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = HealthViewModel(
            FakeHealthRepository(listOf(ApiResult.Success(HealthDto("ok")))),
        )

        viewModel.state.test {
            assertEquals(UiState.Loading, awaitItem().check)
            advanceUntilIdle()
            assertEquals(UiState.Content(HealthDto("ok")), awaitItem().check)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `retry replaces an error with content`() = runTest(mainDispatcherRule.dispatcher) {
        val error = ApiError(ApiErrorKind.HTTP, code = "INTERNAL", httpStatus = 500)
        val repository = FakeHealthRepository(
            listOf(
                ApiResult.Failure(error),
                ApiResult.Success(HealthDto("ok")),
            ),
        )
        val viewModel = HealthViewModel(repository)

        advanceUntilIdle()
        assertEquals(UiState.Error(error), viewModel.state.value.check)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(UiState.Content(HealthDto("ok")), viewModel.state.value.check)
        assertEquals(2, repository.calls)
    }

    private class FakeHealthRepository(
        private val results: List<ApiResult<HealthDto>>,
    ) : HealthRepository {

        private val pending = ArrayDeque(results)
        var calls = 0
            private set

        override suspend fun check(): ApiResult<HealthDto> {
            calls++
            return pending.removeFirst()
        }
    }
}
