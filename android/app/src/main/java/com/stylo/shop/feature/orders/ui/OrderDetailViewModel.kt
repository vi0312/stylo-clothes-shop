package com.stylo.shop.feature.orders.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.orders.data.OrdersRepository
import com.stylo.shop.feature.orders.model.Order
import com.stylo.shop.feature.orders.model.canCancelOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val orderState: UiState<Order> = UiState.Loading,
    val isCancelling: Boolean = false,
    val cancelError: ApiError? = null,
) {
    val canCancel: Boolean
        get() = (orderState as? UiState.Content)?.data?.let(::canCancelOrder) == true
}

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    private val repository: OrdersRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: Long = savedStateHandle.get<Long>("orderId")
        ?: savedStateHandle.get<String>("orderId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(OrderDetailUiState())
    val uiState: StateFlow<OrderDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load(showLoading: Boolean = true) {
        if (orderId <= 0L) return
        viewModelScope.launch {
            if (showLoading) _uiState.update { it.copy(orderState = UiState.Loading) }
            val result = repository.getOrder(orderId)
            _uiState.update {
                it.copy(
                    orderState = when (result) {
                        is ApiResult.Success -> UiState.Content(result.data)
                        is ApiResult.Failure -> UiState.Error(result.error)
                    },
                )
            }
        }
    }

    fun retry() = load()

    /** The payment page runs outside the app; state is re-read whenever the screen returns. */
    fun refresh() = load(showLoading = false)

    fun cancel() {
        val snapshot = _uiState.value
        if (snapshot.isCancelling || !snapshot.canCancel) return
        val order = (snapshot.orderState as? UiState.Content)?.data ?: return

        // Claimed before the coroutine starts so a double tap cannot post two cancels.
        _uiState.update { it.copy(isCancelling = true, cancelError = null) }
        viewModelScope.launch {
            when (val result = repository.cancelOrder(order.id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(orderState = UiState.Content(result.data), isCancelling = false)
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isCancelling = false, cancelError = result.error) }
                    // A rejected cancel means the server moved the order; show what it says now.
                    load(showLoading = false)
                }
            }
        }
    }

    fun dismissCancelError() {
        _uiState.update { it.copy(cancelError = null) }
    }
}
