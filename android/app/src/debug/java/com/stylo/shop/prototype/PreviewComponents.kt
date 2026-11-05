package com.stylo.shop.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.theme.statusColors

@Composable
fun ProductImage(
    product: MockProduct,
    modifier: Modifier = Modifier,
    ratio: Float = 3f / 4f,
    showLabel: Boolean = true,
) {
    Box(
        modifier
            .aspectRatio(ratio)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(listOf(product.tones.first, product.tones.second))),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.10f), Color.Transparent),
                    ),
                ),
        )
        if (showLabel) {
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    product.brand.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                    letterSpacing = 1.sp,
                )
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        product.badge?.let { badge ->
            val lowStock = product.stock <= 5
            val background = if (lowStock) {
                MaterialTheme.statusColors.warningContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
            val content = if (lowStock) {
                MaterialTheme.statusColors.onWarningContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Surface(
                color = background,
                contentColor = content,
                shape = MaterialTheme.shapes.extraSmall,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
fun PriceLine(
    price: Long,
    compareAt: Long? = null,
    modifier: Modifier = Modifier,
    large: Boolean = false,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            Formatters.money(price),
            style = if (large) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.titleSmall
            },
        )
        if (compareAt != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                Formatters.money(compareAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun CategoryChips(items: List<String>, selected: String? = null) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
    ) {
        items(items.size) { index ->
            val item = items[index]
            val isSelected = item == selected
            FilterChip(
                selected = isSelected,
                onClick = {},
                label = { Text(item, style = MaterialTheme.typography.labelLarge) },
                shape = CircleShape,
                leadingIcon = if (isSelected) {
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outline,
                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
fun SearchBarStub(hint: String = "Tìm áo, quần, thương hiệu…") {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("Bộ lọc", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun SortRow(count: Int, sort: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$count sản phẩm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(sort, style = MaterialTheme.typography.labelLarge)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun StatusBadge(status: String) {
    val statusColors = MaterialTheme.statusColors
    val (fg, bg) = when (status) {
        "Chờ xử lý", "Chưa thanh toán", "Tồn thấp" ->
            statusColors.warning to statusColors.warningContainer
        "Đã xác nhận" -> statusColors.info to statusColors.infoContainer
        "Đang giao" -> statusColors.indigo to statusColors.indigoContainer
        "Đã giao", "Đang bán", "Đã thanh toán" ->
            statusColors.success to statusColors.successContainer
        "Đã huỷ", "Đã ẩn", "Đã khoá" ->
            statusColors.neutral to statusColors.neutralContainer
        "Thanh toán lỗi" ->
            MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant to MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = bg, contentColor = fg, shape = MaterialTheme.shapes.extraSmall) {
        Text(
            status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun QtyStepper(quantity: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperBox("Giảm") {
            Text("−", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            quantity.toString(),
            modifier = Modifier.width(34.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
        )
        StepperBox("Tăng") {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun StepperBox(description: String, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = {})
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.extraSmall),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun BottomNavBar(selected: String) {
    val items = listOf(
        "Trang chủ" to Icons.Filled.Home,
        "Tìm kiếm" to Icons.Filled.Search,
        "Giỏ hàng" to Icons.Filled.ShoppingCart,
        "Đơn hàng" to Icons.AutoMirrored.Filled.List,
        "Tôi" to Icons.Filled.Person,
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        items.forEach { (label, icon) ->
            NavigationBarItem(
                selected = label == selected,
                onClick = {},
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun KpiCard(
    label: String,
    value: String,
    delta: String,
    modifier: Modifier = Modifier,
    deltaColor: Color = MaterialTheme.colorScheme.secondary,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall)
            Text(
                delta,
                style = MaterialTheme.typography.labelMedium,
                color = deltaColor,
            )
        }
    }
}

@Composable
fun RevenueBars(values: List<Int>, labels: List<String>) {
    val ink = MaterialTheme.colorScheme.primary
    Column {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .semantics {
                    contentDescription = values.mapIndexed { index, value ->
                        "${labels.getOrNull(index) ?: index}: $value"
                    }.joinToString(", ")
                },
        ) {
            val max = values.max().coerceAtLeast(1)
            val slot = size.width / values.size
            val barWidth = slot * 0.46f
            values.forEachIndexed { index, value ->
                val height = size.height * (value / max.toFloat()) * 0.92f
                val left = slot * index + (slot - barWidth) / 2f
                drawRoundRect(
                    color = if (index == values.lastIndex) ink else ink.copy(alpha = 0.62f),
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(6f, 6f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MockImageStrip(titles: List<String>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        titles.forEach { title ->
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
