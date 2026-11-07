package com.stylo.shop.feature.orders.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.orders.data.OrdersRepository
import com.stylo.shop.feature.orders.model.ORDER_PAGE_SIZE
import com.stylo.shop.feature.orders.model.OrderStatus
import com.stylo.shop.feature.orders.model.OrderSummary
import com.stylo.shop.feature.orders.model.OrdersQuery
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OrderStatusFilter(val status: OrderStatus?) {
    ALL(null),
    PENDING(OrderStatus.PENDING),
    CONFIRMED(OrderStatus.CONFIRMED),
    SHIPPING(OrderStatus.SHIPPING),
    DELIVERED(OrderStatus.DELIVERED),
    CANCELLED(OrderStatus.CANCELLED),
}

data class OrderListUiState(
    val ordersState: UiState<List<OrderSummary>> = UiState.Loading,
    val filter: OrderStatusFilter = OrderStatusFilter.ALL,
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
class OrderListViewModel @Inject constructor(
    private val repository: OrdersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderListUiState())
    val uiState: StateFlow<OrderListUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0

    init {
        loadOrders(page = 1)
    }

    /** Cancelling in the detail screen must be visible here on return, without a spinner flash. */
    fun refreshIfLoaded() {
        if (_uiState.value.ordersState is UiState.Content) {
            loadOrders(page = 1, showLoading = false)
        }
    }

    fun loadOrders(page: Int, showLoading: Boolean = true) {
        val snapshot = _uiState.value
        if (page > 1 && (snapshot.isLoadingMore || !snapshot.hasMore)) return

        // A filter change while a page is in flight must win over the older response.
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

            val query = OrdersQuery(
                status = snapshot.filter.status,
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

    fun onFilterSelected(filter: OrderStatusFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter) }
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
}
