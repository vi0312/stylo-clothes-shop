package com.stylo.shop.feature.admin.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.admin.model.AdminOrderRange
import com.stylo.shop.feature.admin.model.AdminOrderSummary
import com.stylo.shop.feature.admin.ui.AdminScaffold
import com.stylo.shop.feature.admin.ui.AdminTab
import com.stylo.shop.feature.orders.ui.OrderStatusBadge
import com.stylo.shop.core.ui.components.StyloEmptyState
import com.stylo.shop.core.ui.components.StyloErrorState
import com.stylo.shop.core.ui.components.StyloListSkeleton
import com.stylo.shop.core.ui.components.StyloSearchField
import com.stylo.shop.core.ui.components.StyloFilterChip

@Composable
fun AdminOrderListScreen(
    onOpenOrder: (Long) -> Unit,
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenReports: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminOrderListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Transitions applied in the detail screen must be visible in the list on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfLoaded()
        onPauseOrDispose { }
    }

    AdminScaffold(
        selectedTab = AdminTab.ORDERS,
        title = stringResource(R.string.admin_orders_title),
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
            StyloSearchField(
                query = state.searchQuery,
                hint = stringResource(R.string.admin_orders_search_hint),
                onQueryChange = viewModel::onSearchQueryChange,
                onSubmit = viewModel::onSearch,
                onClear = {
                    viewModel.onSearchQueryChange("")
                    viewModel.onSearch()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag(UiTags.AdminOrderSearch),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(AdminOrderStatusFilter.entries) { filter ->
                    StyloFilterChip(
                        label = statusFilterLabel(filter),
                        selected = state.statusFilter == filter,
                        onClick = { viewModel.onStatusFilterSelected(filter) },
                    )
                }
            }
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
            ) {
                items(AdminOrderRange.entries) { range ->
                    StyloFilterChip(
                        label = rangeLabel(range),
                        selected = state.range == range,
                        onClick = { viewModel.onRangeSelected(range) },
                    )
                }
            }

            when (val ordersState = state.ordersState) {
                is UiState.Loading -> StyloListSkeleton(rows = 6, thumbnail = false)
                is UiState.Error -> StyloErrorState(
                    error = ordersState.error,
                    onRetry = viewModel::onRetry,
                )
                is UiState.Content -> if (ordersState.data.isEmpty()) {
                    StyloEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = stringResource(R.string.admin_orders_empty),
                        body = stringResource(R.string.admin_orders_empty_hint),
                        modifier = Modifier.testTag(UiTags.AdminOrderEmpty),
                    )
                } else {
                    OrderRows(
                        orders = ordersState.data,
                        state = state,
                        onOpenOrder = onOpenOrder,
                        onLoadMore = viewModel::onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun OrderRows(
    orders: List<AdminOrderSummary>,
    state: AdminOrderListUiState,
    onOpenOrder: (Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTags.AdminOrderList),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(orders, key = { it.id }) { order ->
            AdminOrderRow(order = order, onClick = { onOpenOrder(order.id) })
        }
        if (state.isLoadingMore) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else if (state.loadMoreError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(ErrorMessages.resourceId(state.loadMoreError)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onLoadMore) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOrderRow(order: AdminOrderSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.AdminOrderRow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = order.code,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.admin_orders_row_meta,
                        order.customerName,
                        order.recipientPhone,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.admin_orders_row_items,
                        order.itemCount,
                    ) + " · " + Formatters.dateTime(order.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                OrderStatusBadge(order.status)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Formatters.money(order.total),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
private fun statusFilterLabel(filter: AdminOrderStatusFilter): String = stringResource(
    when (filter) {
        AdminOrderStatusFilter.ALL -> R.string.orders_filter_all
        AdminOrderStatusFilter.PENDING -> R.string.order_status_pending
        AdminOrderStatusFilter.CONFIRMED -> R.string.order_status_confirmed
        AdminOrderStatusFilter.SHIPPING -> R.string.order_status_shipping
        AdminOrderStatusFilter.DELIVERED -> R.string.order_status_delivered
        AdminOrderStatusFilter.CANCELLED -> R.string.order_status_cancelled
    },
)

@Composable
private fun rangeLabel(range: AdminOrderRange): String = stringResource(
    when (range) {
        AdminOrderRange.ALL -> R.string.admin_orders_range_all
        AdminOrderRange.TODAY -> R.string.admin_orders_filter_today
        AdminOrderRange.LAST_7 -> R.string.admin_orders_filter_7
        AdminOrderRange.LAST_30 -> R.string.admin_orders_filter_30
    },
)
