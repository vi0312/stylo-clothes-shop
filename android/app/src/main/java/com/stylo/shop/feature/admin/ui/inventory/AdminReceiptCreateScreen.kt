package com.stylo.shop.feature.admin.ui.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.e2eTagsAsResourceId
import com.stylo.shop.feature.admin.model.AdminVariantOption
import com.stylo.shop.feature.admin.model.RECEIPT_NOTE_MAX
import com.stylo.shop.feature.admin.model.RECEIPT_SUPPLIER_MAX
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.admin.ui.AdminErrorBanner
import com.stylo.shop.feature.admin.ui.AdminSectionCard
import com.stylo.shop.feature.admin.ui.adminFieldErrorText

@Composable
fun AdminReceiptCreateScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit,
    viewModel: AdminReceiptCreateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdReceiptId) {
        state.createdReceiptId?.let(onCreated)
    }

    AdminDetailScaffold(
        title = stringResource(R.string.admin_receipt_create_title),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (state.formError != null) {
                AdminErrorBanner(error = state.formError!!)
                Spacer(Modifier.height(12.dp))
            }

            AdminSectionCard(title = stringResource(R.string.admin_receipt_section_info)) {
                OutlinedTextField(
                    value = state.supplier,
                    onValueChange = viewModel::onSupplierChange,
                    label = { Text(stringResource(R.string.admin_receipt_supplier)) },
                    singleLine = true,
                    isError = state.supplierError != null,
                    supportingText = state.supplierError?.let { error ->
                        { Text(adminFieldErrorText(error, RECEIPT_SUPPLIER_MAX)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.AdminReceiptSupplier),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::onNoteChange,
                    label = { Text(stringResource(R.string.admin_receipt_note)) },
                    minLines = 2,
                    maxLines = 4,
                    isError = state.noteError != null,
                    supportingText = state.noteError?.let { error ->
                        { Text(adminFieldErrorText(error, RECEIPT_NOTE_MAX)) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.AdminReceiptNote),
                )
            }

            Spacer(Modifier.height(16.dp))

            AdminSectionCard(title = stringResource(R.string.admin_receipt_section_lines)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.admin_receipt_lines_count, state.lines.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = viewModel::openVariantPicker,
                        modifier = Modifier.testTag(UiTags.AdminReceiptAddLine),
                    ) {
                        Text(stringResource(R.string.admin_receipt_add_line))
                    }
                }

                state.lines.forEachIndexed { index, line ->
                    ReceiptLineCard(
                        index = index,
                        line = line,
                        onQuantityChange = viewModel::onQuantityChange,
                        onUnitCostChange = viewModel::onUnitCostChange,
                        onRemove = viewModel::removeLine,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (state.itemsError) {
                    Text(
                        text = stringResource(R.string.admin_receipt_items_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(UiTags.AdminReceiptItemsError),
                    )
                } else if (state.lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.admin_receipt_picker_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.admin_receipt_total),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = Formatters.money(state.totalCost),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = viewModel::save,
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTags.AdminReceiptSubmit),
            ) {
                if (state.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.admin_receipt_submit))
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    val picker = state.picker
    if (picker != null) {
        VariantPickerDialog(
            picker = picker,
            onQueryChange = viewModel::onPickerQueryChange,
            onSelect = viewModel::selectVariant,
            onRetry = viewModel::retryPickerSearch,
            onDismiss = viewModel::dismissVariantPicker,
        )
    }
}

@Composable
private fun ReceiptLineCard(
    index: Int,
    line: ReceiptLineDraft,
    onQuantityChange: (Int, String) -> Unit,
    onUnitCostChange: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = line.variant.productName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = line.variant.sku + " · " + line.variant.size + " · " +
                            line.variant.color,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.admin_receipt_line_stock,
                            line.variant.stock,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.testTag(UiTags.AdminReceiptRemoveLine),
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.admin_receipt_remove_line),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = line.quantity,
                    onValueChange = { onQuantityChange(index, it) },
                    label = { Text(stringResource(R.string.admin_receipt_quantity)) },
                    singleLine = true,
                    isError = line.quantityError != null,
                    supportingText = line.quantityError?.let { error ->
                        { Text(adminFieldErrorText(error)) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UiTags.AdminReceiptQuantity),
                )
                OutlinedTextField(
                    value = line.unitCost,
                    onValueChange = { onUnitCostChange(index, it) },
                    label = { Text(stringResource(R.string.admin_receipt_unit_cost)) },
                    singleLine = true,
                    isError = line.unitCostError != null,
                    supportingText = line.unitCostError?.let { error ->
                        { Text(adminFieldErrorText(error)) }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(UiTags.AdminReceiptUnitCost),
                )
            }

            Text(
                text = stringResource(
                    R.string.admin_receipt_line_total,
                    Formatters.money(line.lineTotal),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun VariantPickerDialog(
    picker: VariantPickerUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (AdminVariantOption) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.e2eTagsAsResourceId(),
        title = { Text(stringResource(R.string.admin_receipt_picker_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = picker.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.admin_receipt_picker_search)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (picker.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = stringResource(R.string.action_clear),
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(UiTags.AdminReceiptPickerSearch),
                )

                Spacer(Modifier.height(8.dp))

                when (val results = picker.results) {
                    is UiState.Loading -> Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    is UiState.Error -> Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(ErrorMessages.resourceId(results.error)),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                    is UiState.Content -> if (results.data.isEmpty()) {
                        Text(
                            text = stringResource(R.string.admin_receipt_picker_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(results.data, key = { it.id }) { option ->
                                VariantPickerRow(option = option, onClick = { onSelect(option) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun VariantPickerRow(option: AdminVariantOption, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(UiTags.AdminReceiptPickerRow),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = option.productName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = option.sku + " · " + option.size + " · " + option.color,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.admin_products_stock, option.stock),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
