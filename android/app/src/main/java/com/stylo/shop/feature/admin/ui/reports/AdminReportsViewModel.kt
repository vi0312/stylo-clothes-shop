package com.stylo.shop.feature.admin.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminReportsRepository
import com.stylo.shop.feature.admin.model.InventoryCostReport
import com.stylo.shop.feature.admin.model.LowStockRow
import com.stylo.shop.feature.admin.model.OrdersByStatusRow
import com.stylo.shop.feature.admin.model.ReportGroupBy
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminReportKind {
    REVENUE,
    TOP_PRODUCTS,
    ORDERS_BY_STATUS,
    LOW_STOCK,
    INVENTORY_COST,
}

sealed interface AdminReportContent {
    data class Revenue(val report: RevenueReport) : AdminReportContent
    data class TopProducts(val items: List<TopProductRow>) : AdminReportContent
    data class OrdersByStatus(val items: List<OrdersByStatusRow>) : AdminReportContent
    data class LowStock(val threshold: Int, val items: List<LowStockRow>) : AdminReportContent
    data class InventoryCost(val report: InventoryCostReport) : AdminReportContent
}

data class AdminReportsUiState(
    val kind: AdminReportKind = AdminReportKind.REVENUE,
    val from: String,
    val to: String,
    val groupBy: ReportGroupBy = ReportGroupBy.DAY,
    val thresholdInput: String = DEFAULT_LOW_STOCK_THRESHOLD.toString(),
    val content: UiState<AdminReportContent> = UiState.Loading,
)

const val DEFAULT_LOW_STOCK_THRESHOLD = 5
const val MAX_LOW_STOCK_THRESHOLD = 1000

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val repository: AdminReportsRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        AdminReportsUiState(
            from = today.minusDays(REPORT_RANGE_DAYS - 1L).toString(),
            to = today.toString(),
        ),
    )
    val uiState: StateFlow<AdminReportsUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0

    init {
        load()
    }

    fun load() {
        val snapshot = _uiState.value
        val generation = ++loadGeneration
        _uiState.update { it.copy(content = UiState.Loading) }

        viewModelScope.launch {
            val threshold = snapshot.thresholdInput.toIntOrNull()
                ?.coerceIn(0, MAX_LOW_STOCK_THRESHOLD)
                ?: DEFAULT_LOW_STOCK_THRESHOLD
            val result: ApiResult<AdminReportContent> = when (snapshot.kind) {
                AdminReportKind.REVENUE ->
                    repository.revenue(snapshot.from, snapshot.to, snapshot.groupBy)
                        .map { AdminReportContent.Revenue(it) }

                AdminReportKind.TOP_PRODUCTS ->
                    repository.topProducts(snapshot.from, snapshot.to)
                        .map { AdminReportContent.TopProducts(it) }

                AdminReportKind.ORDERS_BY_STATUS ->
                    repository.ordersByStatus(snapshot.from, snapshot.to)
                        .map { AdminReportContent.OrdersByStatus(it) }

                AdminReportKind.LOW_STOCK ->
                    repository.lowStock(threshold)
                        .map { AdminReportContent.LowStock(threshold, it) }

                AdminReportKind.INVENTORY_COST ->
                    repository.inventoryCost(snapshot.from, snapshot.to)
                        .map { AdminReportContent.InventoryCost(it) }
            }

            if (generation != loadGeneration) return@launch
            _uiState.update {
                it.copy(
                    content = when (result) {
                        is ApiResult.Success -> UiState.Content(result.data)
                        is ApiResult.Failure -> UiState.Error(result.error)
                    },
                )
            }
        }
    }

    fun onSelectKind(kind: AdminReportKind) {
        if (kind == _uiState.value.kind) return
        _uiState.update { it.copy(kind = kind) }
        load()
    }

    fun onSelectGroupBy(groupBy: ReportGroupBy) {
        if (groupBy == _uiState.value.groupBy) return
        _uiState.update { it.copy(groupBy = groupBy) }
        if (_uiState.value.kind == AdminReportKind.REVENUE) load()
    }

    /** A range where "from" lands after "to" is invalid, so the other end follows the pick. */
    fun onFromSelected(isoDate: String) {
        val from = LocalDate.parse(isoDate)
        val to = LocalDate.parse(_uiState.value.to)
        _uiState.update {
            it.copy(
                from = from.toString(),
                to = (if (from.isAfter(to)) from else to).toString(),
            )
        }
        load()
    }

    fun onToSelected(isoDate: String) {
        val to = LocalDate.parse(isoDate)
        val from = LocalDate.parse(_uiState.value.from)
        _uiState.update {
            it.copy(
                from = (if (to.isBefore(from)) to else from).toString(),
                to = to.toString(),
            )
        }
        load()
    }

    fun onThresholdInputChange(input: String) {
        _uiState.update { it.copy(thresholdInput = input.filter(Char::isDigit).take(4)) }
    }

    fun onThresholdSubmit() {
        val threshold = _uiState.value.thresholdInput.toIntOrNull() ?: return
        val clamped = threshold.coerceIn(0, MAX_LOW_STOCK_THRESHOLD)
        _uiState.update { it.copy(thresholdInput = clamped.toString()) }
        if (_uiState.value.kind == AdminReportKind.LOW_STOCK) load()
    }

    fun retry() = load()
}

private const val REPORT_RANGE_DAYS = 30

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}
