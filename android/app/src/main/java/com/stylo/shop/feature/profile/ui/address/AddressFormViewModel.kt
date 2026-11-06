package com.stylo.shop.feature.profile.ui.address

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.profile.data.ProfileRepository
import com.stylo.shop.feature.profile.model.ADDRESS_LINE_MAX
import com.stylo.shop.feature.profile.model.AddressInput
import com.stylo.shop.feature.profile.model.PROFILE_NAME_MAX
import com.stylo.shop.feature.profile.model.PROFILE_PHONE_MAX
import com.stylo.shop.feature.profile.model.ProfileFieldError
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddressFormUiState(
    val loading: Boolean = false,
    val loadError: ApiError? = null,
    val addressId: Long? = null,
    val recipientName: String = "",
    val phone: String = "",
    val line: String = "",
    val isDefault: Boolean = false,
    val isDefaultLocked: Boolean = false,
    val recipientNameError: ProfileFieldError? = null,
    val phoneError: ProfileFieldError? = null,
    val lineError: ProfileFieldError? = null,
    val saving: Boolean = false,
    val formError: ApiError? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class AddressFormViewModel @Inject constructor(
    private val repository: ProfileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null on the create route; the edit route passes the address id. */
    private val editAddressId: Long? = savedStateHandle.get<Long>("addressId")

    private val _uiState = MutableStateFlow(
        AddressFormUiState(addressId = editAddressId, loading = editAddressId != null),
    )
    val uiState: StateFlow<AddressFormUiState> = _uiState.asStateFlow()

    init {
        if (editAddressId != null) load()
    }

    fun load() {
        val addressId = editAddressId ?: return
        _uiState.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            when (val result = repository.listAddresses()) {
                is ApiResult.Success -> {
                    val address = result.data.firstOrNull { it.id == addressId }
                    if (address == null) {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                loadError = ApiError(
                                    kind = ApiErrorKind.HTTP,
                                    code = "NOT_FOUND",
                                    httpStatus = 404,
                                ),
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                loading = false,
                                loadError = null,
                                addressId = address.id,
                                recipientName = address.recipientName,
                                phone = address.phone,
                                line = address.line,
                                isDefault = address.isDefault,
                                isDefaultLocked = address.isDefault,
                            )
                        }
                    }
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(loading = false, loadError = result.error)
                }
            }
        }
    }

    fun onRecipientNameChange(value: String) {
        _uiState.update {
            it.copy(recipientName = value, recipientNameError = null, formError = null)
        }
    }

    fun onPhoneChange(value: String) {
        _uiState.update { it.copy(phone = value, phoneError = null, formError = null) }
    }

    fun onLineChange(value: String) {
        _uiState.update { it.copy(line = value, lineError = null, formError = null) }
    }

    fun onDefaultChange(value: Boolean) {
        val state = _uiState.value
        if (state.isDefaultLocked) return
        _uiState.update { it.copy(isDefault = value) }
    }

    fun save() {
        val state = _uiState.value
        if (state.saving || state.loading) return

        val recipientName = state.recipientName.trim()
        val phone = state.phone.trim()
        val line = state.line.trim()
        val recipientNameError = when {
            recipientName.isEmpty() -> ProfileFieldError.REQUIRED
            recipientName.length > PROFILE_NAME_MAX -> ProfileFieldError.TOO_LONG
            else -> null
        }
        val phoneError = when {
            phone.isEmpty() -> ProfileFieldError.REQUIRED
            phone.length > PROFILE_PHONE_MAX -> ProfileFieldError.TOO_LONG
            else -> null
        }
        val lineError = when {
            line.isEmpty() -> ProfileFieldError.REQUIRED
            line.length > ADDRESS_LINE_MAX -> ProfileFieldError.TOO_LONG
            else -> null
        }
        if (recipientNameError != null || phoneError != null || lineError != null) {
            _uiState.update {
                it.copy(
                    recipientNameError = recipientNameError,
                    phoneError = phoneError,
                    lineError = lineError,
                )
            }
            return
        }

        val input = AddressInput(
            recipientName = recipientName,
            phone = phone,
            line = line,
            isDefault = state.isDefault,
        )
        _uiState.update {
            it.copy(
                saving = true,
                recipientNameError = null,
                phoneError = null,
                lineError = null,
                formError = null,
            )
        }
        viewModelScope.launch {
            val result = if (state.addressId == null) {
                repository.createAddress(input)
            } else {
                repository.updateAddress(state.addressId, input)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update { it.copy(saving = false, saved = true) }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(saving = false, formError = result.error)
                }
            }
        }
    }
}
