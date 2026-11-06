package com.stylo.shop.feature.profile.ui.address

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.profile.data.ProfileRepository
import com.stylo.shop.feature.profile.model.Address
import com.stylo.shop.feature.profile.model.AddressInput
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

data class AddressListUiState(
    val itemsState: UiState<List<Address>> = UiState.Loading,
    val defaultingId: Long? = null,
    val deletingId: Long? = null,
    val confirmDelete: Address? = null,
)

@HiltViewModel
class AddressListViewModel @Inject constructor(
    private val repository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddressListUiState())
    val uiState: StateFlow<AddressListUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    fun load(showLoading: Boolean = true) {
        if (showLoading) {
            _uiState.update { it.copy(itemsState = UiState.Loading) }
        }
        viewModelScope.launch {
            when (val result = repository.listAddresses()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(itemsState = UiState.Content(result.data))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(itemsState = UiState.Error(result.error))
                }
            }
        }
    }

    fun setDefault(address: Address) {
        val state = _uiState.value
        if (address.isDefault || state.defaultingId != null) return

        _uiState.update { it.copy(defaultingId = address.id) }
        viewModelScope.launch {
            val input = AddressInput(
                recipientName = address.recipientName,
                phone = address.phone,
                line = address.line,
                isDefault = true,
            )
            when (val result = repository.updateAddress(address.id, input)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(defaultingId = null) }
                    _effects.send(UiEffect.ShowMessageRes(R.string.address_default_set))
                    load(showLoading = false)
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(defaultingId = null) }
                    _effects.send(UiEffect.ShowError(result.error))
                }
            }
        }
    }

    fun requestDelete(address: Address) {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(confirmDelete = address) }
    }

    fun dismissDelete() {
        if (_uiState.value.deletingId != null) return
        _uiState.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val state = _uiState.value
        val target = state.confirmDelete ?: return
        if (state.deletingId != null) return

        _uiState.update { it.copy(deletingId = target.id) }
        viewModelScope.launch {
            when (val result = repository.deleteAddress(target.id)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(deletingId = null, confirmDelete = null) }
                    _effects.send(UiEffect.ShowMessageRes(R.string.address_deleted))
                    load(showLoading = false)
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(deletingId = null, confirmDelete = null) }
                    _effects.send(UiEffect.ShowError(result.error))
                }
            }
        }
    }
}
