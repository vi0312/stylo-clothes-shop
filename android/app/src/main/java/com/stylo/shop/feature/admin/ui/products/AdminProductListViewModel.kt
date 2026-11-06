package com.stylo.shop.feature.admin.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminCatalogRepository
import com.stylo.shop.feature.admin.model.AdminProductQuery
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.AdminTaxonomyQuery
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.model.LOW_STOCK_THRESHOLD
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminStatusFilter {
    ALL,
    ACTIVE,
    INACTIVE,
    LOW_STOCK,
}

private const val PRODUCT_PAGE_SIZE = 20
private const val PRODUCT_PAGE_LIMIT_MAX = 100
private const val PRODUCT_MAX_PAGES = 20

data class AdminProductListUiState(
    val productsState: UiState<List<AdminProductSummary>> = UiState.Loading,
    val categories: List<AdminTaxonomy> = emptyList(),
    val brands: List<AdminTaxonomy> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: AdminStatusFilter = AdminStatusFilter.ALL,
    val categoryId: Long? = null,
    val brandId: Long? = null,
    val page: Int = 1,
    val limit: Int = PRODUCT_PAGE_SIZE,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
) {
    val hasMore: Boolean
        get() = limit > 0 && page * limit < total

    val activeFilterCount: Int
        get() = listOfNotNull(categoryId, brandId).size
}

@HiltViewModel
class AdminProductListViewModel @Inject constructor(
    private val repository: AdminCatalogRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminProductListUiState())
    val uiState: StateFlow<AdminProductListUiState> = _uiState.asStateFlow()

    private var initialLoadComplete = false
    private var loadGeneration = 0

    init {
        loadTaxonomies()
        loadProducts(page = 1)
    }

    fun refreshIfLoaded() {
        if (initialLoadComplete) {
            loadProducts(page = 1)
        }
    }

    fun loadTaxonomies() {
        viewModelScope.launch {
            val categories = repository.listCategories(
                AdminTaxonomyQuery(limit = PRODUCT_PAGE_LIMIT_MAX),
            )
            val brands = repository.listBrands(
                AdminTaxonomyQuery(limit = PRODUCT_PAGE_LIMIT_MAX),
            )
            _uiState.update { current ->
                current.copy(
                    categories = (categories as? ApiResult.Success)?.data?.items.orEmpty(),
                    brands = (brands as? ApiResult.Success)?.data?.items.orEmpty(),
                )
            }
        }
    }

    fun loadProducts(page: Int) {
        val snapshot = _uiState.value
        if (page > 1 && (snapshot.isLoadingMore || !snapshot.hasMore)) {
            return
        }

        // Filter changes must never be overwritten by an older in-flight page.
        val generation = ++loadGeneration
        viewModelScope.launch {
            val isInitial = page == 1
            val lowStock = snapshot.statusFilter == AdminStatusFilter.LOW_STOCK
            val limit = if (lowStock) PRODUCT_PAGE_LIMIT_MAX else PRODUCT_PAGE_SIZE

            _uiState.update {
                if (isInitial) {
                    it.copy(
                        productsState = UiState.Loading,
                        page = 1,
                        limit = limit,
                        isLoadingMore = false,
                        loadMoreError = null,
                    )
                } else {
                    it.copy(isLoadingMore = true, loadMoreError = null)
                }
            }

            val query = AdminProductQuery(
                q = snapshot.searchQuery.trim().takeIf { it.isNotEmpty() },
                status = when (snapshot.statusFilter) {
                    AdminStatusFilter.ACTIVE -> CatalogStatus.ACTIVE
                    AdminStatusFilter.INACTIVE -> CatalogStatus.INACTIVE
                    else -> null
                },
                categoryId = snapshot.categoryId,
                brandId = snapshot.brandId,
                page = page,
                limit = limit,
            )

            if (lowStock) {
                // The API has no low-stock filter, so walk the whole result set before filtering.
                val collected = mutableListOf<AdminProductSummary>()
                var total = 0
                var currentPage = 1
                while (currentPage <= PRODUCT_MAX_PAGES) {
                    val result = repository.listProducts(query.copy(page = currentPage))
                    if (generation != loadGeneration) return@launch
                    when (result) {
                        is ApiResult.Success -> {
                            collected += result.data.items
                            total = result.data.total
                            if (collected.size >= total) break
                            currentPage++
                        }
                        is ApiResult.Failure -> {
                            applyFailure(generation, isInitial, result.error)
                            return@launch
                        }
                    }
                }

                val filtered = collected.filter { it.totalStock <= LOW_STOCK_THRESHOLD }
                initialLoadComplete = true
                _uiState.update { previous ->
                    previous.copy(
                        productsState = UiState.Content(filtered),
                        page = 1,
                        limit = PRODUCT_PAGE_LIMIT_MAX,
                        total = filtered.size,
                        isLoadingMore = false,
                        loadMoreError = null,
                    )
                }
            } else {
                when (val result = repository.listProducts(query)) {
                    is ApiResult.Success -> {
                        if (generation != loadGeneration) return@launch
                        initialLoadComplete = true
                        _uiState.update { previous ->
                            val combined = if (isInitial) {
                                result.data.items
                            } else {
                                val existing =
                                    (previous.productsState as? UiState.Content)?.data.orEmpty()
                                existing + result.data.items
                            }
                            previous.copy(
                                productsState = UiState.Content(combined),
                                page = result.data.page,
                                limit = limit,
                                total = result.data.total,
                                isLoadingMore = false,
                                loadMoreError = null,
                            )
                        }
                    }
                    is ApiResult.Failure -> {
                        applyFailure(generation, isInitial, result.error)
                    }
                }
            }
        }
    }

    private fun applyFailure(generation: Int, isInitial: Boolean, error: ApiError) {
        if (generation != loadGeneration) return
        initialLoadComplete = true
        _uiState.update { previous ->
            if (isInitial) {
                previous.copy(
                    productsState = UiState.Error(error),
                    isLoadingMore = false,
                )
            } else {
                previous.copy(
                    isLoadingMore = false,
                    loadMoreError = error,
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchSubmit() {
        loadProducts(page = 1)
    }

    fun onClearSearch() {
        _uiState.update { it.copy(searchQuery = "") }
        loadProducts(page = 1)
    }

    fun onStatusFilterSelected(filter: AdminStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        loadProducts(page = 1)
    }

    fun onCategorySelected(categoryId: Long?) {
        if (_uiState.value.categoryId == categoryId) return
        _uiState.update { it.copy(categoryId = categoryId) }
        loadProducts(page = 1)
    }

    fun onBrandSelected(brandId: Long?) {
        if (_uiState.value.brandId == brandId) return
        _uiState.update { it.copy(brandId = brandId) }
        loadProducts(page = 1)
    }

    fun onClearFilters() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                statusFilter = AdminStatusFilter.ALL,
                categoryId = null,
                brandId = null,
            )
        }
        loadProducts(page = 1)
    }

    fun onLoadMore() {
        val current = _uiState.value
        if (current.productsState is UiState.Content && current.hasMore && !current.isLoadingMore) {
            loadProducts(page = current.page + 1)
        }
    }

    fun onRetry() {
        loadTaxonomies()
        loadProducts(page = 1)
    }

}
