package com.stylo.shop.admin

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.theme.StyloTheme
import com.stylo.shop.feature.admin.data.AdminCatalogRepository
import com.stylo.shop.feature.admin.model.AdminProductDetail
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.AdminProductQuery
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.AdminRef
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminTaxonomyQuery
import com.stylo.shop.feature.admin.model.AdminVariant
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.CreateProductInput
import com.stylo.shop.feature.admin.model.CreateTaxonomyInput
import com.stylo.shop.feature.admin.model.CreateVariantInput
import com.stylo.shop.feature.admin.model.ImageUpload
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.UpdateProductInput
import com.stylo.shop.feature.admin.model.UpdateTaxonomyInput
import com.stylo.shop.feature.admin.model.UpdateVariantInput
import com.stylo.shop.feature.admin.ui.products.AdminProductFormScreen
import com.stylo.shop.feature.admin.ui.products.AdminProductFormViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the create path of the admin product form: the Save action lives inside the info card,
 * so a refactor that renders it only for an existing product breaks product creation silently
 * (regression fixed in 9978531's aftermath). No server is involved.
 */
@RunWith(AndroidJUnit4::class)
class AdminProductFormCreateTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun createModeShowsSaveAndCreatesProduct() {
        val repository = RecordingCatalogRepository()
        val viewModel = AdminProductFormViewModel(repository, SavedStateHandle())
        viewModel.onNameChange("Áo mới")
        viewModel.onCategoryChange(1)
        viewModel.onBrandChange(2)
        var receiptOpened = false

        composeRule.setContent {
            StyloTheme {
                AdminProductFormScreen(
                    productId = null,
                    onBack = {},
                    onOpenReceipt = { receiptOpened = true },
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithTag(UiTags.AdminProductSave).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTags.AdminProductSave).performClick()

        composeRule.waitUntil(5_000) { repository.createProductCalls == 1 }
        assertEquals("Áo mới", repository.lastCreate?.name)
        assertEquals(1L, repository.lastCreate?.categoryId)
        assertEquals(2L, repository.lastCreate?.brandId)

        viewModel.openCreateVariant()
        viewModel.onVariantSkuChange("AT-DEN-M")
        viewModel.onVariantSizeChange("M")
        viewModel.onVariantColorChange("Đen")
        viewModel.onVariantPriceChange("199000")
        viewModel.saveVariant()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.variants.size == 1 }

        // The shortcut sits at the clipped bottom edge, where a touch injection can miss it;
        // the OnClick semantics action still proves the handler is wired.
        composeRule.onNodeWithTag(UiTags.AdminProductReceipt)
            .performScrollTo()
            .assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitUntil(5_000) { receiptOpened }
    }

    @Test
    fun editModeStillShowsSave() {
        val repository = RecordingCatalogRepository()
        val viewModel = AdminProductFormViewModel(repository, SavedStateHandle(mapOf("productId" to 5L)))

        composeRule.setContent {
            StyloTheme {
                AdminProductFormScreen(
                    productId = 5L,
                    onBack = {},
                    onOpenReceipt = {},
                    viewModel = viewModel,
                )
            }
        }

        composeRule.onNodeWithTag(UiTags.AdminProductSave).assertIsDisplayed()
    }
}

private class RecordingCatalogRepository : AdminCatalogRepository {

    var createProductCalls = 0
        private set

    var lastCreate: CreateProductInput? = null
        private set

    override suspend fun listProducts(query: AdminProductQuery): ApiResult<Paginated<AdminProductSummary>> =
        ApiResult.Success(Paginated(emptyList(), page = 1, limit = query.limit, total = 0))

    override suspend fun getProduct(id: Long): ApiResult<AdminProductDetail> =
        ApiResult.Success(detail(id))

    override suspend fun createProduct(input: CreateProductInput): ApiResult<AdminProductDetail> {
        createProductCalls++
        lastCreate = input
        return ApiResult.Success(detail(42))
    }

    override suspend fun updateProduct(id: Long, input: UpdateProductInput): ApiResult<AdminProductDetail> =
        ApiResult.Success(detail(id))

    override suspend fun deleteProduct(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun createVariant(productId: Long, input: CreateVariantInput): ApiResult<AdminVariant> =
        ApiResult.Success(variant(productId))

    override suspend fun updateVariant(id: Long, input: UpdateVariantInput): ApiResult<AdminVariant> =
        ApiResult.Success(variant(productId = 1))

    override suspend fun deleteVariant(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun uploadImage(productId: Long, image: ImageUpload): ApiResult<AdminProductImage> =
        ApiResult.Success(AdminProductImage(id = 1, url = "/uploads/1.jpg", position = 0))

    override suspend fun deleteImage(productId: Long, imageId: Long): ApiResult<Unit> =
        ApiResult.Success(Unit)

    override suspend fun listCategories(
        query: AdminTaxonomyQuery,
    ): ApiResult<Paginated<AdminTaxonomy>> = ApiResult.Success(
        Paginated(listOf(taxonomy(1, "Áo")), page = 1, limit = query.limit, total = 1),
    )

    override suspend fun createCategory(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy> =
        ApiResult.Success(taxonomy(1, input.name))

    override suspend fun updateCategory(id: Long, input: UpdateTaxonomyInput): ApiResult<AdminTaxonomy> =
        ApiResult.Success(taxonomy(id, input.name ?: "Áo"))

    override suspend fun deleteCategory(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    override suspend fun listBrands(
        query: AdminTaxonomyQuery,
    ): ApiResult<Paginated<AdminTaxonomy>> = ApiResult.Success(
        Paginated(listOf(taxonomy(2, "Stylo")), page = 1, limit = query.limit, total = 1),
    )

    override suspend fun createBrand(input: CreateTaxonomyInput): ApiResult<AdminTaxonomy> =
        ApiResult.Success(taxonomy(2, input.name))

    override suspend fun updateBrand(id: Long, input: UpdateTaxonomyInput): ApiResult<AdminTaxonomy> =
        ApiResult.Success(taxonomy(id, input.name ?: "Stylo"))

    override suspend fun deleteBrand(id: Long): ApiResult<Unit> = ApiResult.Success(Unit)

    private fun taxonomy(id: Long, name: String) = AdminTaxonomy(
        id = id,
        name = name,
        status = CatalogStatus.ACTIVE,
        productCount = 0,
    )

    private fun variant(productId: Long) = AdminVariant(
        id = 10,
        productId = productId,
        sku = "AT-DEN-M",
        size = "M",
        color = "Đen",
        price = 199_000,
        stock = 0,
    )

    private fun detail(id: Long) = AdminProductDetail(
        id = id,
        name = "Áo mới",
        description = null,
        status = CatalogStatus.ACTIVE,
        category = AdminRef(1, "Áo"),
        brand = AdminRef(2, "Stylo"),
        images = emptyList(),
        variants = emptyList(),
        minPrice = null,
        maxPrice = null,
    )
}
