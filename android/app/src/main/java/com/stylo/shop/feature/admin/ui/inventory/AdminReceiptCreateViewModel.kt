package com.stylo.shop.feature.admin.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminInventoryRepository
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminVariantOption
import com.stylo.shop.feature.admin.model.CreateReceiptInput
import com.stylo.shop.feature.admin.model.CreateReceiptLineInput
import com.stylo.shop.feature.admin.model.RECEIPT_NOTE_MAX
import com.stylo.shop.feature.admin.model.RECEIPT_SUPPLIER_MAX
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PICKER_DEBOUNCE_MS = 300L
private const val QUANTITY_MAX_DIGITS = 9
private const val UNIT_COST_MAX_DIGITS = 12

data class ReceiptLineDraft(
    val variant: AdminVariantOption,
    val quantity: String = "",
    val unitCost: String = "",
    val quantityError: AdminFieldError? = null,
    val unitCostError: AdminFieldError? = null,
) {
    val lineTotal: Long
        get() {
            val quantityValue = quantity.toIntOrNull() ?: return 0
            val costValue = unitCost.toLongOrNull() ?: return 0
            return if (quantityValue > 0 && costValue >= 0) {
                quantityValue.toLong() * costValue
            } else {
                0
            }
        }
}

data class VariantPickerUiState(
    val query: String = "",
    val results: UiState<List<AdminVariantOption>> = UiState.Loading,
)

data class AdminReceiptCreateUiState(
    val supplier: String = "",
    val note: String = "",
    val lines: List<ReceiptLineDraft> = emptyList(),
    val supplierError: AdminFieldError? = null,
    val noteError: AdminFieldError? = null,
    val itemsError: Boolean = false,
    val saving: Boolean = false,
    val formError: ApiError? = null,
    val createdReceiptId: Long? = null,
    val picker: VariantPickerUiState? = null,
) {
    val totalCost: Long
        get() = lines.sumOf { it.lineTotal }
}

@HiltViewModel
class AdminReceiptCreateViewModel @Inject constructor(
    private val repository: AdminInventoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminReceiptCreateUiState())
    val uiState: StateFlow<AdminReceiptCreateUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var searchGeneration = 0

    fun onSupplierChange(value: String) {
        _uiState.update { it.copy(supplier = value, supplierError = null) }
    }

    fun onNoteChange(value: String) {
        _uiState.update { it.copy(note = value, noteError = null) }
    }

    fun openVariantPicker() {
        _uiState.update { it.copy(picker = VariantPickerUiState(results = UiState.Loading)) }
        searchPicker(query = "", debounce = false)
    }

    fun dismissVariantPicker() {
        searchJob?.cancel()
        _uiState.update { it.copy(picker = null) }
    }

    fun onPickerQueryChange(value: String) {
        _uiState.update { state ->
            state.copy(picker = state.picker?.copy(query = value))
        }
        searchPicker(query = value, debounce = true)
    }

    fun retryPickerSearch() {
        val query = _uiState.value.picker?.query.orEmpty()
        searchPicker(query = query, debounce = false)
    }

    fun selectVariant(option: AdminVariantOption) {
        searchJob?.cancel()
        _uiState.update { state ->
            state.copy(
                lines = state.lines + ReceiptLineDraft(variant = option),
                itemsError = false,
                picker = null,
            )
        }
    }

    fun removeLine(index: Int) {
        _uiState.update { state ->
            state.copy(lines = state.lines.filterIndexed { i, _ -> i != index })
        }
    }

    fun onQuantityChange(index: Int, value: String) {
        val cleaned = value.filter(Char::isDigit).take(QUANTITY_MAX_DIGITS)
        updateLine(index) { it.copy(quantity = cleaned, quantityError = null) }
    }

    fun onUnitCostChange(index: Int, value: String) {
        val cleaned = value.filter(Char::isDigit).take(UNIT_COST_MAX_DIGITS)
        updateLine(index) { it.copy(unitCost = cleaned, unitCostError = null) }
    }

    fun save() {
        val snapshot = _uiState.value
        if (snapshot.saving) return

        val supplier = snapshot.supplier.trim()
        val note = snapshot.note.trim()
        val supplierError = when {
            supplier.isEmpty() -> AdminFieldError.REQUIRED
            supplier.length > RECEIPT_SUPPLIER_MAX -> AdminFieldError.TOO_LONG
            else -> null
        }
        val noteError = if (note.length > RECEIPT_NOTE_MAX) AdminFieldError.TOO_LONG else null

        var linesInvalid = snapshot.lines.isEmpty()
        val validatedLines = snapshot.lines.map { line ->
            val quantity = line.quantity.trim().toIntOrNull()
            val unitCost = line.unitCost.trim().toLongOrNull()
            val quantityError = when {
                line.quantity.isBlank() -> AdminFieldError.REQUIRED
                quantity == null || quantity < 1 -> AdminFieldError.INVALID_QUANTITY
                else -> null
            }
            val unitCostError = when {
                line.unitCost.isBlank() -> AdminFieldError.REQUIRED
                unitCost == null || unitCost < 0 -> AdminFieldError.INVALID_COST
                else -> null
            }
            if (quantityError != null || unitCostError != null) linesInvalid = true
            line.copy(quantityError = quantityError, unitCostError = unitCostError)
        }

        if (supplierError != null || noteError != null || linesInvalid) {
            _uiState.update {
                it.copy(
                    supplier = snapshot.supplier,
                    note = snapshot.note,
                    lines = validatedLines,
                    supplierError = supplierError,
                    noteError = noteError,
                    itemsError = snapshot.lines.isEmpty(),
                    formError = null,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                supplier = snapshot.supplier,
                note = snapshot.note,
                lines = validatedLines,
                supplierError = null,
                noteError = null,
                itemsError = false,
                saving = true,
                formError = null,
            )
        }

        viewModelScope.launch {
            val result = repository.createReceipt(
                CreateReceiptInput(
                    supplier = supplier,
                    note = note.ifEmpty { null },
                    items = validatedLines.map { line ->
                        CreateReceiptLineInput(
                            variantId = line.variant.id,
                            quantity = requireNotNull(line.quantity.trim().toIntOrNull()),
                            unitCost = requireNotNull(line.unitCost.trim().toLongOrNull()),
                        )
                    },
                ),
            )
            _uiState.update { state ->
                when (result) {
                    is ApiResult.Success -> state.copy(
                        saving = false,
                        createdReceiptId = result.data.id,
                    )
                    is ApiResult.Failure -> state.copy(
                        saving = false,
                        formError = result.error,
                    )
                }
            }
        }
    }

    fun dismissFormError() {
        _uiState.update { it.copy(formError = null) }
    }

    private fun updateLine(index: Int, transform: (ReceiptLineDraft) -> ReceiptLineDraft) {
        _uiState.update { state ->
            if (index !in state.lines.indices) return@update state
            state.copy(
                lines = state.lines.mapIndexed { i, line ->
                    if (i == index) transform(line) else line
                },
            )
        }
    }

    private fun searchPicker(query: String, debounce: Boolean) {
        val generation = ++searchGeneration
        searchJob?.cancel()
        _uiState.update { state ->
            state.copy(picker = state.picker?.copy(results = UiState.Loading))
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(PICKER_DEBOUNCE_MS)
            val result = repository.searchVariants(query)
            if (generation != searchGeneration) return@launch
            _uiState.update { state ->
                val picker = state.picker ?: return@update state
                state.copy(
                    picker = picker.copy(
                        results = when (result) {
                            is ApiResult.Success -> UiState.Content(result.data)
                            is ApiResult.Failure -> UiState.Error(result.error)
                        },
                    ),
                )
            }
        }
    }
}
