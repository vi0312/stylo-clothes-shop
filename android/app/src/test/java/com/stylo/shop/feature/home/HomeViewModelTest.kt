package com.stylo.shop.feature.home

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.catalog.FakeCatalogRepository
import com.stylo.shop.feature.catalog.model.BrandItem
import com.stylo.shop.feature.catalog.model.CatalogFilters
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.PriceRange
import com.stylo.shop.feature.catalog.sampleProductCard
import com.stylo.shop.feature.home.ui.HomeViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun repository() = FakeCatalogRepository().apply {
        getFiltersResult = ApiResult.Success(
            CatalogFilters(
                categories = listOf(CategoryItem(id = 1, name = "Áo"), CategoryItem(id = 2, name = "Quần")),
                brands = listOf(BrandItem(id = 3, name = "Stylo")),
                colors = listOf("Đen"),
                sizes = listOf("M"),
                priceRange = PriceRange(min = 100_000, max = 500_000),
            ),
        )
    }

    @Test
    fun `loads the newest carousel and one block per showcase category`() = runTest {
        val repo = repository()
        val viewModel = HomeViewModel(repo)

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val carousel = state.carouselState as UiState.Content
        assertEquals(1, carousel.data.size)
        assertEquals(listOf(1L, 2L), state.blocks.map { it.category.id })
        state.blocks.forEach { block ->
            assertTrue("block ${block.category.name} loaded", block.productsState is UiState.Content)
        }
        val carouselQuery = repo.queries.single { it.limit == 8 }
        assertEquals("newest", carouselQuery.sort)
        assertEquals(null, carouselQuery.categoryId)
        val blockQueries = repo.queries.filter { it.limit == 4 }
        assertEquals(listOf(1L, 2L), blockQueries.map { it.categoryId })
    }

    @Test
    fun `a failing carousel leaves the blocks readable`() = runTest {
        val repo = repository().apply {
            listProductsResult = { query ->
                if (query.limit == 8) {
                    ApiResult.Failure(ApiError(ApiErrorKind.NETWORK))
                } else {
                    ApiResult.Success(
                        com.stylo.shop.feature.catalog.model.PaginatedProducts(
                            items = listOf(sampleProductCard(id = 5, name = "Quần jeans")),
                            page = 1,
                            limit = 4,
                            total = 1,
                        ),
                    )
                }
            }
        }
        val viewModel = HomeViewModel(repo)

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ApiErrorKind.NETWORK, (state.carouselState as UiState.Error).error.kind)
        assertTrue(state.categoriesState is UiState.Content)
        assertTrue(state.blocks.all { it.productsState is UiState.Content })
    }

    @Test
    fun `filters failure keeps the carousel and drops the blocks`() = runTest {
        val repo = repository().apply {
            getFiltersResult = ApiResult.Failure(ApiError(ApiErrorKind.HTTP, httpStatus = 500))
        }
        val viewModel = HomeViewModel(repo)

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.categoriesState is UiState.Error)
        assertTrue(state.carouselState is UiState.Content)
        assertTrue(state.blocks.isEmpty())
    }

    @Test
    fun `retrying one block does not reload the other`() = runTest {
        val repo = repository().apply {
            listProductsResult = { query ->
                if (query.categoryId == 1L && query.limit == 4) {
                    ApiResult.Failure(ApiError(ApiErrorKind.NETWORK))
                } else {
                    ApiResult.Success(
                        com.stylo.shop.feature.catalog.model.PaginatedProducts(
                            items = listOf(sampleProductCard(id = 9, name = "Áo thun")),
                            page = 1,
                            limit = 4,
                            total = 1,
                        ),
                    )
                }
            }
        }
        val viewModel = HomeViewModel(repo)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        val failed = viewModel.uiState.value.blocks.single { it.category.id == 1L }
        assertTrue(failed.productsState is UiState.Error)

        repo.listProductsResult = { query ->
            ApiResult.Success(
                com.stylo.shop.feature.catalog.model.PaginatedProducts(
                    items = listOf(sampleProductCard(id = 9, name = "Áo thun")),
                    page = 1,
                    limit = 4,
                    total = 1,
                ),
            )
        }
        val before = repo.queries.size
        viewModel.loadBlock(1)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val retried = viewModel.uiState.value.blocks.single { it.category.id == 1L }
        assertTrue(retried.productsState is UiState.Content)
        assertEquals(before + 1, repo.queries.size)
        assertTrue(viewModel.uiState.value.blocks.single { it.category.id == 2L }.productsState is UiState.Content)
    }
}
