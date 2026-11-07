package com.stylo.shop.feature.admin.ui.reports

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
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
import com.stylo.shop.feature.admin.model.RevenuePoint
import com.stylo.shop.feature.admin.model.RevenueReport
import com.stylo.shop.feature.admin.model.TopProductRow
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.admin.ui.AdminEmptyState
import com.stylo.shop.feature.admin.ui.AdminErrorState
import com.stylo.shop.feature.admin.ui.AdminListSkeleton
import com.stylo.shop.feature.admin.ui.AdminSectionCard
import com.stylo.shop.feature.orders.ui.OrderStatusBadge
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val reportDatePattern = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("vi-VN"))
private val dayPeriodPattern = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("vi-VN"))
private val monthPeriodPattern = DateTimeFormatter.ofPattern("MM/yyyy", Locale.forLanguageTag("vi-VN"))

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
                    UiState.Loading -> AdminListSkeleton(
                        rows = 6,
                        thumbnail = false,
                        modifier = Modifier.padding(20.dp),
                    )

                    is UiState.Error -> AdminErrorState(
                        error = content.error,
                        onRetry = viewModel::retry,
                    )

                    is UiState.Content -> ReportBody(content.data)
                }
            }
        }
    }

    dateField?.let { field ->
        ReportDatePickerDialog(
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
            FilterChip(
                selected = kind == entry,
                onClick = { onSelect(entry) },
                label = { Text(stringResource(entry.labelRes)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportDatePickerDialog(
    initialIsoDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialMillis = LocalDate.parse(initialIsoDate).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date.toString())
                },
            ) {
                Text(stringResource(R.string.admin_reports_choose))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(state = pickerState)
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
        AdminSectionCard(title = stringResource(R.string.admin_reports_revenue_title)) {
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
        }
        Spacer(Modifier.height(16.dp))
        RevenueChart(
            series = report.series,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RevenueChart(
    series: List<RevenuePoint>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chartDescription = stringResource(
        R.string.admin_reports_chart_description,
        series.joinToString(separator = "; ") { point ->
            context.getString(
                R.string.admin_reports_chart_point,
                periodLabel(point.period),
                Formatters.money(point.revenue),
                point.orders,
            )
        },
    )
    val barColor = MaterialTheme.colorScheme.secondary
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val peakLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = modifier.semantics { contentDescription = chartDescription }) {
        if (series.isEmpty()) return@Canvas

        val axisLabelSpace = 18.dp.toPx()
        val baseLine = size.height - axisLabelSpace
        val maxRevenue = series.maxOf { it.revenue }.coerceAtLeast(1L)
        // Keep the end bars clear of the platform scroll indicator hugging the right edge.
        val sideInset = 4.dp.toPx()
        val drawWidth = size.width - sideInset * 2
        val slot = drawWidth / series.size
        val barWidth = (slot * 0.55f)
            .coerceAtLeast(2.dp.toPx())
            .coerceAtMost(28.dp.toPx())
        val peakIndex = series.indices.maxBy { series[it].revenue }
        val peakLayout = if (series[peakIndex].revenue > 0) {
            textMeasurer.measure(
                text = AnnotatedString(Formatters.money(series[peakIndex].revenue)),
                style = peakLabelStyle,
            )
        } else {
            null
        }
        val plotTop = (peakLayout?.size?.height ?: 0).toFloat() + 4.dp.toPx()
        val plotHeight = baseLine - plotTop

        listOf(0f, 0.5f, 1f).forEach { fraction ->
            val y = baseLine - plotHeight * fraction
            drawLine(
                color = axisColor,
                start = Offset(sideInset, y),
                end = Offset(size.width - sideInset, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        series.forEachIndexed { index, point ->
            val barHeight = (point.revenue.toFloat() / maxRevenue) * (plotHeight - 2.dp.toPx())
            if (barHeight > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(
                        sideInset + index * slot + (slot - barWidth) / 2f,
                        baseLine - barHeight,
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }

        if (peakLayout != null) {
            val peakBarLeft = sideInset + peakIndex * slot + (slot - barWidth) / 2f
            val peakX = (peakBarLeft + barWidth - peakLayout.size.width)
                .coerceIn(sideInset, size.width - sideInset - peakLayout.size.width)
            drawText(peakLayout, topLeft = Offset(peakX, 0f))
        }

        val labelIndices = listOf(0, series.lastIndex / 2, series.lastIndex).distinct()
        labelIndices.forEach { index ->
            val layout = textMeasurer.measure(
                text = AnnotatedString(periodLabel(series[index].period)),
                style = labelStyle,
            )
            val x = when (index) {
                0 -> sideInset
                series.lastIndex -> size.width - sideInset - layout.size.width
                else -> sideInset + index * slot + (slot - layout.size.width) / 2f
            }
            drawText(layout, topLeft = Offset(x, baseLine + 2.dp.toPx()))
        }
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
        AdminEmptyState(
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
        AdminSectionCard(title = stringResource(R.string.admin_reports_cost_title)) {
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
    AdminEmptyState(
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

private fun periodLabel(period: String): String =
    if (period.length == 7) {
        YearMonth.parse(period).format(monthPeriodPattern)
    } else {
        LocalDate.parse(period).format(dayPeriodPattern)
    }
