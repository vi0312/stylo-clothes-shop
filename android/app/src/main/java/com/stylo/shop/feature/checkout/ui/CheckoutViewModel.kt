package com.stylo.shop.feature.checkout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.cart.data.CartRepository
import com.stylo.shop.feature.cart.model.Cart
import com.stylo.shop.feature.checkout.data.CheckoutRepository
import com.stylo.shop.feature.checkout.data.toSubmitError
import com.stylo.shop.feature.checkout.model.CHECKOUT_NOTE_MAX
import com.stylo.shop.feature.checkout.model.CheckoutDraft
import com.stylo.shop.feature.checkout.model.SubmitError
import com.stylo.shop.feature.orders.model.OrderPaymentMethod
import com.stylo.shop.feature.profile.data.ProfileRepository
import com.stylo.shop.feature.profile.model.Address
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val CLIENT_ORDER_ID_KEY = "clientOrderId"

data class CheckoutUiState(
    val cartState: UiState<Cart> = UiState.Loading,
    val addressesState: UiState<List<Address>> = UiState.Loading,
    val selectedAddressId: Long? = null,
    val paymentMethod: OrderPaymentMethod = OrderPaymentMethod.COD,
    val note: String = "",
    val isSubmitting: Boolean = false,
    val submitError: SubmitError? = null,
) {
    val cart: Cart? get() = (cartState as? UiState.Content)?.data
    val addresses: List<Address> get() = (addressesState as? UiState.Content)?.data.orEmpty()
    val selectedAddress: Address? get() = addresses.firstOrNull { it.id == selectedAddressId }
    val isLoading: Boolean get() = cartState is UiState.Loading || addressesState is UiState.Loading
    val loadError: ApiError?
        get() = (cartState as? UiState.Error)?.error ?: (addressesState as? UiState.Error)?.error
    val canSubmit: Boolean
        get() = !isSubmitting &&
            selectedAddress != null &&
            cart?.isEmpty == false &&
            note.length <= CHECKOUT_NOTE_MAX
    val total: Long get() = cart?.total ?: 0
}

sealed interface CheckoutEffect {
    data class Placed(val orderId: Long, val paymentUrl: String?) : CheckoutEffect
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    private val checkoutRepository: CheckoutRepository,
    private val cartRepository: CartRepository,
    private val profileRepository: ProfileRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CheckoutEffect>(Channel.BUFFERED)
    val effects: Flow<CheckoutEffect> = _effects.receiveAsFlow()

    init {
        load()
    }

    /**
     * Kept in [SavedStateHandle] so a retry after a lost response, even across process death,
     * still carries the id the server already saw; `(user, clientOrderId)` is unique there.
     */
    private fun clientOrderId(): String =
        savedStateHandle.get<String>(CLIENT_ORDER_ID_KEY)
            ?: UUID.randomUUID().toString().also { savedStateHandle[CLIENT_ORDER_ID_KEY] = it }

    fun load(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.update {
                    it.copy(
                        cartState = UiState.Loading,
                        addressesState = UiState.Loading,
                        submitError = null,
                    )
                }
            }
            val cartResult = cartRepository.getCart()
            val addressResult = profileRepository.listAddresses()

            _uiState.update { state ->
                val addresses = (addressResult as? ApiResult.Success)?.data
                val selected = addresses?.firstOrNull { it.id == state.selectedAddressId }
                    ?: addresses?.firstOrNull { it.isDefault }
                    ?: addresses?.firstOrNull()
                state.copy(
                    cartState = cartResult.asState(),
                    addressesState = addressResult.asState(),
                    selectedAddressId = selected?.id ?: state.selectedAddressId.takeIf { addresses == null },
                )
            }
        }
    }

    fun retry() = load()

    fun selectAddress(addressId: Long) {
        _uiState.update { it.copy(selectedAddressId = addressId, submitError = null) }
    }

    fun selectPaymentMethod(method: OrderPaymentMethod) {
        _uiState.update { it.copy(paymentMethod = method, submitError = null) }
    }

    fun updateNote(value: String) {
        _uiState.update { it.copy(note = value.take(CHECKOUT_NOTE_MAX), submitError = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(submitError = null) }
    }

    fun submit() {
        val state = _uiState.value
        val address = state.selectedAddress ?: return
        if (!state.canSubmit) return

        // Set before suspending: a second tap arrives while `isSubmitting` is already true.
        _uiState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val draft = CheckoutDraft(
                clientOrderId = clientOrderId(),
                addressId = address.id,
                paymentMethod = state.paymentMethod,
                note = state.note.trim().takeIf { it.isNotEmpty() },
            )
            when (val result = checkoutRepository.placeOrder(draft)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _effects.send(
                        CheckoutEffect.Placed(
                            orderId = result.data.order.id,
                            paymentUrl = result.data.paymentUrl,
                        ),
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isSubmitting = false, submitError = result.error.toSubmitError())
                }
            }
        }
    }
}

private fun <T> ApiResult<T>.asState(): UiState<T> = when (this) {
    is ApiResult.Success -> UiState.Content(data)
    is ApiResult.Failure -> UiState.Error(error)
}
