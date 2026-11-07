package com.stylo.shop.feature.catalog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.catalog.model.CategoryItem
import com.stylo.shop.feature.catalog.ui.components.FilterBottomSheet
import com.stylo.shop.feature.catalog.ui.components.ProductCardView
import com.stylo.shop.feature.catalog.ui.components.SortSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onNavigateToProduct: (Long) -> Unit,
    modifier: Modifier = Modifier,
    categoryId: Long? = null,
    focusSearch: Boolean = false,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(categoryId) {
        if (categoryId != null) viewModel.onSelectCategoryTab(categoryId)
    }
    LaunchedEffect(focusSearch) {
        if (focusSearch) searchFocusRequester.requestFocus()
    }

    // Trigger load more when reaching bottom of grid
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = gridState.layoutInfo.totalItemsCount
            val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.onLoadMore()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // App bar / Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STYLO",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
            )
        }

        // Search Bar & Filter Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text(
                        text = stringResource(R.string.catalog_search_hint),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = viewModel::onClearSearch,
                            modifier = Modifier.testTag(UiTags.CatalogSearchClear),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Xoá",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        viewModel.onSearchSubmit()
                    },
                ),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .focusRequester(searchFocusRequester)
                    .testTag(UiTags.CatalogSearch),
            )

            Spacer(Modifier.width(10.dp))

            // Filter button with badge
            Surface(
                onClick = viewModel::onOpenFilterSheet,
                shape = RoundedCornerShape(12.dp),
                color = if (state.activeFilterCount > 0) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier
                    .height(52.dp)
                    .testTag(UiTags.FilterOpen),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.catalog_filter_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (state.activeFilterCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = state.activeFilterCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }

        // Category Chips Row
        val filtersState = state.filtersState
        if (filtersState is UiState.Content && filtersState.data.categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // "Tất cả" chip
                item {
                    val isAllSelected = state.activeCategoryId == null
                    FilterChip(
                        selected = isAllSelected,
                        onClick = { viewModel.onSelectCategoryTab(null) },
                        label = { Text(stringResource(R.string.catalog_all_categories)) },
                        modifier = Modifier.testTag(UiTags.CatalogCategoryChip),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }

                items(filtersState.data.categories, key = { it.id }) { category ->
                    val isSelected = state.activeCategoryId == category.id
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.onSelectCategoryTab(category.id) },
                        label = { Text(category.name) },
                        modifier = Modifier.testTag(UiTags.CatalogCategoryChip),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        // Sort Row & Product Count
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.catalog_products_count, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SortSelector(
                selectedSort = state.activeSort,
                onSortSelected = viewModel::onSortSelected,
            )
        }

        Spacer(Modifier.height(4.dp))

        // Product Grid Content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when (val productsState = state.productsState) {
                is UiState.Loading -> {
                    // Skeleton loading grid (6 cards)
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        userScrollEnabled = false,
                    ) {
                        items(6) {
                            CatalogCardSkeleton()
                        }
                    }
                }

                is UiState.Error -> {
                    // Error state with retry button
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = stringResource(ErrorMessages.resourceId(productsState.error)),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = viewModel::onRetry,
                            modifier = Modifier.testTag(UiTags.CatalogRetry),
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

                is UiState.Content -> {
                    val products = productsState.data
                    if (products.isEmpty()) {
                        // Empty state: "Không tìm thấy sản phẩm" + "Xoá lọc"
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.catalog_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.catalog_empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = viewModel::onClearAllFilters,
                                modifier = Modifier.testTag(UiTags.CatalogEmptyClear),
                            ) {
                                Text(stringResource(R.string.catalog_filter_clear))
                            }
                        }
                    } else {
                        // Product Grid (2 columns, 14dp spacing, 20dp margin)
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(products, key = { it.id }) { product ->
                                ProductCardView(
                                    product = product,
                                    onClick = { onNavigateToProduct(product.id) },
                                    modifier = Modifier.testTag(UiTags.ProductCard),
                                )
                            }

                            // Loading more footer indicator
                            if (state.isLoadingMore) {
                                item(span = { GridItemSpan(2) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(28.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Filter Bottom Sheet
    if (state.isFilterSheetOpen) {
        val currentFilters = (state.filtersState as? UiState.Content)?.data
        if (currentFilters != null) {
            FilterBottomSheet(
                filters = currentFilters,
                selectedCategoryId = state.draftCategoryId,
                selectedBrandId = state.draftBrandId,
                selectedColor = state.draftColor,
                selectedSize = state.draftSize,
                selectedMinPrice = state.draftMinPrice,
                selectedMaxPrice = state.draftMaxPrice,
                onCategorySelected = viewModel::onDraftCategorySelected,
                onBrandSelected = viewModel::onDraftBrandSelected,
                onColorSelected = viewModel::onDraftColorSelected,
                onSizeSelected = viewModel::onDraftSizeSelected,
                onPriceSelected = viewModel::onDraftPriceChanged,
                onApply = viewModel::onApplyDraftFilters,
                onClear = viewModel::onClearDraftFilters,
                onDismiss = viewModel::onDismissFilterSheet,
                sheetState = filterSheetState,
            )
        }
    }
}

@Composable
private fun CatalogCardSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
