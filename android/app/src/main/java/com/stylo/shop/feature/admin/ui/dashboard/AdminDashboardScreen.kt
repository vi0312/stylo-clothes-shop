package com.stylo.shop.feature.admin.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.admin.model.AdminOrderSummary
import com.stylo.shop.feature.admin.model.DashboardBlock
import com.stylo.shop.feature.admin.model.DashboardKpis
import com.stylo.shop.feature.admin.model.AdminDashboardUiState
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import com.stylo.shop.feature.admin.model.dashboardChartSeries
import com.stylo.shop.feature.admin.ui.AdminDatePickerDialog
import com.stylo.shop.feature.admin.ui.AdminErrorBanner
import com.stylo.shop.feature.admin.ui.AdminRevenueChart
import com.stylo.shop.feature.admin.ui.AdminScaffold
import com.stylo.shop.feature.admin.ui.AdminTab
import com.stylo.shop.feature.orders.ui.OrderStatusBadge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.stylo.shop.core.ui.components.StyloCard
import com.stylo.shop.core.ui.components.StyloErrorState
import com.stylo.shop.core.ui.components.StyloListSkeleton
import com.stylo.shop.core.ui.theme.statusColors
import androidx.compose.ui.platform.LocalDensity

private enum class DashboardDateField { FROM, TO }

private val dashboardDatePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

@Composable
fun AdminDashboardScreen(
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenOrder: (Long) -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dateField by remember { mutableStateOf<DashboardDateField?>(null) }

    AdminScaffold(
        selectedTab = AdminTab.DASHBOARD,
        title = stringResource(R.string.admin_tab_dashboard),
        onSelectTab = onSelectTab,
        onOpenTaxonomy = onOpenTaxonomy,
        onOpenUsers = onOpenUsers,
        onOpenReports = onOpenReports,
        onLogout = onLogout,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            DashboardRangeRow(
                state = state,
                onPickFrom = { dateField = DashboardDateField.FROM },
                onPickTo = { dateField = DashboardDateField.TO },
            )
            when {
                state.isLoading -> StyloListSkeleton(
                    rows = 4,
                    thumbnail = false,
                    modifier = Modifier.padding(20.dp),
                )

                state.isFullyFailed -> state.firstError?.let { error ->
                    StyloErrorState(error = error, onRetry = viewModel::load)
                }

                else -> DashboardBody(
                    state = state,
                    onOpenOrder = onOpenOrder,
                    onRetry = viewModel::load,
                )
            }
        }
    }

    dateField?.let { field ->
        AdminDatePickerDialog(
            initialIsoDate = if (field == DashboardDateField.FROM) state.from else state.to,
            onDismiss = { dateField = null },
            onConfirm = { isoDate ->
                if (field == DashboardDateField.FROM) {
                    viewModel.onFromSelected(isoDate)
                } else {
                    viewModel.onToSelected(isoDate)
                }
                dateField = null
            },
        )
    }
}

@Composable
private fun DashboardRangeRow(
    state: AdminDashboardUiState,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onPickFrom,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.admin_reports_from, dashboardDateLabel(state.from)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onPickTo,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = stringResource(R.string.admin_reports_to, dashboardDateLabel(state.to)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DashboardBody(
    state: AdminDashboardUiState,
    onOpenOrder: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { DashboardKpiGrid(kpis = state.kpis) }
        item { RevenueBlock(block = state.revenue, onRetry = onRetry) }
        item { TopProductsBlock(block = state.topProducts, onRetry = onRetry) }
        item {
            RecentOrdersBlock(
                block = state.recentOrders,
                onOpenOrder = onOpenOrder,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun DashboardKpiGrid(kpis: DashboardKpis) {
    val missing = stringResource(R.string.admin_dashboard_missing_value)
    // Two columns only while the numbers actually fit: at large font scales a card is too narrow
    // to hold "36.368.000 ₫", and wrapping inside the digits reads as a wrong number.
    val singleColumn = LocalDensity.current.fontScale >= 1.3f
    val cards = listOf(
        KpiSpec(
            title = stringResource(R.string.admin_dashboard_kpi_revenue),
            value = kpis.revenue?.let { Formatters.money(it) } ?: missing,
            tag = UiTags.AdminDashboardKpiRevenue,
            deltaPercent = kpis.revenueDeltaPercent,
        ),
        KpiSpec(
            title = stringResource(R.string.admin_dashboard_kpi_orders),
            value = kpis.orders?.toString() ?: missing,
            tag = UiTags.AdminDashboardKpiOrders,
            deltaPercent = kpis.ordersDeltaPercent,
        ),
        KpiSpec(
            title = stringResource(R.string.admin_dashboard_kpi_average),
            value = kpis.averageOrderValue?.let { Formatters.money(it) } ?: missing,
            tag = UiTags.AdminDashboardKpiAverage,
        ),
        KpiSpec(
            title = stringResource(R.string.admin_dashboard_kpi_low_stock),
            value = kpis.lowStockVariants?.toString() ?: missing,
            tag = UiTags.AdminDashboardKpiLowStock,
        ),
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (singleColumn) {
            cards.forEach { card ->
                KpiCard(
                    title = card.title,
                    value = card.value,
                    tag = card.tag,
                    deltaPercent = card.deltaPercent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            cards.chunked(2).forEach { rowCards ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowCards.forEach { card ->
                        KpiCard(
                            title = card.title,
                            value = card.value,
                            tag = card.tag,
                            deltaPercent = card.deltaPercent,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

private data class KpiSpec(
    val title: String,
    val value: String,
    val tag: String,
    val deltaPercent: Int? = null,
)

@Composable
private fun KpiCard(
    title: String,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    deltaPercent: Int? = null,
) {
    Surface(
        modifier = modifier.testTag(tag),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // A flat period is not news: no arrow, no "0%".
            if (deltaPercent != null && deltaPercent != 0) {
                Spacer(Modifier.height(4.dp))
                val positive = deltaPercent >= 0
                Text(
                    text = stringResource(
                        if (positive) {
                            R.string.admin_dashboard_delta_up
                        } else {
                            R.string.admin_dashboard_delta_down
                        },
                        kotlin.math.abs(deltaPercent),
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (positive) {
                        MaterialTheme.statusColors.success
                    } else {
                        MaterialTheme.statusColors.warning
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RevenueBlock(
    block: DashboardBlock<RevenueReport>,
    onRetry: () -> Unit,
) {
    StyloCard(title = stringResource(R.string.admin_dashboard_chart_title)) {
        when (block) {
            DashboardBlock.Loading -> BlockLoading()
            is DashboardBlock.Failed -> BlockError(error = block.error, onRetry = onRetry)
            is DashboardBlock.Ready -> {
                val report = block.value
                val series = dashboardChartSeries(report)
                if (report.totalOrders == 0 || series.isEmpty()) {
                    BlockEmpty(text = stringResource(R.string.admin_dashboard_chart_empty))
                } else {
                    AdminRevenueChart(
                        series = series,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag(UiTags.AdminDashboardChart),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopProductsBlock(
    block: DashboardBlock<List<TopProductRow>>,
    onRetry: () -> Unit,
) {
    StyloCard(title = stringResource(R.string.admin_dashboard_top_products_title)) {
        when (block) {
            DashboardBlock.Loading -> BlockLoading()
            is DashboardBlock.Failed -> BlockError(error = block.error, onRetry = onRetry)
            is DashboardBlock.Ready -> {
                val items = block.value.take(3)
                if (items.isEmpty()) {
                    BlockEmpty(text = stringResource(R.string.admin_dashboard_top_products_empty))
                } else {
                    Column(modifier = Modifier.testTag(UiTags.AdminDashboardTopProducts)) {
                        items.forEachIndexed { index, item ->
                            if (index > 0) Spacer(Modifier.height(12.dp))
                            TopProductRow(index = index + 1, item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopProductRow(
    index: Int,
    item: TopProductRow,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = index.toString(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.admin_dashboard_product_sold, item.quantitySold),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Formatters.money(item.revenue),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun RecentOrdersBlock(
    block: DashboardBlock<List<AdminOrderSummary>>,
    onOpenOrder: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    StyloCard(title = stringResource(R.string.admin_dashboard_recent_orders_title)) {
        when (block) {
            DashboardBlock.Loading -> BlockLoading()
            is DashboardBlock.Failed -> BlockError(error = block.error, onRetry = onRetry)
            is DashboardBlock.Ready -> {
                val orders = block.value.take(3)
                if (orders.isEmpty()) {
                    BlockEmpty(text = stringResource(R.string.admin_orders_empty))
                } else {
                    Column(modifier = Modifier.testTag(UiTags.AdminDashboardRecentOrders)) {
                        orders.forEachIndexed { index, order ->
                            if (index > 0) Spacer(Modifier.height(4.dp))
                            RecentOrderRow(order = order, onClick = { onOpenOrder(order.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentOrderRow(
    order: AdminOrderSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = order.code,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = order.customerName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Formatters.money(order.total),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            OrderStatusBadge(order.status)
        }
    }
}

@Composable
private fun BlockLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun BlockEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BlockError(
    error: ApiError,
    onRetry: () -> Unit,
) {
    Column {
        AdminErrorBanner(error = error)
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

private fun dashboardDateLabel(isoDate: String): String =
    LocalDate.parse(isoDate).format(dashboardDatePattern)
