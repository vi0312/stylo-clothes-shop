package com.stylo.shop.feature.catalog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.core.ui.components.StyloSizeChip
import com.stylo.shop.core.ui.components.StyloSwatch
import com.stylo.shop.feature.catalog.ui.SizeItemState

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ColorSelector(
    colors: List<String>,
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.product_detail_color),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.product_detail_color_count, colors.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedColor != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedColor,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            colors.forEach { colorName ->
                StyloSwatch(
                    colorName = colorName,
                    selected = colorName == selectedColor,
                    onClick = { onColorSelected(colorName) },
                    modifier = Modifier.testTag(UiTags.ProductDetailColor),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SizeSelector(
    sizes: List<SizeItemState>,
    selectedSize: String?,
    onSizeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.product_detail_size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (selectedSize != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedSize,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            sizes.forEach { item ->
                StyloSizeChip(
                    size = item.size,
                    selected = item.size == selectedSize,
                    available = item.isAvailable,
                    onClick = { onSizeSelected(item.size) },
                    modifier = Modifier.testTag(UiTags.ProductDetailSize),
                )
            }
        }
    }
}
