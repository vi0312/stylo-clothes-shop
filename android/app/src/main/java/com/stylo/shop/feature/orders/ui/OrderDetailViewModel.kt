package com.stylo.shop.feature.orders.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.orders.data.OrdersRepository
import com.stylo.shop.feature.orders.model.Order
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OrderDetailUiState(
    val orderState: UiState<Order> = UiState.Loading,
)

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
}
