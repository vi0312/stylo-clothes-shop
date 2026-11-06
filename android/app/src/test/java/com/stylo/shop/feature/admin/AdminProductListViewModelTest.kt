package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.ui.products.AdminProductListViewModel
import com.stylo.shop.feature.admin.ui.products.AdminStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminProductListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadPublishesProductsAndTaxonomies() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminProductListViewModel(repository)

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productsState is UiState.Content)
        assertEquals(1, (state.productsState as UiState.Content).data.size)
        assertEquals(1, state.categories.size)
        assertEquals(1, state.brands.size)
        assertEquals(1, state.total)
    }

    @Test
    fun statusFilterSendsStatusAndResetsPage() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminProductListViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusFilterSelected(AdminStatusFilter.ACTIVE)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val query = repository.productQueries.last()
        assertEquals(CatalogStatus.ACTIVE, query.status)
        assertEquals(1, query.page)
        assertEquals(20, query.limit)
    }

    @Test
    fun lowStockFilterRequestsWidePageAndFiltersTotalStock() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            productsResult = { query ->
                ApiResult.Success(
                    Paginated(
                        items = listOf(
                            sampleSummary(id = 1, totalStock = 3),
                            sampleSummary(id = 2, totalStock = 30),
                        ),
                        page = 1,
                        limit = query.limit,
                        total = 2,
                    ),
                )
            }
        }
        val viewModel = AdminProductListViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusFilterSelected(AdminStatusFilter.LOW_STOCK)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(100, repository.productQueries.last().limit)
        val state = viewModel.uiState.value
        val items = (state.productsState as UiState.Content).data
        assertEquals(listOf(1L), items.map { it.id })
        assertEquals(1, state.total)
    }

    @Test
    fun searchSubmitTrimsQuery() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminProductListViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("  áo  ")
        viewModel.onSearchSubmit()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("áo", repository.productQueries.last().q)
    }

    @Test
    fun loadMoreAppendsNextPage() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            productsResult = { query ->
                val page = if (query.page == 1) {
                    Paginated(listOf(sampleSummary(id = 1)), 1, query.limit, total = 45)
                } else {
                    Paginated(listOf(sampleSummary(id = 2)), 2, query.limit, total = 45)
                }
                ApiResult.Success(page)
            }
        }
        val viewModel = AdminProductListViewModel(repository)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoadMore()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val items = (viewModel.uiState.value.productsState as UiState.Content).data
        assertEquals(listOf(1L, 2L), items.map { it.id })
        assertEquals(2, viewModel.uiState.value.page)
    }
}
