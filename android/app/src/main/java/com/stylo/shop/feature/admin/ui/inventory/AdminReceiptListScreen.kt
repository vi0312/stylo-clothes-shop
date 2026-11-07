package com.stylo.shop.feature.admin.ui.inventory

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import com.stylo.shop.feature.admin.model.AdminReceiptSummary
import com.stylo.shop.feature.admin.ui.AdminEmptyState
import com.stylo.shop.feature.admin.ui.AdminErrorState
import com.stylo.shop.feature.admin.ui.AdminListSkeleton
import com.stylo.shop.feature.admin.ui.AdminScaffold
import com.stylo.shop.feature.admin.ui.AdminTab

@Composable
fun AdminReceiptListScreen(
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onLogout: () -> Unit,
    onAdd: () -> Unit,
    onOpenReceipt: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminReceiptListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // A receipt created in the form must appear in the list on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfLoaded()
        onPauseOrDispose { }
    }

    AdminScaffold(
        selectedTab = AdminTab.INVENTORY,
        title = stringResource(R.string.admin_receipts_title),
        onSelectTab = onSelectTab,
        onOpenTaxonomy = onOpenTaxonomy,
        onLogout = onLogout,
        modifier = modifier,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.receiptsState is UiState.Loading) {
                        ""
                    } else {
                        stringResource(R.string.admin_receipts_count, state.total)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onAdd,
                    modifier = Modifier.testTag(UiTags.AdminReceiptAdd),
                ) {
                    Text(stringResource(R.string.admin_receipts_add))
                }
            }

            when (val receiptsState = state.receiptsState) {
                is UiState.Loading -> AdminListSkeleton(rows = 6, thumbnail = false)
                is UiState.Error -> AdminErrorState(
                    error = receiptsState.error,
                    onRetry = viewModel::onRetry,
                )
                is UiState.Content -> if (receiptsState.data.isEmpty()) {
                    AdminEmptyState(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = stringResource(R.string.admin_receipts_empty),
                        body = stringResource(R.string.admin_receipts_empty_hint),
                        actionLabel = stringResource(R.string.admin_receipts_add),
                        onAction = onAdd,
                        modifier = Modifier.testTag(UiTags.AdminReceiptEmpty),
                    )
                } else {
                    ReceiptRows(
                        receipts = receiptsState.data,
                        state = state,
                        onOpenReceipt = onOpenReceipt,
                        onLoadMore = viewModel::loadMore,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiptRows(
    receipts: List<AdminReceiptSummary>,
    state: AdminReceiptListUiState,
    onOpenReceipt: (Long) -> Unit,
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

    LaunchedEffect(shouldLoadMore, state.hasMore, state.isLoadingMore, receipts.size) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTags.AdminReceiptList),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(receipts, key = { it.id }) { receipt ->
            ReceiptRow(receipt = receipt, onClick = { onOpenReceipt(receipt.id) })
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
private fun ReceiptRow(receipt: AdminReceiptSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.AdminReceiptRow),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = receipt.code,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.admin_receipts_row_meta,
                        Formatters.dateTime(receipt.createdAt),
                        receipt.itemCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = receipt.supplier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Formatters.money(receipt.totalCost),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.admin_receipts_row_creator,
                        receipt.createdBy.fullName,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
