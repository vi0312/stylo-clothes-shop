package com.stylo.shop.feature.admin.ui.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminInventoryRepository
import com.stylo.shop.feature.admin.model.AdminReceiptDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminReceiptDetailUiState(
    val receiptState: UiState<AdminReceiptDetail> = UiState.Loading,
)

@HiltViewModel
class AdminReceiptDetailViewModel @Inject constructor(
    private val repository: AdminInventoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val receiptId: Long = savedStateHandle.get<Long>("receiptId")
        ?: savedStateHandle.get<String>("receiptId")?.toLongOrNull()
        ?: 0L

    private val _uiState = MutableStateFlow(AdminReceiptDetailUiState())
    val uiState: StateFlow<AdminReceiptDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(receiptState = UiState.Loading) }
            val result = repository.getReceipt(receiptId)
            _uiState.update {
                it.copy(
                    receiptState = when (result) {
                        is ApiResult.Success -> UiState.Content(result.data)
                        is ApiResult.Failure -> UiState.Error(result.error)
                    },
                )
            }
        }
    }
}
