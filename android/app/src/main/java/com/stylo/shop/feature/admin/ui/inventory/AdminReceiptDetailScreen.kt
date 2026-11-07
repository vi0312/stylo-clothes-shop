package com.stylo.shop.feature.admin.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.admin.model.AdminReceiptDetail
import com.stylo.shop.feature.admin.model.AdminReceiptItem
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.admin.ui.AdminErrorState
import com.stylo.shop.feature.admin.ui.AdminSectionCard

@Composable
fun AdminReceiptDetailScreen(
    onBack: () -> Unit,
    viewModel: AdminReceiptDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    AdminDetailScaffold(
        title = stringResource(R.string.admin_receipt_detail_title),
        onBack = onBack,
    ) { padding ->
        when (val receiptState = state.receiptState) {
            is UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is UiState.Error -> AdminErrorState(
                error = receiptState.error,
                onRetry = viewModel::load,
                modifier = Modifier.padding(padding),
            )
            is UiState.Content -> ReceiptDetailContent(
                receipt = receiptState.data,
                padding = padding,
            )
        }
    }
}

@Composable
private fun ReceiptDetailContent(
    receipt: AdminReceiptDetail,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .testTag(UiTags.AdminReceiptDetailList),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AdminSectionCard(title = stringResource(R.string.admin_receipt_section_info)) {
                InfoRow(
                    label = stringResource(R.string.admin_receipt_code_label),
                    value = receipt.code,
                )
                InfoRow(
                    label = stringResource(R.string.admin_receipt_created_at),
                    value = Formatters.dateTime(receipt.createdAt),
                )
                InfoRow(
                    label = stringResource(R.string.admin_receipt_supplier),
                    value = receipt.supplier,
                )
                InfoRow(
                    label = stringResource(R.string.admin_receipt_created_by),
                    value = receipt.createdBy.fullName,
                )
                if (receipt.note != null) {
                    InfoRow(
                        label = stringResource(R.string.admin_receipt_note),
                        value = receipt.note,
                    )
                }
            }
        }

        item {
            AdminSectionCard(title = stringResource(R.string.admin_receipt_section_lines)) {
                receipt.items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    }
                    ReceiptItemRow(item)
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.admin_receipt_total),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.money(receipt.totalCost),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ReceiptItemRow(item: AdminReceiptItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.sku + " · " + item.size + " · " + item.color,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.quantity.toString() + " × " + Formatters.money(item.unitCost),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Formatters.money(item.lineTotal),
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(2f),
        )
    }
}
