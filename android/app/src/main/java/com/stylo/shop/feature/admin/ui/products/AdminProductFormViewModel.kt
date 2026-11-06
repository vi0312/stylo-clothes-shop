package com.stylo.shop.feature.admin.ui.products

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.feature.admin.data.AdminCatalogRepository
import com.stylo.shop.feature.admin.model.ADMIN_COLOR_MAX
import com.stylo.shop.feature.admin.model.ADMIN_DESCRIPTION_MAX
import com.stylo.shop.feature.admin.model.ADMIN_NAME_MAX
import com.stylo.shop.feature.admin.model.ADMIN_SIZE_MAX
import com.stylo.shop.feature.admin.model.ADMIN_SKU_MAX
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminProductDetail
import com.stylo.shop.feature.admin.model.AdminProductImage
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminTaxonomyQuery
import com.stylo.shop.feature.admin.model.AdminVariant
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.CreateProductInput
import com.stylo.shop.feature.admin.model.CreateVariantInput
import com.stylo.shop.feature.admin.model.ImageUpload
import com.stylo.shop.feature.admin.model.MAX_IMAGE_BYTES
import com.stylo.shop.feature.admin.model.MAX_PRODUCT_IMAGES
import com.stylo.shop.feature.admin.model.UpdateProductInput
import com.stylo.shop.feature.admin.model.UpdateVariantInput
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VariantEditorState(
    val variantId: Long? = null,
    val sku: String = "",
    val size: String = "",
    val color: String = "",
    val price: String = "",
    val skuError: AdminFieldError? = null,
    val sizeError: AdminFieldError? = null,
    val colorError: AdminFieldError? = null,
    val priceError: AdminFieldError? = null,
    val formError: ApiError? = null,
    val saving: Boolean = false,
)

data class AdminProductFormUiState(
    val loading: Boolean = true,
    val loadError: ApiError? = null,
    val productId: Long? = null,
    val name: String = "",
    val description: String = "",
    val categoryId: Long? = null,
    val brandId: Long? = null,
    val status: CatalogStatus = CatalogStatus.ACTIVE,
    val categories: List<AdminTaxonomy> = emptyList(),
    val brands: List<AdminTaxonomy> = emptyList(),
    val images: List<AdminProductImage> = emptyList(),
    val variants: List<AdminVariant> = emptyList(),
    val nameError: AdminFieldError? = null,
    val descriptionError: AdminFieldError? = null,
    val categoryError: AdminFieldError? = null,
    val brandError: AdminFieldError? = null,
    val saving: Boolean = false,
    val formError: ApiError? = null,
    val created: Boolean = false,
    val imageUploading: Boolean = false,
    val failedImage: ImageUpload? = null,
    val variantEditor: VariantEditorState? = null,
    val confirmDeleteVariant: AdminVariant? = null,
    val isDeletingVariant: Boolean = false,
    val confirmDeleteImage: AdminProductImage? = null,
    val isDeletingImage: Boolean = false,
    val showDeleteProduct: Boolean = false,
    val isDeletingProduct: Boolean = false,
    val deleted: Boolean = false,
)

@HiltViewModel
class AdminProductFormViewModel @Inject constructor(
    private val repository: AdminCatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeProductId: Long? = savedStateHandle["productId"]

    private val _uiState = MutableStateFlow(AdminProductFormUiState())
    val uiState: StateFlow<AdminProductFormUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loadError = null) }

            val categoriesResult = repository.listCategories(AdminTaxonomyQuery(limit = TAXONOMY_LIMIT))
            val brandsResult = repository.listBrands(AdminTaxonomyQuery(limit = TAXONOMY_LIMIT))
            val categories = (categoriesResult as? ApiResult.Success)?.data?.items.orEmpty()
            val brands = (brandsResult as? ApiResult.Success)?.data?.items.orEmpty()

            val detailResult = routeProductId?.let { repository.getProduct(it) }

            when {
                detailResult is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = detailResult.error,
                            categories = categories,
                            brands = brands,
                        )
                    }
                }
                routeProductId == null && categoriesResult is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = categoriesResult.error,
                            categories = categories,
                            brands = brands,
                        )
                    }
                }
                routeProductId == null && brandsResult is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            loadError = brandsResult.error,
                            categories = categories,
                            brands = brands,
                        )
                    }
                }
                detailResult is ApiResult.Success -> {
                    _uiState.update { current ->
                        current.applyDetail(detailResult.data, categories, brands)
                    }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            categories = categories,
                            brands = brands,
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, nameError = null, formError = null) }
    }

    fun onDescriptionChange(value: String) {
        _uiState.update {
            it.copy(description = value, descriptionError = null, formError = null)
        }
    }

    fun onCategoryChange(categoryId: Long) {
        _uiState.update { it.copy(categoryId = categoryId, categoryError = null) }
    }

    fun onBrandChange(brandId: Long) {
        _uiState.update { it.copy(brandId = brandId, brandError = null) }
    }

    fun onStatusChange(status: CatalogStatus) {
        _uiState.update { it.copy(status = status) }
    }

    fun save() {
        val snapshot = _uiState.value
        if (snapshot.saving) return

        val name = snapshot.name.trim()
        val description = snapshot.description.trim()
        val nameError = when {
            name.isEmpty() -> AdminFieldError.REQUIRED
            name.length > ADMIN_NAME_MAX -> AdminFieldError.TOO_LONG
            else -> null
        }
        val descriptionError = if (description.length > ADMIN_DESCRIPTION_MAX) {
            AdminFieldError.TOO_LONG
        } else {
            null
        }
        val categoryError = if (snapshot.categoryId == null) AdminFieldError.REQUIRED else null
        val brandError = if (snapshot.brandId == null) AdminFieldError.REQUIRED else null

        if (nameError != null || descriptionError != null || categoryError != null || brandError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    descriptionError = descriptionError,
                    categoryError = categoryError,
                    brandError = brandError,
                    formError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                saving = true,
                nameError = null,
                descriptionError = null,
                categoryError = null,
                brandError = null,
                formError = null,
            )
        }

        viewModelScope.launch {
            val categoryId = snapshot.categoryId ?: return@launch
            val brandId = snapshot.brandId ?: return@launch
            val productId = snapshot.productId

            if (productId == null) {
                val result = repository.createProduct(
                    CreateProductInput(
                        name = name,
                        description = description.ifEmpty { null },
                        categoryId = categoryId,
                        brandId = brandId,
                        status = snapshot.status,
                    ),
                )
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.update { current ->
                            current.applyDetail(
                                detail = result.data,
                                categories = current.categories,
                                brands = current.brands,
                            ).copy(created = true)
                        }
                        emit(UiEffect.ShowMessageRes(R.string.admin_product_created))
                    }
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(saving = false, formError = result.error) }
                    }
                }
            } else {
                val result = repository.updateProduct(
                    productId,
                    UpdateProductInput(
                        name = name,
                        description = description,
                        categoryId = categoryId,
                        brandId = brandId,
                        status = snapshot.status,
                    ),
                )
                when (result) {
                    is ApiResult.Success -> {
                        _uiState.update { current ->
                            current.applyDetail(
                                detail = result.data,
                                categories = current.categories,
                                brands = current.brands,
                            ).copy(created = false)
                        }
                        emit(UiEffect.ShowMessageRes(R.string.admin_product_saved))
                    }
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(saving = false, formError = result.error) }
                    }
                }
            }
        }
    }

    fun openCreateVariant() {
        _uiState.update {
            it.copy(variantEditor = VariantEditorState())
        }
    }

    fun openEditVariant(variant: AdminVariant) {
        _uiState.update {
            it.copy(
                variantEditor = VariantEditorState(
                    variantId = variant.id,
                    sku = variant.sku,
                    size = variant.size,
                    color = variant.color,
                    price = variant.price.toString(),
                ),
            )
        }
    }

    fun dismissVariantEditor() {
        if (_uiState.value.variantEditor?.saving == true) return
        _uiState.update { it.copy(variantEditor = null) }
    }

    fun onVariantSkuChange(value: String) = updateVariantEditor {
        it.copy(sku = value, skuError = null, formError = null)
    }

    fun onVariantSizeChange(value: String) = updateVariantEditor {
        it.copy(size = value, sizeError = null, formError = null)
    }

    fun onVariantColorChange(value: String) = updateVariantEditor {
        it.copy(color = value, colorError = null, formError = null)
    }

    fun onVariantPriceChange(value: String) = updateVariantEditor {
        it.copy(price = value, priceError = null, formError = null)
    }

    fun saveVariant() {
        val state = _uiState.value
        val editor = state.variantEditor ?: return
        if (editor.saving) return
        val productId = state.productId ?: return

        val sku = editor.sku.trim()
        val size = editor.size.trim()
        val color = editor.color.trim()
        val price = editor.price.trim().toLongOrNull()

        val skuError = when {
            sku.isEmpty() -> AdminFieldError.REQUIRED
            sku.length > ADMIN_SKU_MAX -> AdminFieldError.TOO_LONG
            else -> null
        }
        val sizeError = when {
            size.isEmpty() -> AdminFieldError.REQUIRED
            size.length > ADMIN_SIZE_MAX -> AdminFieldError.TOO_LONG
            else -> null
        }
        val colorError = when {
            color.isEmpty() -> AdminFieldError.REQUIRED
            color.length > ADMIN_COLOR_MAX -> AdminFieldError.TOO_LONG
            else -> null
        }
        val priceError = when {
            editor.price.isBlank() -> AdminFieldError.REQUIRED
            price == null -> AdminFieldError.INVALID_NUMBER
            price <= 0 -> AdminFieldError.INVALID_PRICE
            else -> null
        }

        if (skuError != null || sizeError != null || colorError != null || priceError != null) {
            _uiState.update {
                it.copy(
                    variantEditor = it.variantEditor?.copy(
                        skuError = skuError,
                        sizeError = sizeError,
                        colorError = colorError,
                        priceError = priceError,
                    ),
                )
            }
            return
        }

        updateVariantEditor { it.copy(saving = true, formError = null) }

        viewModelScope.launch {
            val result = if (editor.variantId == null) {
                repository.createVariant(
                    productId,
                    CreateVariantInput(sku = sku, size = size, color = color, price = price!!),
                )
            } else {
                repository.updateVariant(
                    editor.variantId,
                    UpdateVariantInput(sku = sku, size = size, color = color, price = price!!),
                )
            }

            when (result) {
                is ApiResult.Success -> {
                    _uiState.update { current ->
                        val nextVariants = (current.variants.filterNot { it.id == result.data.id } + result.data)
                            .sortedBy { it.id }
                        current.copy(variants = nextVariants, variantEditor = null)
                    }
                    emit(
                        UiEffect.ShowMessageRes(
                            if (editor.variantId == null) {
                                R.string.admin_variant_added
                            } else {
                                R.string.admin_variant_saved
                            },
                        ),
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.update { current ->
                        val nextEditor = when (result.error.code) {
                            "SKU_TAKEN" -> current.variantEditor?.copy(
                                saving = false,
                                skuError = AdminFieldError.SKU_TAKEN,
                            )
                            "VARIANT_EXISTS" -> current.variantEditor?.copy(
                                saving = false,
                                sizeError = AdminFieldError.VARIANT_EXISTS,
                                colorError = AdminFieldError.VARIANT_EXISTS,
                            )
                            else -> current.variantEditor?.copy(
                                saving = false,
                                formError = result.error,
                            )
                        }
                        current.copy(variantEditor = nextEditor)
                    }
                }
            }
        }
    }

    fun requestDeleteVariant(variant: AdminVariant) {
        _uiState.update { it.copy(confirmDeleteVariant = variant) }
    }

    fun dismissDeleteVariant() {
        if (_uiState.value.isDeletingVariant) return
        _uiState.update { it.copy(confirmDeleteVariant = null) }
    }

    fun confirmDeleteVariant() {
        val state = _uiState.value
        val target = state.confirmDeleteVariant ?: return
        if (state.isDeletingVariant) return

        _uiState.update { it.copy(isDeletingVariant = true) }
        viewModelScope.launch {
            when (val result = repository.deleteVariant(target.id)) {
                is ApiResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            variants = current.variants.filterNot { it.id == target.id },
                            confirmDeleteVariant = null,
                            isDeletingVariant = false,
                        )
                    }
                    emit(UiEffect.ShowMessageRes(R.string.admin_variant_deleted))
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(confirmDeleteVariant = null, isDeletingVariant = false)
                    }
                    if (result.error.code == "IN_USE") {
                        emit(UiEffect.ShowMessageRes(R.string.admin_variant_delete_in_use))
                    } else {
                        emit(UiEffect.ShowError(result.error))
                    }
                }
            }
        }
    }

    fun uploadImage(image: ImageUpload) {
        val state = _uiState.value
        val productId = state.productId
        if (productId == null) {
            emit(UiEffect.ShowMessageRes(R.string.admin_product_save_hint))
            return
        }
        if (state.imageUploading) return
        if (state.images.size >= MAX_PRODUCT_IMAGES) {
            emit(
                UiEffect.ShowMessageRes(
                    R.string.admin_image_limit,
                    listOf(MAX_PRODUCT_IMAGES),
                ),
            )
            return
        }
        if (image.bytes.size > MAX_IMAGE_BYTES) {
            emit(UiEffect.ShowMessageRes(R.string.error_file_too_large))
            return
        }

        _uiState.update { it.copy(imageUploading = true, failedImage = null) }
        viewModelScope.launch {
            when (val result = repository.uploadImage(productId, image)) {
                is ApiResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            images = (current.images + result.data).sortedBy { it.position },
                            imageUploading = false,
                            failedImage = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(imageUploading = false, failedImage = image) }
                    emit(UiEffect.ShowError(result.error))
                }
            }
        }
    }

    fun retryImage() {
        val image = _uiState.value.failedImage ?: return
        uploadImage(image)
    }

    fun dismissFailedImage() {
        _uiState.update { it.copy(failedImage = null) }
    }

    fun onImageReadFailed() {
        emit(UiEffect.ShowMessageRes(R.string.admin_image_upload_failed))
    }

    fun requestDeleteImage(image: AdminProductImage) {
        _uiState.update { it.copy(confirmDeleteImage = image) }
    }

    fun dismissDeleteImage() {
        if (_uiState.value.isDeletingImage) return
        _uiState.update { it.copy(confirmDeleteImage = null) }
    }

    fun confirmDeleteImage() {
        val state = _uiState.value
        val target = state.confirmDeleteImage ?: return
        val productId = state.productId ?: return
        if (state.isDeletingImage) return

        _uiState.update { it.copy(isDeletingImage = true) }
        viewModelScope.launch {
            when (val result = repository.deleteImage(productId, target.id)) {
                is ApiResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            images = current.images.filterNot { it.id == target.id },
                            confirmDeleteImage = null,
                            isDeletingImage = false,
                        )
                    }
                    emit(UiEffect.ShowMessageRes(R.string.admin_image_deleted))
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(confirmDeleteImage = null, isDeletingImage = false)
                    }
                    emit(UiEffect.ShowError(result.error))
                }
            }
        }
    }

    fun requestDeleteProduct() {
        _uiState.update { it.copy(showDeleteProduct = true) }
    }

    fun dismissDeleteProduct() {
        if (_uiState.value.isDeletingProduct) return
        _uiState.update { it.copy(showDeleteProduct = false) }
    }

    fun confirmDeleteProduct() {
        val state = _uiState.value
        val productId = state.productId ?: return
        if (state.isDeletingProduct) return

        _uiState.update { it.copy(isDeletingProduct = true) }
        viewModelScope.launch {
            when (val result = repository.deleteProduct(productId)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            showDeleteProduct = false,
                            isDeletingProduct = false,
                            deleted = true,
                        )
                    }
                    emit(UiEffect.ShowMessageRes(R.string.admin_product_deleted))
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(showDeleteProduct = false, isDeletingProduct = false)
                    }
                    if (result.error.code == "IN_USE") {
                        emit(UiEffect.ShowMessageRes(R.string.admin_product_delete_in_use))
                    } else {
                        emit(UiEffect.ShowError(result.error))
                    }
                }
            }
        }
    }

    private fun updateVariantEditor(transform: (VariantEditorState) -> VariantEditorState) {
        _uiState.update { current ->
            val editor = current.variantEditor ?: return@update current
            current.copy(variantEditor = transform(editor))
        }
    }

    private fun emit(effect: UiEffect) {
        _effects.trySend(effect)
    }

    private fun AdminProductFormUiState.applyDetail(
        detail: AdminProductDetail,
        categories: List<AdminTaxonomy>,
        brands: List<AdminTaxonomy>,
    ): AdminProductFormUiState = copy(
        loading = false,
        loadError = null,
        productId = detail.id,
        name = detail.name,
        description = detail.description.orEmpty(),
        categoryId = detail.category.id,
        brandId = detail.brand.id,
        status = detail.status,
        categories = categories,
        brands = brands,
        images = detail.images,
        variants = detail.variants,
        saving = false,
        formError = null,
        deleted = false,
    )

    private companion object {
        const val TAXONOMY_LIMIT = 100
    }
}
