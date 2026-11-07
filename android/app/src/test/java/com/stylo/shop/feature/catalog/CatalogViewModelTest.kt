package com.stylo.shop.feature.catalog

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.catalog.data.CatalogRepository
import com.stylo.shop.feature.catalog.model.BrandItem
import com.stylo.shop.feature.catalog.model.CatalogFilters
import com.stylo.shop.feature.catalog.model.CatalogQuery
import com.stylo.shop.feature.catalog.model.CatalogSortOption
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.PaginatedProducts
import com.stylo.shop.feature.catalog.model.PriceRange
import com.stylo.shop.feature.catalog.model.ProductCard
import com.stylo.shop.feature.catalog.model.ProductDetail
import com.stylo.shop.feature.catalog.ui.CatalogViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FakeCatalogRepository : CatalogRepository {
    var listProductsResult: (CatalogQuery) -> ApiResult<PaginatedProducts> = {
        ApiResult.Success(
            PaginatedProducts(
                items = listOf(sampleProductCard(id = 1, name = "Áo")),
                page = 1,
                limit = 20,
                total = 1,
            ),
        )
    }

    var getFiltersResult: ApiResult<CatalogFilters> = ApiResult.Success(
        CatalogFilters(
            categories = listOf(CategoryItem(id = 1, name = "Áo")),
            brands = listOf(BrandItem(id = 2, name = "Stylo")),
            colors = listOf("Đen"),
            sizes = listOf("M"),
            priceRange = PriceRange(min = 100_000, max = 500_000),
        ),
    )

    var getDetailResult: (Long) -> ApiResult<ProductDetail> = { id ->
        ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = "NOT_FOUND"))
    }

    var lastQuery: CatalogQuery? = null
    val queries = mutableListOf<CatalogQuery>()

    override suspend fun listProducts(query: CatalogQuery): ApiResult<PaginatedProducts> {
        lastQuery = query
        queries += query
        return listProductsResult(query)
    }

    override suspend fun getProductDetail(id: Long): ApiResult<ProductDetail> = getDetailResult(id)

    override suspend fun getFilters(): ApiResult<CatalogFilters> = getFiltersResult
}

fun sampleProductCard(id: Long, name: String) = ProductCard(
    id = id,
    name = name,
    coverUrl = null,
    minPrice = 200_000,
    maxPrice = 250_000,
    category = CategoryItem(id = 1, name = "Áo"),
    brand = BrandItem(id = 2, name = "Stylo"),
)

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun initialLoadSetsContentState() = runTest {
        val repo = FakeCatalogRepository()
        val viewModel = CatalogViewModel(repo, SavedStateHandle())

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productsState is UiState.Content)
        val items = (state.productsState as UiState.Content).data
        assertEquals(1, items.size)
        assertEquals("Áo", items[0].name)
        assertTrue(state.filtersState is UiState.Content)
    }

    @Test
    fun initialLoadErrorSetsErrorState() = runTest {
        val repo = FakeCatalogRepository().apply {
            listProductsResult = { ApiResult.Failure(ApiError(ApiErrorKind.NETWORK)) }
        }
        val viewModel = CatalogViewModel(repo, SavedStateHandle())

        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productsState is UiState.Error)
        val error = (state.productsState as UiState.Error).error
        assertEquals(ApiErrorKind.NETWORK, error.kind)
    }

    @Test
    fun searchSubmitReloadsWithQuery() = runTest {
        val repo = FakeCatalogRepository()
        val savedState = SavedStateHandle()
        val viewModel = CatalogViewModel(repo, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("jeans")
        viewModel.onSearchSubmit()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("jeans", repo.lastQuery?.q)
        assertEquals("jeans", savedState[CatalogViewModel.KEY_QUERY])
    }

    @Test
    fun selectCategoryTabTogglesFilter() = runTest {
        val repo = FakeCatalogRepository()
        val viewModel = CatalogViewModel(repo, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSelectCategoryTab(1L)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1L, repo.lastQuery?.categoryId)
        assertEquals(1L, viewModel.uiState.value.activeCategoryId)

        // Select again toggles off
        viewModel.onSelectCategoryTab(1L)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        assertNull(repo.lastQuery?.categoryId)
        assertNull(viewModel.uiState.value.activeCategoryId)
    }

    @Test
    fun sortSelectionReloadsWithSort() = runTest {
        val repo = FakeCatalogRepository()
        val viewModel = CatalogViewModel(repo, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSortSelected(CatalogSortOption.PRICE_ASC)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("price_asc", repo.lastQuery?.sort)
        assertEquals(CatalogSortOption.PRICE_ASC, viewModel.uiState.value.activeSort)
    }

    @Test
    fun applyDraftFiltersCommitsToActiveAndReloads() = runTest {
        val repo = FakeCatalogRepository()
        val viewModel = CatalogViewModel(repo, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onOpenFilterSheet()
        viewModel.onDraftCategorySelected(1L)
        viewModel.onDraftBrandSelected(2L)
        viewModel.onDraftColorSelected("Đen")
        viewModel.onDraftSizeSelected("M")
        viewModel.onDraftPriceChanged(100_000, 300_000)

        viewModel.onApplyDraftFilters()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1L, state.activeCategoryId)
        assertEquals(2L, state.activeBrandId)
        assertEquals("Đen", state.activeColor)
        assertEquals("M", state.activeSize)
        assertEquals(100_000L, state.activeMinPrice)
        assertEquals(300_000L, state.activeMaxPrice)
        assertEquals(false, state.isFilterSheetOpen)
        assertEquals(5, state.activeFilterCount)
    }

    @Test
    fun clearAllFiltersResetsEverything() = runTest {
        val repo = FakeCatalogRepository()
        val viewModel = CatalogViewModel(repo, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onSearchQueryChanged("ao")
        viewModel.onSelectCategoryTab(1L)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onClearAllFilters()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.searchQuery)
        assertNull(state.activeCategoryId)
        assertNull(state.activeBrandId)
        assertEquals(CatalogSortOption.NEWEST, state.activeSort)
        assertEquals(0, state.activeFilterCount)
    }

    @Test
    fun loadMoreAppendsProducts() = runTest {
        val repo = FakeCatalogRepository().apply {
            listProductsResult = { query ->
                if (query.page == 1) {
                    ApiResult.Success(
                        PaginatedProducts(
                            items = listOf(sampleProductCard(id = 1, name = "Product 1")),
                            page = 1,
                            limit = 1,
                            total = 2,
                        ),
                    )
                } else {
                    ApiResult.Success(
                        PaginatedProducts(
                            items = listOf(sampleProductCard(id = 2, name = "Product 2")),
                            page = 2,
                            limit = 1,
                            total = 2,
                        ),
                    )
                }
            }
        }
        val viewModel = CatalogViewModel(repo, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, ((viewModel.uiState.value.productsState as UiState.Content).data).size)

        viewModel.onLoadMore()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val items = (viewModel.uiState.value.productsState as UiState.Content).data
        assertEquals(2, items.size)
        assertEquals("Product 1", items[0].name)
        assertEquals("Product 2", items[1].name)
    }
}
