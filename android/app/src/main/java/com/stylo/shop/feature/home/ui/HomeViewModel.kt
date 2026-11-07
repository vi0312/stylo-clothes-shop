package com.stylo.shop.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.catalog.data.CatalogRepository
import com.stylo.shop.feature.catalog.model.CatalogQuery
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.model.ProductCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeBlock(
    val category: CategoryItem,
    val productsState: UiState<List<ProductCard>> = UiState.Loading,
)

data class HomeUiState(
    val categoriesState: UiState<List<CategoryItem>> = UiState.Loading,
    val carouselState: UiState<List<ProductCard>> = UiState.Loading,
    val blocks: List<HomeBlock> = emptyList(),
)

/**
 * Home layout A (docs/ui.md): newest carousel plus one block per showcase category. Sections
 * load and fail independently so one broken request never blanks the page.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: CatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        loadCarousel()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _uiState.update { it.copy(categoriesState = UiState.Loading) }
            when (val result = repository.getFilters()) {
                is ApiResult.Success -> {
                    val categories = result.data.categories
                    _uiState.update { it.copy(categoriesState = UiState.Content(categories)) }
                    loadBlocks(categories)
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(categoriesState = UiState.Error(result.error)) }
            }
        }
    }

    fun loadCarousel() {
        viewModelScope.launch {
            _uiState.update { it.copy(carouselState = UiState.Loading) }
            val state = when (
                val result = repository.listProducts(
                    CatalogQuery(sort = "newest", page = 1, limit = CAROUSEL_LIMIT),
                )
            ) {
                is ApiResult.Success -> UiState.Content(result.data.items)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            _uiState.update { it.copy(carouselState = state) }
        }
    }

    fun loadBlock(categoryId: Long) {
        val category = _uiState.value.blocks
            .firstOrNull { it.category.id == categoryId }
            ?.category ?: return
        viewModelScope.launch {
            updateBlock(categoryId) { it.copy(productsState = UiState.Loading) }
            val state = when (
                val result = repository.listProducts(
                    CatalogQuery(categoryId = categoryId, sort = "newest", page = 1, limit = BLOCK_LIMIT),
                )
            ) {
                is ApiResult.Success -> UiState.Content(result.data.items)
                is ApiResult.Failure -> UiState.Error(result.error)
            }
            updateBlock(categoryId) { it.copy(productsState = state) }
        }
    }

    private fun loadBlocks(categories: List<CategoryItem>) {
        val showcase = SHOWCASE_CATEGORIES.mapNotNull { name ->
            categories.firstOrNull { it.name == name }
        }
        _uiState.update { it.copy(blocks = showcase.map { HomeBlock(it) }) }
        showcase.forEach { loadBlock(it.id) }
    }

    private fun updateBlock(categoryId: Long, transform: (HomeBlock) -> HomeBlock) {
        _uiState.update { state ->
            state.copy(
                blocks = state.blocks.map { block ->
                    if (block.category.id == categoryId) transform(block) else block
                },
            )
        }
    }

    private companion object {
        const val CAROUSEL_LIMIT = 8
        const val BLOCK_LIMIT = 4
        val SHOWCASE_CATEGORIES = listOf("Áo", "Quần")
    }
}
