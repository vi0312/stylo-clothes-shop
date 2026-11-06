package com.stylo.shop.feature.admin.ui.taxonomy

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.UiEffectHost
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.ADMIN_TAXONOMY_NAME_MAX
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.ui.AdminConfirmDialog
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.feature.admin.ui.AdminEmptyState
import com.stylo.shop.feature.admin.ui.AdminErrorBanner
import com.stylo.shop.feature.admin.ui.AdminErrorState
import com.stylo.shop.feature.admin.ui.AdminListSkeleton
import com.stylo.shop.feature.admin.ui.AdminSearchField
import com.stylo.shop.feature.admin.ui.TaxonomyStatusBadge
import com.stylo.shop.feature.admin.ui.adminFieldErrorText

@Composable
fun AdminTaxonomyScreen(
    onBack: () -> Unit,
    viewModel: AdminTaxonomyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    UiEffectHost(effects = viewModel.effects, snackbarHostState = snackbarHostState)

    AdminDetailScaffold(
        title = stringResource(R.string.admin_taxonomy_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PrimaryTabRow(
                selectedTabIndex = state.tab.ordinal,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                TaxonomyTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                stringResource(
                                    if (tab == TaxonomyTab.CATEGORIES) {
                                        R.string.admin_taxonomy_tab_categories
                                    } else {
                                        R.string.admin_taxonomy_tab_brands
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            val list = state.currentList
            val isCategories = state.tab == TaxonomyTab.CATEGORIES

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (list.itemsState is UiState.Loading) {
                        ""
                    } else {
                        stringResource(
                            if (isCategories) {
                                R.string.admin_taxonomy_count_categories
                            } else {
                                R.string.admin_taxonomy_count_brands
                            },
                            list.total,
                        )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = viewModel::openCreate) {
                    Text(
                        stringResource(
                            if (isCategories) {
                                R.string.admin_taxonomy_add_category
                            } else {
                                R.string.admin_taxonomy_add_brand
                            },
                        ),
                    )
                }
            }

            AdminSearchField(
                query = list.searchQuery,
                placeholder = stringResource(R.string.admin_taxonomy_search_hint),
                onQueryChange = viewModel::onSearchQueryChanged,
                onSubmit = viewModel::onSearchSubmit,
                onClear = viewModel::onClearSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(TaxonomyStatusFilter.entries, key = { it.name }) { filter ->
                    val selected = list.statusFilter == filter
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.onStatusFilterSelected(filter) },
                        label = { Text(filter.label()) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            }
                        } else {
                            null
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (val items = list.itemsState) {
                    is UiState.Loading -> AdminListSkeleton(rows = 6, thumbnail = false)
                    is UiState.Error -> AdminErrorState(
                        error = items.error,
                        onRetry = viewModel::onRetry,
                    )
                    is UiState.Content -> {
                        if (items.data.isEmpty()) {
                            AdminEmptyState(
                                icon = Icons.AutoMirrored.Filled.List,
                                title = stringResource(
                                    if (isCategories) {
                                        R.string.admin_taxonomy_empty_categories
                                    } else {
                                        R.string.admin_taxonomy_empty_brands
                                    },
                                ),
                                body = stringResource(R.string.admin_taxonomy_empty_body),
                                actionLabel = stringResource(
                                    if (isCategories) {
                                        R.string.admin_taxonomy_add_category
                                    } else {
                                        R.string.admin_taxonomy_add_brand
                                    },
                                ),
                                onAction = viewModel::openCreate,
                            )
                        } else {
                            TaxonomyList(
                                items = items.data,
                                listState = list,
                                onEdit = viewModel::openEdit,
                                onDelete = viewModel::requestDelete,
                                onLoadMore = viewModel::onLoadMore,
                                onRetryLoadMore = viewModel::onLoadMore,
                            )
                        }
                    }
                }
            }
        }
    }

    state.editor?.let { editor ->
        TaxonomyEditorDialog(
            editor = editor,
            isEditing = editor.id != null,
            isCategories = state.tab == TaxonomyTab.CATEGORIES,
            onNameChange = viewModel::onEditorNameChange,
            onStatusChange = viewModel::onEditorStatusChange,
            onSave = viewModel::saveEditor,
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.confirmDelete?.let { target ->
        val isCategories = state.tab == TaxonomyTab.CATEGORIES
        if (target.productCount > 0) {
            AlertDialog(
                onDismissRequest = viewModel::dismissDelete,
                title = {
                    Text(
                        stringResource(
                            if (isCategories) {
                                R.string.admin_taxonomy_delete_category_title
                            } else {
                                R.string.admin_taxonomy_delete_brand_title
                            },
                        ),
                    )
                },
                text = {
                    Text(
                        stringResource(
                            R.string.admin_taxonomy_delete_in_use,
                            target.productCount,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissDelete()
                            viewModel.openDisable(target)
                        },
                    ) {
                        Text(stringResource(R.string.admin_taxonomy_disable))
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDelete) {
                        Text(stringResource(R.string.admin_taxonomy_keep))
                    }
                },
            )
        } else {
            AdminConfirmDialog(
                title = stringResource(
                    if (isCategories) {
                        R.string.admin_taxonomy_delete_category_title
                    } else {
                        R.string.admin_taxonomy_delete_brand_title
                    },
                ),
                body = stringResource(R.string.admin_taxonomy_delete_body),
                confirmLabel = stringResource(R.string.action_delete),
                busy = state.isDeleting,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::dismissDelete,
            )
        }
    }
}

@Composable
private fun TaxonomyList(
    items: List<AdminTaxonomy>,
    listState: TaxonomyListUiState,
    onEdit: (AdminTaxonomy) -> Unit,
    onDelete: (AdminTaxonomy) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val lazyState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = lazyState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = lazyState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = lazyState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(item) }
                    .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.admin_taxonomy_product_count,
                            item.productCount,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                TaxonomyStatusBadge(status = item.status)
                IconButton(onClick = { onDelete(item) }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (listState.isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else if (listState.loadMoreError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(
                            ErrorMessages.resourceId(listState.loadMoreError),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetryLoadMore) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaxonomyEditorDialog(
    editor: TaxonomyEditorState,
    isEditing: Boolean,
    isCategories: Boolean,
    onNameChange: (String) -> Unit,
    onStatusChange: (CatalogStatus) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    when {
                        isCategories && !isEditing -> R.string.admin_taxonomy_add_category
                        isCategories -> R.string.admin_taxonomy_edit_category
                        !isEditing -> R.string.admin_taxonomy_add_brand
                        else -> R.string.admin_taxonomy_edit_brand
                    },
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.admin_taxonomy_name)) },
                    isError = editor.nameError != null,
                    supportingText = editor.nameError?.let {
                        { Text(adminFieldErrorText(it, ADMIN_TAXONOMY_NAME_MAX)) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (isEditing) {
                    Text(
                        text = stringResource(R.string.admin_product_status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        CatalogStatus.entries.forEachIndexed { index, status ->
                            SegmentedButton(
                                selected = editor.status == status,
                                onClick = { onStatusChange(status) },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = CatalogStatus.entries.size,
                                ),
                                label = {
                                    Text(
                                        stringResource(
                                            if (status == CatalogStatus.ACTIVE) {
                                                R.string.admin_status_active_taxonomy
                                            } else {
                                                R.string.admin_status_inactive_taxonomy
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                editor.formError?.let { AdminErrorBanner(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave, enabled = !editor.saving) {
                if (editor.saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !editor.saving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun TaxonomyStatusFilter.label(): String = when (this) {
    TaxonomyStatusFilter.ALL -> stringResource(R.string.admin_filter_all)
    TaxonomyStatusFilter.ACTIVE -> stringResource(R.string.admin_status_active_taxonomy)
    TaxonomyStatusFilter.INACTIVE -> stringResource(R.string.admin_filter_inactive)
}
