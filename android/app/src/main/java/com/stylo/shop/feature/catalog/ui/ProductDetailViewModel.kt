package com.stylo.shop.feature.catalog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.cart.data.CartRepository
import com.stylo.shop.feature.catalog.data.CatalogRepository
import com.stylo.shop.feature.catalog.model.ProductDetail
import com.stylo.shop.feature.catalog.model.ProductVariant
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

data class SizeItemState(
    val size: String,
    val isAvailable: Boolean,
    val stock: Int,
)

data class ProductDetailUiState(
    val productState: UiState<ProductDetail> = UiState.Loading,
    val selectedColor: String? = null,
    val selectedSize: String? = null,
    val selectedVariant: ProductVariant? = null,
    val availableColors: List<String> = emptyList(),
    val availableSizes: List<SizeItemState> = emptyList(),
    val stockText: String = "",
    val ctaButtonText: String = "Chọn kích cỡ",
    val isCtaEnabled: Boolean = false,
    val currentPrice: Long = 0L,
    val isLowStock: Boolean = false,
    val isAddingToCart: Boolean = false,
)

sealed interface ProductDetailEffect {
    data object AddedToCart : ProductDetailEffect
    data class AddFailed(val error: ApiError) : ProductDetailEffect
}

@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val cartRepository: CartRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val productId: Long = savedStateHandle.get<Long>("productId")
        ?: savedStateHandle.get<String>("productId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(ProductDetailUiState())
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<ProductDetailEffect>(Channel.BUFFERED)
    val effects: Flow<ProductDetailEffect> = _effects.receiveAsFlow()

    init {
        loadDetail()
    }

    fun addToCart() {
        val state = _uiState.value
        val variant = state.selectedVariant ?: return
        if (state.isAddingToCart) return

        viewModelScope.launch {
            _uiState.update { it.copy(isAddingToCart = true) }
            when (val result = cartRepository.addItem(variant.id, 1)) {
                is ApiResult.Success -> _effects.send(ProductDetailEffect.AddedToCart)
                is ApiResult.Failure -> _effects.send(ProductDetailEffect.AddFailed(result.error))
            }
            _uiState.update { it.copy(isAddingToCart = false) }
        }
    }

    fun loadDetail() {
        if (productId <= 0L) return
        viewModelScope.launch {
            _uiState.update { it.copy(productState = UiState.Loading) }
            when (val result = repository.getProductDetail(productId)) {
                is ApiResult.Success -> {
                    val product = result.data
                    val colors = product.variants.map { it.color }.distinct()
                    // Auto-select first color if available
                    val initialColor = colors.firstOrNull()
                    val sizes = computeSizes(product.variants, initialColor)
                    // Auto-select first in-stock size if any
                    val initialSize = sizes.firstOrNull { it.isAvailable }?.size
                    val initialVariant = findVariant(product.variants, initialColor, initialSize)

                    _uiState.update {
                        computeUiState(
                            product = product,
                            selectedColor = initialColor,
                            selectedSize = initialSize,
                            selectedVariant = initialVariant,
                            availableColors = colors,
                            availableSizes = sizes,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(productState = UiState.Error(result.error)) }
                }
            }
        }
    }

    fun onSelectColor(color: String) {
        val product = (_uiState.value.productState as? UiState.Content)?.data ?: return
        val newSizes = computeSizes(product.variants, color)

        // If currently selected size is not available in the new color, select first available size or null
        val currentSize = _uiState.value.selectedSize
        val newSize = if (currentSize != null && newSizes.any { it.size == currentSize && it.isAvailable }) {
            currentSize
        } else {
            newSizes.firstOrNull { it.isAvailable }?.size
        }

        val variant = findVariant(product.variants, color, newSize)

        _uiState.update {
            computeUiState(
                product = product,
                selectedColor = color,
                selectedSize = newSize,
                selectedVariant = variant,
                availableColors = it.availableColors,
                availableSizes = newSizes,
            )
        }
    }

    fun onSelectSize(size: String) {
        val product = (_uiState.value.productState as? UiState.Content)?.data ?: return
        val color = _uiState.value.selectedColor
        val sizeInfo = _uiState.value.availableSizes.find { it.size == size }
        if (sizeInfo == null || !sizeInfo.isAvailable) return

        val variant = findVariant(product.variants, color, size)

        _uiState.update {
            computeUiState(
                product = product,
                selectedColor = color,
                selectedSize = size,
                selectedVariant = variant,
                availableColors = it.availableColors,
                availableSizes = it.availableSizes,
            )
        }
    }

    fun onRetry() {
        loadDetail()
    }

    private fun computeSizes(variants: List<ProductVariant>, color: String?): List<SizeItemState> {
        val allSizes = variants.map { it.size }.distinct()
        return allSizes.map { size ->
            val matchingVariant = variants.find {
                (color == null || it.color == color) && it.size == size
            }
            val stock = matchingVariant?.stock ?: 0
            SizeItemState(
                size = size,
                isAvailable = stock > 0,
                stock = stock,
            )
        }
    }

    private fun findVariant(variants: List<ProductVariant>, color: String?, size: String?): ProductVariant? {
        if (color == null || size == null) return null
        return variants.find { it.color == color && it.size == size }
    }

    private fun computeUiState(
        product: ProductDetail,
        selectedColor: String?,
        selectedSize: String?,
        selectedVariant: ProductVariant?,
        availableColors: List<String>,
        availableSizes: List<SizeItemState>,
    ): ProductDetailUiState {
        val stock = selectedVariant?.stock ?: 0
        val isLowStock = selectedVariant != null && stock in 1..5
        val allSizesOutOfStock = availableSizes.isNotEmpty() && availableSizes.none { it.isAvailable }

        val stockText = when {
            allSizesOutOfStock || (selectedVariant != null && stock <= 0) -> "Hết hàng"
            selectedColor == null -> "Vui lòng chọn màu"
            selectedSize == null -> "Vui lòng chọn kích cỡ"
            selectedVariant == null -> "Không có sẵn"
            isLowStock -> "Còn $stock sản phẩm (Tồn thấp)"
            else -> "Còn $stock sản phẩm"
        }

        val ctaText = when {
            allSizesOutOfStock || (selectedVariant != null && stock <= 0) -> "Hết hàng"
            selectedColor == null -> "Chọn màu"
            selectedSize == null -> "Chọn kích cỡ"
            selectedVariant == null -> "Hết hàng"
            else -> "Thêm vào giỏ"
        }

        val isCtaEnabled = !allSizesOutOfStock && selectedVariant != null && stock > 0
        val price = selectedVariant?.price ?: product.minPrice ?: 0L

        return ProductDetailUiState(
            productState = UiState.Content(product),
            selectedColor = selectedColor,
            selectedSize = selectedSize,
            selectedVariant = selectedVariant,
            availableColors = availableColors,
            availableSizes = availableSizes,
            stockText = stockText,
            ctaButtonText = ctaText,
            isCtaEnabled = isCtaEnabled,
            currentPrice = price,
            isLowStock = isLowStock,
        )
    }
}
