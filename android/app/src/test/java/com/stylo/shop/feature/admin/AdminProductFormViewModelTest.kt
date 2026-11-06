package com.stylo.shop.feature.admin

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.ImageUpload
import com.stylo.shop.feature.admin.model.MAX_IMAGE_BYTES
import com.stylo.shop.feature.admin.ui.products.AdminProductFormViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminProductFormViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun editHandle(productId: Long): SavedStateHandle =
        SavedStateHandle(mapOf("productId" to productId))

    @Test
    fun editLoadPopulatesFormFromDetail() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            productDetailResult = {
                ApiResult.Success(
                    sampleDetail(
                        id = 5,
                        name = "Áo len",
                        variants = listOf(sampleVariant(id = 10)),
                        images = listOf(sampleImage(id = 3, position = 0)),
                    ),
                )
            }
        }
        val viewModel = AdminProductFormViewModel(repository, editHandle(5))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.loading)
        assertNull(state.loadError)
        assertEquals(5L, state.productId)
        assertEquals("Áo len", state.name)
        assertEquals(1L, state.categoryId)
        assertEquals(2L, state.brandId)
        assertEquals(1, state.variants.size)
        assertEquals(1, state.images.size)
    }

    @Test
    fun saveWithoutNameBlocksRequest() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminProductFormViewModel(repository, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onCategoryChange(1)
        viewModel.onBrandChange(2)
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, repository.createProductCalls)
        assertEquals(AdminFieldError.REQUIRED, viewModel.uiState.value.nameError)
    }

    @Test
    fun createProductEnablesVariantAndImageSteps() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            createProductResult = { input ->
                ApiResult.Success(
                    sampleDetail(
                        id = 42,
                        name = input.name,
                        status = input.status,
                        categoryId = input.categoryId,
                        brandId = input.brandId,
                        variants = emptyList(),
                        images = emptyList(),
                    ),
                )
            }
        }
        val viewModel = AdminProductFormViewModel(repository, SavedStateHandle())
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onNameChange("Áo mới")
        viewModel.onCategoryChange(1)
        viewModel.onBrandChange(2)
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val created = viewModel.uiState.value
        assertEquals(42L, created.productId)
        assertTrue(created.created)

        viewModel.openCreateVariant()
        viewModel.onVariantSkuChange("SKU-1")
        viewModel.onVariantSizeChange("M")
        viewModel.onVariantColorChange("Đen")
        viewModel.onVariantPriceChange("199000")
        viewModel.saveVariant()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val withVariant = viewModel.uiState.value
        assertEquals(1, withVariant.variants.size)
        assertNull(withVariant.variantEditor)

        viewModel.uploadImage(ImageUpload(ByteArray(16), "a.jpg", "image/jpeg"))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.images.size)
    }

    @Test
    fun duplicateSkuKeepsEditorOpenWithInlineError() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            createVariantResult = { _, _ -> ApiResult.Failure(skuTaken) }
        }
        val viewModel = AdminProductFormViewModel(repository, editHandle(5))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.openCreateVariant()
        viewModel.onVariantSkuChange("SKU-1")
        viewModel.onVariantSizeChange("M")
        viewModel.onVariantColorChange("Đen")
        viewModel.onVariantPriceChange("199000")
        viewModel.saveVariant()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val editor = viewModel.uiState.value.variantEditor
        assertNotNull(editor)
        assertEquals(AdminFieldError.SKU_TAKEN, editor?.skuError)
        assertEquals(false, editor?.saving)
    }

    @Test
    fun hideExistingProductSendsInactiveStatus() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminProductFormViewModel(repository, editHandle(5))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStatusChange(CatalogStatus.INACTIVE)
        viewModel.save()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val (id, input) = repository.updateProductCalls.single()
        assertEquals(5L, id)
        assertEquals(CatalogStatus.INACTIVE, input.status)
    }

    @Test
    fun deleteInUseKeepsProductAndClosesDialog() = runTest {
        val repository = FakeAdminCatalogRepository().apply {
            deleteProductResult = ApiResult.Failure(inUse)
        }
        val viewModel = AdminProductFormViewModel(repository, editHandle(5))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.requestDeleteProduct()
        viewModel.confirmDeleteProduct()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.deleteProductCalls)
        assertEquals(false, viewModel.uiState.value.deleted)
        assertEquals(false, viewModel.uiState.value.showDeleteProduct)
    }

    @Test
    fun failedUploadIsKeptForRetry() = runTest {
        var failFirst = true
        val repository = FakeAdminCatalogRepository().apply {
            productDetailResult = {
                ApiResult.Success(sampleDetail(id = 5, variants = emptyList(), images = emptyList()))
            }
            uploadImageResult = { _, _ ->
                if (failFirst) {
                    failFirst = false
                    ApiResult.Failure(ApiError(ApiErrorKind.NETWORK))
                } else {
                    ApiResult.Success(
                        AdminProductImage(
                            id = 9,
                            url = "/uploads/products/retry.jpg",
                            position = 1,
                        ),
                    )
                }
            }
        }
        val viewModel = AdminProductFormViewModel(repository, editHandle(5))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.uploadImage(ImageUpload(ByteArray(16), "a.jpg", "image/jpeg"))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.failedImage)
        assertTrue(viewModel.uiState.value.images.isEmpty())

        viewModel.retryImage()
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.failedImage)
        assertEquals(1, viewModel.uiState.value.images.size)
        assertEquals(2, repository.uploadImageCalls)
    }

    @Test
    fun oversizedImageIsRejectedBeforeNetworkCall() = runTest {
        val repository = FakeAdminCatalogRepository()
        val viewModel = AdminProductFormViewModel(repository, editHandle(5))
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.effects.test {
            viewModel.uploadImage(
                ImageUpload(
                    bytes = ByteArray(MAX_IMAGE_BYTES + 1),
                    fileName = "big.jpg",
                    mimeType = "image/jpeg",
                ),
            )
            assertEquals(
                UiEffect.ShowMessageRes(R.string.error_file_too_large),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(0, repository.uploadImageCalls)
    }
}
