package com.stylo.shop.feature.admin.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.data.AdminOrdersRepository
import com.stylo.shop.feature.admin.data.AdminReportsRepository
import com.stylo.shop.feature.admin.model.AdminDashboardUiState
import com.stylo.shop.feature.admin.model.AdminOrderQuery
import com.stylo.shop.feature.admin.model.DASHBOARD_LOW_STOCK_THRESHOLD
import com.stylo.shop.feature.admin.model.DASHBOARD_RANGE_DAYS
import com.stylo.shop.feature.admin.model.DASHBOARD_RECENT_ORDERS
import com.stylo.shop.feature.admin.model.DASHBOARD_TOP_PRODUCTS
import com.stylo.shop.feature.admin.model.DashboardBlock
import com.stylo.shop.feature.admin.model.ReportGroupBy
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AdminDashboardViewModel @Inject constructor(
    private val reportsRepository: AdminReportsRepository,
    private val ordersRepository: AdminOrdersRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    private val _uiState = MutableStateFlow(
        AdminDashboardUiState(
            from = today.minusDays(DASHBOARD_RANGE_DAYS - 1L).toString(),
            to = today.toString(),
        ),
    )
    val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

    private var loadGeneration = 0

    init {
        load()
    }

    fun load() {
        val snapshot = _uiState.value
        val generation = ++loadGeneration
        _uiState.update {
            it.copy(
                revenue = DashboardBlock.Loading,
                previousRevenue = DashboardBlock.Loading,
                topProducts = DashboardBlock.Loading,
                lowStock = DashboardBlock.Loading,
                recentOrders = DashboardBlock.Loading,
            )
        }

        viewModelScope.launch {
            val result = reportsRepository.revenue(
                from = snapshot.from,
                to = snapshot.to,
                groupBy = ReportGroupBy.DAY,
            )
            if (generation != loadGeneration) return@launch
            _uiState.update { it.copy(revenue = result.toBlock()) }
        }
        viewModelScope.launch {
            // Same-length window right before the selected one, so KPI deltas compare like periods.
            val from = LocalDate.parse(snapshot.from)
            val to = LocalDate.parse(snapshot.to)
            val days = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
            val result = reportsRepository.revenue(
                from = from.minusDays(days).toString(),
                to = from.minusDays(1).toString(),
                groupBy = ReportGroupBy.DAY,
            )
            if (generation != loadGeneration) return@launch
            _uiState.update { it.copy(previousRevenue = result.toBlock()) }
        }
        viewModelScope.launch {
            val result = reportsRepository.topProducts(
                from = snapshot.from,
                to = snapshot.to,
                limit = DASHBOARD_TOP_PRODUCTS,
            )
            if (generation != loadGeneration) return@launch
            _uiState.update { it.copy(topProducts = result.toBlock()) }
        }
        viewModelScope.launch {
            val result = reportsRepository.lowStock(DASHBOARD_LOW_STOCK_THRESHOLD)
            if (generation != loadGeneration) return@launch
            _uiState.update { it.copy(lowStock = result.toBlock()) }
        }
        viewModelScope.launch {
            val result = ordersRepository.listOrders(
                AdminOrderQuery(
                    from = snapshot.from,
                    to = snapshot.to,
                    limit = DASHBOARD_RECENT_ORDERS,
                ),
            ).map { it.items }
            if (generation != loadGeneration) return@launch
            _uiState.update { it.copy(recentOrders = result.toBlock()) }
        }
    }

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
}

private fun <T> ApiResult<T>.toBlock(): DashboardBlock<T> = when (this) {
    is ApiResult.Success -> DashboardBlock.Ready(data)
    is ApiResult.Failure -> DashboardBlock.Failed(error)
}

private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}
