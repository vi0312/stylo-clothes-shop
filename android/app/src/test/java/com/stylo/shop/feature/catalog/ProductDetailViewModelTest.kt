package com.stylo.shop.feature.catalog

import androidx.lifecycle.SavedStateHandle
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.catalog.model.BrandItem
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.ProductDetail
import com.stylo.shop.feature.catalog.model.ProductVariant
import com.stylo.shop.feature.catalog.ui.ProductDetailViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

fun sampleProductDetail(
    id: Long = 1,
    variants: List<ProductVariant> = listOf(
        ProductVariant(id = 1, sku = "S-BLK", size = "S", color = "Đen", price = 199_000, stock = 10),
        ProductVariant(id = 2, sku = "M-BLK", size = "M", color = "Đen", price = 199_000, stock = 0),
        ProductVariant(id = 3, sku = "L-WHT", size = "L", color = "Trắng", price = 209_000, stock = 2),
    ),
) = ProductDetail(
    id = id,
    name = "Áo thun basic",
    description = "Áo cotton mềm",
    category = CategoryItem(id = 1, name = "Áo"),
    brand = BrandItem(id = 1, name = "Stylo"),
    images = emptyList(),
    variants = variants,
    minPrice = 199_000,
    maxPrice = 209_000,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ProductDetailViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun loadDetailSucceedsAndAutoSelectsInStockVariant() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productState is UiState.Content)
        assertEquals("Đen", state.selectedColor)
        assertEquals("S", state.selectedSize)
        assertEquals(1L, state.selectedVariant?.id)
        assertTrue(state.isCtaEnabled)
        assertEquals("Thêm vào giỏ", state.ctaButtonText)
        assertEquals(199_000L, state.currentPrice)
    }

    @Test
    fun clickingOutOfStockSizeDoesNotChangeSelection() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        // Size M has stock = 0, so clicking it must be ignored per design spec
        viewModel.onSelectSize("M")
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("S", state.selectedSize)
        assertEquals(1L, state.selectedVariant?.id)
        assertTrue(state.isCtaEnabled)
    }

    @Test
    fun allVariantsOutOfStockDisablesCta() = runTest {
        val outOfStockProduct = sampleProductDetail(
            variants = listOf(
                ProductVariant(id = 1, sku = "S-BLK", size = "S", color = "Đen", price = 199_000, stock = 0),
            ),
        )
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(outOfStockProduct) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCtaEnabled)
        assertFalse(state.availableSizes[0].isAvailable)
        assertEquals("Hết hàng", state.ctaButtonText)
        assertEquals("Hết hàng", state.stockText)
    }

    @Test
    fun selectingColorWithLowStockShowsLowStockWarning() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        // Trắng has size L with stock = 2 (low stock)
        viewModel.onSelectColor("Trắng")
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Trắng", state.selectedColor)
        assertEquals("L", state.selectedSize)
        assertTrue(state.isLowStock)
        assertTrue(state.stockText.contains("Tồn thấp"))
        assertTrue(state.isCtaEnabled)
    }

    @Test
    fun errorStateWhenProductNotFound() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = "NOT_FOUND")) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 999L))
        val viewModel = ProductDetailViewModel(repo, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productState is UiState.Error)
        val error = (state.productState as UiState.Error).error
        assertEquals("NOT_FOUND", error.code)
    }
}
