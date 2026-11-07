package com.stylo.shop.feature.catalog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.catalog.ui.SizeItemState

private fun parseColor(name: String): Color = when (name.trim().lowercase()) {
    "đen", "black" -> Color(0xFF1E1C18)
    "trắng", "white" -> Color(0xFFFBF8F1)
    "xanh đậm", "navy", "xanh navy" -> Color(0xFF1E3A5F)
    "xanh dương", "blue" -> Color(0xFF2C5E8A)
    "xám", "ghi", "gray", "grey" -> Color(0xFF8E8D8A)
    "be", "beige" -> Color(0xFFDDCBA4)
    "nâu", "brown" -> Color(0xFF6B4226)
    "đỏ", "red" -> Color(0xFFA63228)
    "hồng", "pink" -> Color(0xFFD48B92)
    "vàng", "yellow" -> Color(0xFFD4A017)
    "xanh lá", "green" -> Color(0xFF4F5E3F)
    else -> Color(0xFFB5A895)
}

@OptIn(ExperimentalLayoutApi::class)
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
                text = "Màu sắc",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (selectedColor != null) {
                Text(
                    text = ": $selectedColor",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            colors.forEach { colorName ->
                val isSelected = colorName == selectedColor
                val colorValue = parseColor(colorName)

                // 48dp minimum touch target wrapping a 26dp / 36dp swatch
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .testTag(UiTags.ProductDetailColor)
                        .clip(CircleShape)
                        .clickable { onColorSelected(colorName) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        // Outer selection ring
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(colorValue, CircleShape)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(colorValue, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                text = "Kích cỡ",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            if (selectedSize != null) {
                Text(
                    text = ": $selectedSize",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sizes.forEach { item ->
                val isSelected = item.size == selectedSize
                val isAvailable = item.isAvailable

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag(UiTags.ProductDetailSize)
                        .alpha(if (isAvailable) 1f else 0.4f)
                        .then(
                            if (!isSelected && isAvailable) {
                                Modifier.border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                )
                            } else Modifier,
                        )
                        .clickable(enabled = isAvailable) {
                            onSizeSelected(item.size)
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = item.size,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
