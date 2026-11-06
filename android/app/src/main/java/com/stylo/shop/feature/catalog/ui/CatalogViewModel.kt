package com.stylo.shop.feature.catalog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.catalog.data.CatalogRepository
import com.stylo.shop.feature.catalog.model.CatalogFilters
import com.stylo.shop.feature.catalog.model.CatalogQuery
import com.stylo.shop.feature.catalog.model.CatalogSortOption
import com.stylo.shop.feature.catalog.model.ProductCard
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val productsState: UiState<List<ProductCard>> = UiState.Loading,
    val filtersState: UiState<CatalogFilters> = UiState.Loading,
    val searchQuery: String = "",
    val activeCategoryId: Long? = null,
    val activeBrandId: Long? = null,
    val activeColor: String? = null,
    val activeSize: String? = null,
    val activeMinPrice: Long? = null,
    val activeMaxPrice: Long? = null,
    val activeSort: CatalogSortOption = CatalogSortOption.NEWEST,
    val isFilterSheetOpen: Boolean = false,
    val draftCategoryId: Long? = null,
    val draftBrandId: Long? = null,
    val draftColor: String? = null,
    val draftSize: String? = null,
    val draftMinPrice: Long? = null,
    val draftMaxPrice: Long? = null,
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
) {
    val totalPages: Int
        get() = if (limit > 0) ((total + limit - 1) / limit).coerceAtLeast(1) else 1

    val hasMore: Boolean
        get() = page < totalPages

    val activeFilterCount: Int
        get() = listOfNotNull(
            activeBrandId,
            activeColor,
            activeSize,
            activeMinPrice,
            activeMaxPrice,
        ).size
}

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val repository: CatalogRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(restoreInitialState())
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        loadFilters()
        loadProducts(page = 1)
    }

    private fun restoreInitialState(): CatalogUiState {
        val q: String? = savedStateHandle[KEY_QUERY]
        val categoryId: Long? = savedStateHandle[KEY_CATEGORY_ID]
        val sortId: String? = savedStateHandle[KEY_SORT]
        return CatalogUiState(
            searchQuery = q.orEmpty(),
            activeCategoryId = categoryId,
            activeSort = sortId?.let { CatalogSortOption.fromId(it) } ?: CatalogSortOption.NEWEST,
        )
    }

    fun loadFilters() {
        viewModelScope.launch {
            _uiState.update { it.copy(filtersState = UiState.Loading) }
            when (val result = repository.getFilters()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(filtersState = UiState.Content(result.data)) }
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(filtersState = UiState.Error(result.error)) }
                }
            }
        }
    }

    fun loadProducts(page: Int) {
        viewModelScope.launch {
            val currentState = _uiState.value
            val isInitial = page == 1

            if (isInitial) {
                _uiState.update { it.copy(productsState = UiState.Loading, page = 1) }
            } else {
                _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
            }

            val query = CatalogQuery(
                q = currentState.searchQuery.takeIf { it.isNotBlank() },
                categoryId = currentState.activeCategoryId,
                brandId = currentState.activeBrandId,
                color = currentState.activeColor,
                size = currentState.activeSize,
                minPrice = currentState.activeMinPrice,
                maxPrice = currentState.activeMaxPrice,
                sort = currentState.activeSort.id,
                page = page,
                limit = currentState.limit,
            )

            when (val result = repository.listProducts(query)) {
                is ApiResult.Success -> {
                    val paginated = result.data
                    _uiState.update { prev ->
                        val combinedItems = if (isInitial) {
                            paginated.items
                        } else {
                            val currentItems = (prev.productsState as? UiState.Content)?.data.orEmpty()
                            currentItems + paginated.items
                        }
                        prev.copy(
                            productsState = UiState.Content(combinedItems),
                            page = paginated.page,
                            limit = paginated.limit,
                            total = paginated.total,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { prev ->
                        if (isInitial) {
                            prev.copy(productsState = UiState.Error(result.error), isLoadingMore = false)
                        } else {
                            prev.copy(isLoadingMore = false, loadMoreError = result.error)
                        }
                    }
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        savedStateHandle[KEY_QUERY] = query
    }

    fun onSearchSubmit() {
        loadProducts(page = 1)
    }

    fun onClearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        savedStateHandle[KEY_QUERY] = ""
        loadProducts(page = 1)
    }

    fun onSelectCategoryTab(categoryId: Long?) {
        val newCategoryId = if (_uiState.value.activeCategoryId == categoryId) null else categoryId
        _uiState.update { it.copy(activeCategoryId = newCategoryId) }
        savedStateHandle[KEY_CATEGORY_ID] = newCategoryId
        loadProducts(page = 1)
    }

    fun onSortSelected(sort: CatalogSortOption) {
        _uiState.update { it.copy(activeSort = sort) }
        savedStateHandle[KEY_SORT] = sort.id
        loadProducts(page = 1)
    }

    fun onOpenFilterSheet() {
        _uiState.update { current ->
            current.copy(
                isFilterSheetOpen = true,
                draftCategoryId = current.activeCategoryId,
                draftBrandId = current.activeBrandId,
                draftColor = current.activeColor,
                draftSize = current.activeSize,
                draftMinPrice = current.activeMinPrice,
                draftMaxPrice = current.activeMaxPrice,
            )
        }
    }

    fun onDismissFilterSheet() {
        _uiState.update { it.copy(isFilterSheetOpen = false) }
    }

    fun onDraftCategorySelected(categoryId: Long?) {
        _uiState.update {
            it.copy(draftCategoryId = if (it.draftCategoryId == categoryId) null else categoryId)
        }
    }

    fun onDraftBrandSelected(brandId: Long?) {
        _uiState.update {
            it.copy(draftBrandId = if (it.draftBrandId == brandId) null else brandId)
        }
    }

    fun onDraftColorSelected(color: String?) {
        _uiState.update {
            it.copy(draftColor = if (it.draftColor == color) null else color)
        }
    }

    fun onDraftSizeSelected(size: String?) {
        _uiState.update {
            it.copy(draftSize = if (it.draftSize == size) null else size)
        }
    }

    fun onDraftPriceChanged(min: Long?, max: Long?) {
        _uiState.update { it.copy(draftMinPrice = min, draftMaxPrice = max) }
    }

    fun onApplyDraftFilters() {
        _uiState.update { current ->
            current.copy(
                isFilterSheetOpen = false,
                activeCategoryId = current.draftCategoryId,
                activeBrandId = current.draftBrandId,
                activeColor = current.draftColor,
                activeSize = current.draftSize,
                activeMinPrice = current.draftMinPrice,
                activeMaxPrice = current.draftMaxPrice,
            )
        }
        savedStateHandle[KEY_CATEGORY_ID] = _uiState.value.activeCategoryId
        loadProducts(page = 1)
    }

    fun onClearDraftFilters() {
        _uiState.update {
            it.copy(
                draftCategoryId = null,
                draftBrandId = null,
                draftColor = null,
                draftSize = null,
                draftMinPrice = null,
                draftMaxPrice = null,
            )
        }
    }

    fun onClearAllFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                activeCategoryId = null,
                activeBrandId = null,
                activeColor = null,
                activeSize = null,
                activeMinPrice = null,
                activeMaxPrice = null,
                activeSort = CatalogSortOption.NEWEST,
            )
        }
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle[KEY_CATEGORY_ID] = null
        savedStateHandle[KEY_SORT] = CatalogSortOption.NEWEST.id
        loadProducts(page = 1)
    }

    fun onLoadMore() {
        val current = _uiState.value
        if (!current.isLoadingMore && current.hasMore && current.productsState is UiState.Content) {
            loadProducts(page = current.page + 1)
        }
    }

    fun onRetry() {
        loadFilters()
        loadProducts(page = 1)
    }

    companion object {
        const val KEY_QUERY = "catalog_query"
        const val KEY_CATEGORY_ID = "catalog_category_id"
        const val KEY_SORT = "catalog_sort"
    }
}
