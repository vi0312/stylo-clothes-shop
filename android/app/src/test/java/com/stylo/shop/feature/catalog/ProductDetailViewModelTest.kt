package com.stylo.shop.feature.catalog

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.catalog.model.BrandItem
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.ProductDetail
import com.stylo.shop.feature.catalog.model.ProductVariant
import com.stylo.shop.feature.catalog.ui.ProductDetailEffect
import com.stylo.shop.feature.catalog.ui.ProductDetailViewModel
import com.stylo.shop.feature.cart.FakeCartRepository
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
        val viewModel = ProductDetailViewModel(repo, FakeCartRepository(), savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productState is UiState.Content)
        assertEquals("Đen", state.selectedColor)
        assertEquals("S", state.selectedSize)
        assertEquals(1L, state.selectedVariant?.id)
        assertTrue(state.isCtaEnabled)
        assertFalse(state.isOutOfStock)
        assertEquals(10, state.stock)
        assertEquals(199_000L, state.currentPrice)
    }

    @Test
    fun clickingOutOfStockSizeDoesNotChangeSelection() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, FakeCartRepository(), savedState)
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
        val viewModel = ProductDetailViewModel(repo, FakeCartRepository(), savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isCtaEnabled)
        assertTrue(state.isOutOfStock)
        assertFalse(state.availableSizes[0].isAvailable)
        assertEquals(0, state.stock)
    }

    @Test
    fun selectingColorWithLowStockShowsLowStockWarning() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, FakeCartRepository(), savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        // Trắng has size L with stock = 2 (low stock)
        viewModel.onSelectColor("Trắng")
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Trắng", state.selectedColor)
        assertEquals("L", state.selectedSize)
        assertTrue(state.isLowStock)
        assertEquals(2, state.stock)
        assertTrue(state.isCtaEnabled)
    }

    @Test
    fun errorStateWhenProductNotFound() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Failure(ApiError(ApiErrorKind.HTTP, code = "NOT_FOUND")) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 999L))
        val viewModel = ProductDetailViewModel(repo, FakeCartRepository(), savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.productState is UiState.Error)
        val error = (state.productState as UiState.Error).error
        assertEquals("NOT_FOUND", error.code)
    }

    @Test
    fun addToCartSendsTheSelectedVariantAndEmitsAdded() = runTest {
        val cartRepository = FakeCartRepository()
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, cartRepository, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.addToCart()
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(ProductDetailEffect.AddedToCart, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(1L to 1), cartRepository.added)
        assertFalse(viewModel.uiState.value.isAddingToCart)
    }

    @Test
    fun `addToCart sends the chosen quantity and resets it`() = runTest {
        val cartRepository = FakeCartRepository()
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val viewModel = ProductDetailViewModel(
            repo,
            cartRepository,
            SavedStateHandle(mapOf("productId" to 1L)),
        )
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onIncreaseQuantity()
        viewModel.onIncreaseQuantity()
        assertEquals(3, viewModel.uiState.value.quantity)

        viewModel.effects.test {
            viewModel.addToCart()
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(ProductDetailEffect.AddedToCart, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(listOf(1L to 3), cartRepository.added)
        assertEquals(1, viewModel.uiState.value.quantity)
    }

    @Test
    fun `quantity stops at stock and clamps to the newly selected variant`() = runTest {
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val viewModel = ProductDetailViewModel(
            repo,
            FakeCartRepository(),
            SavedStateHandle(mapOf("productId" to 1L)),
        )
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        repeat(20) { viewModel.onIncreaseQuantity() }
        assertEquals(10, viewModel.uiState.value.quantity)

        // Trắng/L only has 2 units, so the held 10 is clamped instead of carried over.
        viewModel.onSelectColor("Trắng")
        assertEquals(2, viewModel.uiState.value.quantity)

        repeat(5) { viewModel.onDecreaseQuantity() }
        assertEquals(1, viewModel.uiState.value.quantity)
    }

    @Test
    fun addToCartFailureEmitsTheError() = runTest {
        val error = ApiError(ApiErrorKind.HTTP, code = "OUT_OF_STOCK", httpStatus = 409)
        val cartRepository = FakeCartRepository().apply {
            addResult = ApiResult.Failure(error)
        }
        val repo = FakeCatalogRepository().apply {
            getDetailResult = { ApiResult.Success(sampleProductDetail()) }
        }
        val savedState = SavedStateHandle(mapOf("productId" to 1L))
        val viewModel = ProductDetailViewModel(repo, cartRepository, savedState)
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.addToCart()
            dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(ProductDetailEffect.AddFailed(error), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(viewModel.uiState.value.isAddingToCart)
    }
}
