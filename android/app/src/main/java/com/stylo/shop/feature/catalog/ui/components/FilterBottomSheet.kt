package com.stylo.shop.feature.catalog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stylo.shop.core.ui.e2eTagsAsResourceId
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.catalog.model.CatalogFilters
import com.stylo.shop.core.ui.components.StyloFilterChip
import com.stylo.shop.core.ui.Formatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    filters: CatalogFilters,
    selectedCategoryId: Long?,
    selectedBrandId: Long?,
    selectedColor: String?,
    selectedSize: String?,
    selectedMinPrice: Long?,
    selectedMaxPrice: Long?,
    onCategorySelected: (Long?) -> Unit,
    onBrandSelected: (Long?) -> Unit,
    onColorSelected: (String?) -> Unit,
    onSizeSelected: (String?) -> Unit,
    onPriceSelected: (Long?, Long?) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.e2eTagsAsResourceId(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.catalog_filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.testTag(UiTags.FilterClear),
                ) {
                    Text(
                        text = stringResource(R.string.catalog_filter_clear),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Categories
            if (filters.categories.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.catalog_filter_category),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.categories.forEach { category ->
                        val isSelected = category.id == selectedCategoryId
                        StyloFilterChip(
                            label = category.name,
                            selected = isSelected,
                            onClick = { onCategorySelected(if (isSelected) null else category.id) },
                            modifier = Modifier.testTag(UiTags.FilterCategory),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
            }

            // Brands
            if (filters.brands.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.catalog_filter_brand),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.brands.forEach { brand ->
                        val isSelected = brand.id == selectedBrandId
                        StyloFilterChip(
                            label = brand.name,
                            selected = isSelected,
                            onClick = { onBrandSelected(if (isSelected) null else brand.id) },
                            modifier = Modifier.testTag(UiTags.FilterBrand),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
            }

            // Colors
            if (filters.colors.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.catalog_filter_color),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.colors.forEach { color ->
                        val isSelected = color == selectedColor
                        StyloFilterChip(
                            label = color,
                            selected = isSelected,
                            onClick = { onColorSelected(if (isSelected) null else color) },
                            modifier = Modifier.testTag(UiTags.FilterColor),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
            }

            // Sizes
            if (filters.sizes.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.catalog_filter_size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.sizes.forEach { size ->
                        val isSelected = size == selectedSize
                        StyloFilterChip(
                            label = size,
                            selected = isSelected,
                            onClick = { onSizeSelected(if (isSelected) null else size) },
                            modifier = Modifier.testTag(UiTags.FilterSize),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(16.dp))
            }

            // Price range quick presets
            Text(
                text = stringResource(R.string.catalog_filter_price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val presets = listOf(
                    Triple(
                        stringResource(R.string.filter_price_under, Formatters.money(200_000)),
                        null,
                        200_000L,
                    ),
                    Triple(
                        stringResource(
                            R.string.filter_price_between,
                            Formatters.money(200_000),
                            Formatters.money(400_000),
                        ),
                        200_000L,
                        400_000L,
                    ),
                    Triple(
                        stringResource(R.string.filter_price_over, Formatters.money(400_000)),
                        400_000L,
                        null,
                    ),
                )

                presets.forEach { (label, min, max) ->
                    val isSelected = selectedMinPrice == min && selectedMaxPrice == max
                    StyloFilterChip(
                        label = label,
                        selected = isSelected,
                        onClick = {
                            if (isSelected) {
                                onPriceSelected(null, null)
                            } else {
                                onPriceSelected(min, max)
                            }
                        },
                        modifier = Modifier.testTag(UiTags.FilterPrice),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // Bottom CTA
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(UiTags.FilterApply),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = stringResource(R.string.catalog_filter_apply),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
