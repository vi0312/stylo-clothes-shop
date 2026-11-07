package com.stylo.shop.feature.admin.ui.reports

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.admin.model.InventoryCostReport
import com.stylo.shop.feature.admin.model.LowStockRow
import com.stylo.shop.feature.admin.model.OrdersByStatusRow
import com.stylo.shop.feature.admin.model.ReportGroupBy
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import com.stylo.shop.feature.admin.ui.AdminDatePickerDialog
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.admin.ui.AdminRevenueChart
import com.stylo.shop.feature.orders.ui.OrderStatusBadge
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.stylo.shop.core.ui.components.StyloCard
import com.stylo.shop.core.ui.components.StyloEmptyState
import com.stylo.shop.core.ui.components.StyloErrorState
import com.stylo.shop.core.ui.components.StyloListSkeleton
import com.stylo.shop.core.ui.components.StyloFilterChip

private val reportDatePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))

private enum class ReportDateField { FROM, TO }

@Composable
fun AdminReportsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dateField by remember { mutableStateOf<ReportDateField?>(null) }

    AdminDetailScaffold(
        title = stringResource(R.string.admin_reports_title),
        onBack = onBack,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ReportKindChips(kind = state.kind, onSelect = viewModel::onSelectKind)
            ReportFilters(
                state = state,
                onSelectGroupBy = viewModel::onSelectGroupBy,
                onPickFrom = { dateField = ReportDateField.FROM },
                onPickTo = { dateField = ReportDateField.TO },
                onThresholdInputChange = viewModel::onThresholdInputChange,
                onThresholdSubmit = viewModel::onThresholdSubmit,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (val content = state.content) {
                    UiState.Loading -> StyloListSkeleton(
                        rows = 6,
                        thumbnail = false,
                        modifier = Modifier.padding(20.dp),
                    )

                    is UiState.Error -> StyloErrorState(
                        error = content.error,
                        onRetry = viewModel::retry,
                    )

                    is UiState.Content -> ReportBody(content.data)
                }
            }
        }
    }

    dateField?.let { field ->
        AdminDatePickerDialog(
            initialIsoDate = if (field == ReportDateField.FROM) state.from else state.to,
            onDismiss = { dateField = null },
            onConfirm = { isoDate ->
                if (field == ReportDateField.FROM) {
                    viewModel.onFromSelected(isoDate)
                } else {
                    viewModel.onToSelected(isoDate)
                }
                dateField = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportKindChips(
    kind: AdminReportKind,
    onSelect: (AdminReportKind) -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AdminReportKind.entries.forEach { entry ->
            StyloFilterChip(
                label = stringResource(entry.labelRes),
                selected = kind == entry,
                onClick = { onSelect(entry) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportFilters(
    state: AdminReportsUiState,
    onSelectGroupBy: (ReportGroupBy) -> Unit,
    onPickFrom: () -> Unit,
    onPickTo: () -> Unit,
    onThresholdInputChange: (String) -> Unit,
    onThresholdSubmit: () -> Unit,
) {
    if (state.kind == AdminReportKind.LOW_STOCK) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.admin_reports_threshold),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.thresholdInput,
                onValueChange = onThresholdInputChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onThresholdSubmit() }),
                modifier = Modifier.width(96.dp),
            )
            Button(onClick = onThresholdSubmit) {
                Text(stringResource(R.string.admin_reports_apply))
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onPickFrom,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.admin_reports_from, reportDateLabel(state.from)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(
                onClick = onPickTo,
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.admin_reports_to, reportDateLabel(state.to)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (state.kind == AdminReportKind.REVENUE) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        ) {
            ReportGroupBy.entries.forEachIndexed { index, groupBy ->
                SegmentedButton(
                    selected = state.groupBy == groupBy,
                    onClick = { onSelectGroupBy(groupBy) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ReportGroupBy.entries.size,
                    ),
                ) {
                    Text(stringResource(groupBy.labelRes))
                }
            }
        }
    }
}

@Composable
private fun ReportBody(content: AdminReportContent) {
    when (content) {
        is AdminReportContent.Revenue -> RevenueReportBody(content.report)
        is AdminReportContent.TopProducts -> TopProductsTable(content.items)
        is AdminReportContent.OrdersByStatus -> OrdersByStatusTable(content.items)
        is AdminReportContent.LowStock -> LowStockTable(content.threshold, content.items)
        is AdminReportContent.InventoryCost -> InventoryCostBody(content.report)
    }
}

@Composable
private fun RevenueReportBody(report: RevenueReport) {
    if (report.totalOrders == 0) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            NoDataState()
        }
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        StyloCard(title = stringResource(R.string.admin_reports_revenue_title)) {
            Text(
                text = Formatters.money(report.totalRevenue),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.admin_reports_revenue_orders, report.totalOrders),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.series.size > MAX_CHART_COLUMNS) {
                Text(
                    text = stringResource(R.string.admin_reports_chart_capped, MAX_CHART_COLUMNS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        // docs/ui.md caps the chart at 14 columns; a longer range keeps its latest 14 periods.
        AdminRevenueChart(
            series = report.series.takeLast(MAX_CHART_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}



@Composable
private fun TopProductsTable(items: List<TopProductRow>) {
    if (items.isEmpty()) {
        NoDataState()
        return
    }
    val productHeader = stringResource(R.string.admin_reports_col_product)
    val quantityHeader = stringResource(R.string.admin_reports_col_quantity)
    val revenueHeader = stringResource(R.string.admin_reports_col_revenue)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        stickyTableHeader(productHeader, quantityHeader, revenueHeader)
        itemsIndexed(items, key = { _, item -> item.productId }) { _, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.quantitySold.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(NumberOfColumn),
                )
                Text(
                    text = Formatters.money(item.revenue),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(MoneyColumn),
                )
            }
            RowDivider()
        }
    }
}

@Composable
private fun OrdersByStatusTable(items: List<OrdersByStatusRow>) {
    val statusHeader = stringResource(R.string.admin_reports_col_status)
    val ordersHeader = stringResource(R.string.admin_reports_col_orders)
    val totalHeader = stringResource(R.string.admin_reports_col_total)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        stickyTableHeader(statusHeader, ordersHeader, totalHeader)
        itemsIndexed(items, key = { _, item -> item.status.apiValue }) { _, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    OrderStatusBadge(item.status)
                }
                Text(
                    text = item.count.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(NumberOfColumn),
                )
                Text(
                    text = Formatters.money(item.total),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(MoneyColumn),
                )
            }
            RowDivider()
        }
    }
}

@Composable
private fun LowStockTable(threshold: Int, items: List<LowStockRow>) {
    if (items.isEmpty()) {
        StyloEmptyState(
            icon = Icons.Filled.Warning,
            title = stringResource(R.string.admin_reports_low_stock_empty_title),
            body = stringResource(R.string.admin_reports_low_stock_empty_body, threshold),
        )
        return
    }
    val productHeader = stringResource(R.string.admin_reports_col_product)
    val skuHeader = stringResource(R.string.admin_reports_col_sku)
    val stockHeader = stringResource(R.string.admin_reports_col_stock)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        stickyTableHeader(
            first = productHeader,
            second = skuHeader,
            third = stockHeader,
            secondWidth = SkuColumn,
            thirdWidth = NumberOfColumn,
        )
        itemsIndexed(items, key = { _, item -> item.variantId }) { _, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${item.size} · ${item.color}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = item.sku,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(SkuColumn),
                )
                Text(
                    text = item.stock.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.statusColors.warning,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(NumberOfColumn),
                )
            }
            RowDivider()
        }
    }
}

@Composable
private fun InventoryCostBody(report: InventoryCostReport) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        StyloCard(title = stringResource(R.string.admin_reports_cost_title)) {
            CostRow(
                label = stringResource(R.string.admin_reports_cost_receipts),
                value = report.receiptsCost,
            )
            CostRow(
                label = stringResource(R.string.admin_reports_cost_cogs),
                value = report.cogs,
            )
            CostRow(
                label = stringResource(R.string.admin_reports_cost_revenue),
                value = report.revenue,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CostRow(
                label = stringResource(R.string.admin_reports_cost_profit),
                value = report.grossProfit,
                color = if (report.grossProfit >= 0) {
                    MaterialTheme.statusColors.success
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CostRow(
    label: String,
    value: Long,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Formatters.money(value),
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun NoDataState() {
    StyloEmptyState(
        icon = Icons.Filled.Info,
        title = stringResource(R.string.admin_reports_empty_title),
        body = stringResource(R.string.admin_reports_empty_body),
    )
}

@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.stickyTableHeader(
    first: String,
    second: String,
    third: String,
    secondWidth: Dp = NumberOfColumn,
    thirdWidth: Dp = MoneyColumn,
) {
    stickyHeader {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    TableHeaderCell(first, Modifier.weight(1f), TextAlign.Start)
                    TableHeaderCell(second, Modifier.width(secondWidth), TextAlign.End)
                    TableHeaderCell(third, Modifier.width(thirdWidth), TextAlign.End)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun TableHeaderCell(
    text: String,
    modifier: Modifier,
    textAlign: TextAlign,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

private val NumberOfColumn = 56.dp
private val MoneyColumn = 124.dp
private val SkuColumn = 96.dp

private val AdminReportKind.labelRes: Int
    get() = when (this) {
        AdminReportKind.REVENUE -> R.string.admin_reports_kind_revenue
        AdminReportKind.TOP_PRODUCTS -> R.string.admin_reports_kind_top_products
        AdminReportKind.ORDERS_BY_STATUS -> R.string.admin_reports_kind_orders
        AdminReportKind.LOW_STOCK -> R.string.admin_reports_kind_low_stock
        AdminReportKind.INVENTORY_COST -> R.string.admin_reports_kind_cost
    }

private val ReportGroupBy.labelRes: Int
    get() = when (this) {
        ReportGroupBy.DAY -> R.string.admin_reports_group_day
        ReportGroupBy.MONTH -> R.string.admin_reports_group_month
    }

private fun reportDateLabel(isoDate: String): String =
    LocalDate.parse(isoDate).format(reportDatePattern)

private const val MAX_CHART_COLUMNS = 14
