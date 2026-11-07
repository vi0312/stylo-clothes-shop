package com.stylo.shop.feature.cart.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.cart.data.CartRepository
import com.stylo.shop.feature.cart.model.Cart
import com.stylo.shop.feature.cart.model.CartItem
import com.stylo.shop.feature.cart.model.withItemQuantity
import com.stylo.shop.feature.cart.model.withoutItem
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

data class CartUiState(
    val cartState: UiState<Cart> = UiState.Loading,
    /** One mutation at a time keeps optimistic snapshots from stomping each other. */
    val mutatingItemId: Long? = null,
    val isClearing: Boolean = false,
) {
    val isMutating: Boolean get() = mutatingItemId != null || isClearing
}

@HiltViewModel
class CartViewModel @Inject constructor(
    private val repository: CartRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CartUiState())
    val uiState: StateFlow<CartUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update { it.copy(cartState = UiState.Loading) }
            }
            when (val result = repository.getCart()) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(cartState = UiState.Content(result.data)) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(cartState = UiState.Error(result.error)) }
            }
        }
    }

    fun retry() {
        load()
    }

    fun increase(item: CartItem) {
        changeQuantity(item, item.quantity + 1)
    }

    fun decrease(item: CartItem) {
        changeQuantity(item, item.quantity - 1)
    }

    private fun changeQuantity(item: CartItem, quantity: Int) {
        val state = _uiState.value
        val cart = (state.cartState as? UiState.Content)?.data ?: return
        if (state.isMutating) return
        if (quantity < 1 || quantity > item.availableStock) return

        val optimistic = cart.withItemQuantity(item.id, quantity)
        _uiState.update {
            it.copy(cartState = UiState.Content(optimistic), mutatingItemId = item.id)
        }
        viewModelScope.launch {
            when (val result = repository.updateQuantity(item.id, quantity)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(cartState = UiState.Content(result.data), mutatingItemId = null)
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(cartState = UiState.Content(cart), mutatingItemId = null)
                    }
                    _effects.send(UiEffect.ShowError(result.error))
                }
            }
        }
    }

    fun remove(item: CartItem) {
        val state = _uiState.value
        val cart = (state.cartState as? UiState.Content)?.data ?: return
        if (state.isMutating) return

        _uiState.update {
            it.copy(
                cartState = UiState.Content(cart.withoutItem(item.id)),
                mutatingItemId = item.id,
            )
        }
        viewModelScope.launch {
            when (val result = repository.removeItem(item.id)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(cartState = UiState.Content(result.data), mutatingItemId = null)
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(cartState = UiState.Content(cart), mutatingItemId = null)
                    }
                    _effects.send(UiEffect.ShowError(result.error))
                }
            }
        }
    }

    fun clear() {
        val state = _uiState.value
        val cart = (state.cartState as? UiState.Content)?.data ?: return
        if (state.isMutating || cart.isEmpty) return

        _uiState.update { it.copy(cartState = UiState.Content(emptyCart()), isClearing = true) }
        viewModelScope.launch {
            when (val result = repository.clear()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(cartState = UiState.Content(result.data), isClearing = false)
                }
                is ApiResult.Failure -> {
                    _uiState.update {
                        it.copy(cartState = UiState.Content(cart), isClearing = false)
                    }
                    _effects.send(UiEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun emptyCart(): Cart = Cart(
        id = null,
        items = emptyList(),
        itemCount = 0,
        subtotal = 0,
    )
}
