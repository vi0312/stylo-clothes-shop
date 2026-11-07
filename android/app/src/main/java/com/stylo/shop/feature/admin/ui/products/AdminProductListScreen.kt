package com.stylo.shop.feature.admin.ui.products

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.AdminTaxonomy
import com.stylo.shop.feature.admin.model.CatalogStatus
import com.stylo.shop.feature.admin.ui.AdminEmptyState
import com.stylo.shop.feature.admin.ui.AdminErrorState
import com.stylo.shop.feature.admin.ui.AdminListSkeleton
import com.stylo.shop.feature.admin.ui.AdminProductThumbnail
import com.stylo.shop.feature.admin.ui.AdminScaffold
import com.stylo.shop.feature.admin.ui.AdminSearchField
import com.stylo.shop.feature.admin.ui.AdminTab
import com.stylo.shop.feature.admin.ui.ProductStatusBadge

@Composable
fun AdminProductListScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onSelectTab: (AdminTab) -> Unit,
    onOpenTaxonomy: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenReports: () -> Unit,
    onLogout: () -> Unit,
    viewModel: AdminProductListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Returning from the form must show the new or edited product.
    LaunchedEffect(Unit) { viewModel.refreshIfLoaded() }

    AdminScaffold(
        selectedTab = AdminTab.PRODUCTS,
        title = stringResource(R.string.admin_tab_products),
        onSelectTab = onSelectTab,
        onOpenTaxonomy = onOpenTaxonomy,
        onOpenUsers = onOpenUsers,
        onOpenReports = onOpenReports,
        onLogout = onLogout,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (state.productsState is UiState.Loading) {
                        ""
                    } else {
                        stringResource(R.string.admin_products_count, state.total)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onAdd) {
                    Text(stringResource(R.string.admin_products_add))
                }
            }

            AdminSearchField(
                query = state.searchQuery,
                placeholder = stringResource(R.string.admin_products_search_hint),
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
                items(AdminStatusFilter.entries, key = { it.name }) { filter ->
                    val selected = state.statusFilter == filter
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TaxonomyFilterChip(
                    label = stringResource(R.string.admin_filter_category),
                    items = state.categories,
                    selectedId = state.categoryId,
                    onSelect = viewModel::onCategorySelected,
                )
                TaxonomyFilterChip(
                    label = stringResource(R.string.admin_filter_brand),
                    items = state.brands,
                    selectedId = state.brandId,
                    onSelect = viewModel::onBrandSelected,
                )
                if (state.activeFilterCount > 0) {
                    TextButton(onClick = viewModel::onClearFilters) {
                        Text(stringResource(R.string.catalog_filter_clear))
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (val products = state.productsState) {
                    is UiState.Loading -> AdminListSkeleton(rows = 6)
                    is UiState.Error -> AdminErrorState(
                        error = products.error,
                        onRetry = viewModel::onRetry,
                    )
                    is UiState.Content -> {
                        if (products.data.isEmpty()) {
                            AdminEmptyState(
                                icon = Icons.Filled.ShoppingCart,
                                title = stringResource(R.string.admin_products_empty_title),
                                body = stringResource(R.string.admin_products_empty_body),
                                actionLabel = stringResource(R.string.admin_products_add),
                                onAction = onAdd,
                            )
                        } else {
                            ProductList(
                                products = products.data,
                                state = state,
                                onEdit = onEdit,
                                onLoadMore = viewModel::onLoadMore,
                                onRetryLoadMore = viewModel::onLoadMore,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductList(
    products: List<AdminProductSummary>,
    state: AdminProductListUiState,
    onEdit: (Long) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
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
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        items(products, key = { it.id }) { product ->
            ProductRow(product = product, onClick = { onEdit(product.id) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        if (state.isLoadingMore) {
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
                    TextButton(onClick = onRetryLoadMore) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductRow(
    product: AdminProductSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AdminProductThumbnail(url = product.coverUrl, name = product.name)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = product.brand.name + " · " + stringResource(
                    R.string.admin_products_variant_count,
                    product.variantCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (product.isLowStock) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.admin_products_stock, product.totalStock),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.statusColors.warning,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = product.priceLabel(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            ProductStatusBadge(product = product)
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaxonomyFilterChip(
    label: String,
    items: List<AdminTaxonomy>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = items.firstOrNull { it.id == selectedId }?.name

    Box {
        FilterChip(
            selected = selectedId != null,
            onClick = { expanded = true },
            label = {
                Text(
                    if (selectedName != null) "$label: $selectedName" else label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            leadingIcon = if (selectedId != null) {
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
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.admin_filter_all)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (item.status == CatalogStatus.INACTIVE) {
                                item.name + " (" + stringResource(R.string.admin_filter_inactive) + ")"
                            } else {
                                item.name
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(item.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AdminStatusFilter.label(): String = when (this) {
    AdminStatusFilter.ALL -> stringResource(R.string.admin_filter_all)
    AdminStatusFilter.ACTIVE -> stringResource(R.string.admin_filter_active)
    AdminStatusFilter.INACTIVE -> stringResource(R.string.admin_filter_inactive)
    AdminStatusFilter.LOW_STOCK -> stringResource(R.string.admin_filter_low_stock)
}

private fun AdminProductSummary.priceLabel(): String = when {
    minPrice != null && maxPrice != null && minPrice != maxPrice ->
        "${Formatters.money(minPrice)} - ${Formatters.money(maxPrice)}"
    minPrice != null -> Formatters.money(minPrice)
    maxPrice != null -> Formatters.money(maxPrice)
    else -> "—"
}
