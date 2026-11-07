package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.AdminReceiptSummary
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.RECEIPT_PAGE_SIZE
import com.stylo.shop.feature.admin.ui.inventory.AdminReceiptListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminReceiptListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun content(viewModel: AdminReceiptListViewModel): List<AdminReceiptSummary> =
        (viewModel.uiState.value.receiptsState as UiState.Content).data

    @Test
    fun `loads the newest receipts on the first page`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptListViewModel(repository)
        advance()

        val query = repository.queries.single()
        assertEquals(1, query.page)
        assertEquals(RECEIPT_PAGE_SIZE, query.limit)
        assertEquals("newest", query.sort)
        assertEquals(1, content(viewModel).size)
    }

    @Test
    fun `load more appends the next page and stops at the total`() = runTest {
        val repository = FakeAdminInventoryRepository(
            pageResults = listOf(
                ApiResult.Success(
                    Paginated(
                        items = listOf(testReceiptSummary(id = 1), testReceiptSummary(id = 2)),
                        page = 1,
                        limit = 2,
                        total = 3,
                    ),
                ),
                ApiResult.Success(
                    Paginated(
                        items = listOf(testReceiptSummary(id = 3)),
                        page = 2,
                        limit = 2,
                        total = 3,
                    ),
                ),
            ),
        )
        val viewModel = AdminReceiptListViewModel(repository)
        advance()

        assertTrue(viewModel.uiState.value.hasMore)
        viewModel.loadMore()
        advance()

        assertEquals(listOf(1L, 2L, 3L), content(viewModel).map { it.id })
        assertEquals(2, repository.queries.size)
        assertEquals(3, viewModel.uiState.value.total)
        assertTrue(!viewModel.uiState.value.hasMore)
    }

    @Test
    fun `a failed first page becomes an error state and retry reloads it`() = runTest {
        val repository = FakeAdminInventoryRepository(
            pageResults = listOf(
                ApiResult.Failure(
                    ApiError(kind = ApiErrorKind.NETWORK, developerMessage = "offline"),
                ),
                ApiResult.Success(
                    Paginated(
                        items = listOf(testReceiptSummary()),
                        page = 1,
                        limit = 20,
                        total = 1,
                    ),
                ),
            ),
        )
        val viewModel = AdminReceiptListViewModel(repository)
        advance()

        assertTrue(viewModel.uiState.value.receiptsState is UiState.Error)

        viewModel.onRetry()
        advance()

        assertEquals(1, content(viewModel).size)
    }

    @Test
    fun `refreshIfLoaded refetches page one without showing the skeleton`() = runTest {
        val repository = FakeAdminInventoryRepository()
        val viewModel = AdminReceiptListViewModel(repository)
        advance()

        viewModel.refreshIfLoaded()
        advance()

        assertEquals(2, repository.queries.size)
        assertTrue(viewModel.uiState.value.receiptsState is UiState.Content)
    }
}
