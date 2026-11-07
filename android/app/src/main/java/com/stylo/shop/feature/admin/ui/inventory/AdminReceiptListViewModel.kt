package com.stylo.shop.feature.admin.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminInventoryRepository
import com.stylo.shop.feature.admin.model.AdminReceiptQuery
import com.stylo.shop.feature.admin.model.AdminReceiptSummary
import com.stylo.shop.feature.admin.model.RECEIPT_PAGE_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReceiptListUiState(
    val receiptsState: UiState<List<AdminReceiptSummary>> = UiState.Loading,
    val page: Int = 1,
    val limit: Int = RECEIPT_PAGE_SIZE,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
) {
    val hasMore: Boolean
        get() = limit > 0 && page * limit < total
}

@HiltViewModel
class AdminReceiptListViewModel @Inject constructor(
    private val repository: AdminInventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReceiptListUiState())
    val uiState: StateFlow<AdminReceiptListUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0

    init {
        load(page = 1)
    }

    /** Returning from the form or detail must show the new receipt. */
    fun refreshIfLoaded() {
        if (_uiState.value.receiptsState is UiState.Content) {
            load(page = 1, showLoading = false)
        }
    }

    fun load(page: Int, showLoading: Boolean = true) {
        val snapshot = _uiState.value
        if (page > 1 && (snapshot.isLoadingMore || !snapshot.hasMore)) return

        val generation = ++loadGeneration
        val isInitial = page == 1
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) {
                    it.copy(
                        receiptsState = if (showLoading && it.receiptsState !is UiState.Content) {
                            UiState.Loading
                        } else {
                            it.receiptsState
                        },
                        page = 1,
                        isLoadingMore = false,
                        loadMoreError = null,
                    )
                } else {
                    it.copy(isLoadingMore = true, loadMoreError = null)
                }
            }

            val result = repository.listReceipts(
                AdminReceiptQuery(page = page, limit = snapshot.limit),
            )
            if (generation != loadGeneration) return@launch

            when (result) {
                is ApiResult.Success -> {
                    val loaded = result.data
                    _uiState.update { state ->
                        val merged = if (isInitial) {
                            loaded.items
                        } else {
                            val existing = (state.receiptsState as? UiState.Content)?.data.orEmpty()
                            existing + loaded.items.filter { new ->
                                existing.none { it.id == new.id }
                            }
                        }
                        state.copy(
                            receiptsState = UiState.Content(merged),
                            page = loaded.page,
                            limit = loaded.limit,
                            total = loaded.total,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { state ->
                        if (isInitial && state.receiptsState !is UiState.Content) {
                            state.copy(
                                receiptsState = UiState.Error(result.error),
                                isLoadingMore = false,
                            )
                        } else {
                            state.copy(
                                isLoadingMore = false,
                                loadMoreError = if (isInitial) null else result.error,
                            )
                        }
                    }
                }
            }
        }
    }

    fun loadMore() = load(_uiState.value.page + 1)

    fun retryLoadMore() = load(_uiState.value.page + 1)

    fun onRetry() = load(page = 1)
}
