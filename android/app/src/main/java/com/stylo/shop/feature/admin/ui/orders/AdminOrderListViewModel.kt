package com.stylo.shop.feature.admin.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminOrdersRepository
import com.stylo.shop.feature.admin.model.AdminOrderQuery
import com.stylo.shop.feature.admin.model.AdminOrderRange
import com.stylo.shop.feature.admin.model.AdminOrderSummary
import com.stylo.shop.feature.admin.model.ORDER_PAGE_SIZE
import com.stylo.shop.feature.admin.model.window
import com.stylo.shop.feature.orders.model.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminOrderStatusFilter(val status: OrderStatus?) {
    ALL(null),
    PENDING(OrderStatus.PENDING),
    CONFIRMED(OrderStatus.CONFIRMED),
    SHIPPING(OrderStatus.SHIPPING),
    DELIVERED(OrderStatus.DELIVERED),
    CANCELLED(OrderStatus.CANCELLED),
}

data class AdminOrderListUiState(
    val ordersState: UiState<List<AdminOrderSummary>> = UiState.Loading,
    val statusFilter: AdminOrderStatusFilter = AdminOrderStatusFilter.ALL,
    val range: AdminOrderRange = AdminOrderRange.ALL,
    val searchQuery: String = "",
    val page: Int = 1,
    val limit: Int = ORDER_PAGE_SIZE,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
) {
    val hasMore: Boolean
        get() = limit > 0 && page * limit < total
}

@HiltViewModel
class AdminOrderListViewModel @Inject constructor(
    private val repository: AdminOrdersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminOrderListUiState())
    val uiState: StateFlow<AdminOrderListUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0

    init {
        loadOrders(page = 1)
    }

    /** Returning from the detail screen must show the transition just applied. */
    fun refreshIfLoaded() {
        if (_uiState.value.ordersState is UiState.Content) {
            loadOrders(page = 1, showLoading = false)
        }
    }

    fun loadOrders(page: Int, showLoading: Boolean = true) {
        val snapshot = _uiState.value
        if (page > 1 && (snapshot.isLoadingMore || !snapshot.hasMore)) return

        val generation = ++loadGeneration
        val isInitial = page == 1
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) {
                    it.copy(
                        ordersState = if (showLoading && it.ordersState !is UiState.Content) {
                            UiState.Loading
                        } else {
                            it.ordersState
                        },
                        page = 1,
                        isLoadingMore = false,
                        loadMoreError = null,
                    )
                } else {
                    it.copy(isLoadingMore = true, loadMoreError = null)
                }
            }

            val (from, to) = snapshot.range.window(todayInVietnam())
            val query = AdminOrderQuery(
                status = snapshot.statusFilter.status,
                q = snapshot.searchQuery.trim().takeIf { it.isNotEmpty() },
                from = from,
                to = to,
                page = page,
                limit = ORDER_PAGE_SIZE,
            )

            when (val result = repository.listOrders(query)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    _uiState.update { previous ->
                        val existing = if (isInitial) {
                            emptyList()
                        } else {
                            (previous.ordersState as? UiState.Content)?.data.orEmpty()
                        }
                        previous.copy(
                            ordersState = UiState.Content(existing + result.data.items),
                            page = result.data.page,
                            limit = result.data.limit,
                            total = result.data.total,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    _uiState.update { previous ->
                        if (isInitial) {
                            previous.copy(
                                ordersState = UiState.Error(result.error),
                                isLoadingMore = false,
                            )
                        } else {
                            previous.copy(
                                isLoadingMore = false,
                                loadMoreError = result.error,
                            )
                        }
                    }
                }
            }
        }
    }

    fun onStatusFilterSelected(filter: AdminOrderStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        loadOrders(page = 1)
    }

    fun onRangeSelected(range: AdminOrderRange) {
        if (_uiState.value.range == range) return
        _uiState.update { it.copy(range = range) }
        loadOrders(page = 1)
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearch() {
        loadOrders(page = 1)
    }

    fun onLoadMore() {
        val current = _uiState.value
        if (current.ordersState is UiState.Content &&
            current.hasMore &&
            !current.isLoadingMore
        ) {
            loadOrders(page = current.page + 1)
        }
    }

    fun onRetry() = loadOrders(page = 1)

    private fun todayInVietnam(): LocalDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))
}
