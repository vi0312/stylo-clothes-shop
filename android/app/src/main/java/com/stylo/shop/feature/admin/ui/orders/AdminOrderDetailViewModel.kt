package com.stylo.shop.feature.admin.ui.orders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminOrdersRepository
import com.stylo.shop.feature.admin.model.AdminOrderDetail
import com.stylo.shop.feature.admin.model.allowedOrderTransitions
import com.stylo.shop.feature.orders.model.OrderStatus
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

data class AdminOrderDetailUiState(
    val orderState: UiState<AdminOrderDetail> = UiState.Loading,
    val isUpdating: Boolean = false,
) {
    val order: AdminOrderDetail?
        get() = (orderState as? UiState.Content)?.data

    /** Only legal targets are offered; the server still rejects anything stale or paid. */
    val allowedTargets: List<OrderStatus>
        get() = order?.let {
            allowedOrderTransitions(it.status, paid = it.paidAt != null)
        }.orEmpty()
}

@HiltViewModel
class AdminOrderDetailViewModel @Inject constructor(
    private val repository: AdminOrdersRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val orderId: Long = savedStateHandle.get<Long>("orderId")
        ?: savedStateHandle.get<String>("orderId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(AdminOrderDetailUiState())
    val uiState: StateFlow<AdminOrderDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

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

    fun transition(target: OrderStatus) {
        val snapshot = _uiState.value
        val order = snapshot.order ?: return
        if (snapshot.isUpdating || target !in snapshot.allowedTargets) return

        // Claimed before the coroutine starts so a double tap cannot post two transitions.
        _uiState.update { it.copy(isUpdating = true) }
        viewModelScope.launch {
            when (val result = repository.updateStatus(order.id, target)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(orderState = UiState.Content(result.data), isUpdating = false)
                    }
                    _effects.send(
                        UiEffect.ShowMessageRes(
                            R.string.admin_order_transition_done,
                            listOf(order.code),
                        ),
                    )
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isUpdating = false) }
                    _effects.send(UiEffect.ShowError(result.error))
                    // The order moved under us; re-read so the offered actions match reality.
                    load(showLoading = false)
                }
            }
        }
    }
}
