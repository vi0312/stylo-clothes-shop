package com.stylo.shop.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stylo.shop.core.ui.Formatters
import com.stylo.shop.core.ui.theme.statusColors

@Composable
private fun AdminHeader(title: String, subtitle: String? = null, action: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action()
    }
}

@Composable
private fun Chip(label: String, selected: Boolean = false) {
    FilterChip(
        selected = selected,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        shape = CircleShape,
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun DateFilter(current: String) {
    AssistChip(
        onClick = {},
        label = { Text(current, style = MaterialTheme.typography.labelMedium) },
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outline,
        ),
        leadingIcon = {
            Icon(
                Icons.Filled.DateRange,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
fun AdminNavBar(selected: String) {
    val items = listOf(
        "Tổng quan" to Icons.Filled.Home,
        "Đơn hàng" to Icons.AutoMirrored.Filled.List,
        "Sản phẩm" to Icons.Filled.ShoppingCart,
        "Kho" to Icons.Filled.Build,
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
fun AdminDashboardA() {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        AdminHeader("Tổng quan", "Tháng 10/2026", action = { DateFilter("01/10 – 31/10") })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Doanh thu", "128,4 tr", "+12,3%", Modifier.weight(1f))
                KpiCard("Đơn hàng", "342", "+8,1%", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Giá trị TB", "375.400 ₫", "+4,0%", Modifier.weight(1f))
                KpiCard(
                    "Tồn thấp",
                    "7 variant",
                    "cần nhập",
                    Modifier.weight(1f),
                    deltaColor = MaterialTheme.statusColors.warning,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Doanh thu 7 ngày", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("28,6 tr", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                RevenueBars(
                    values = listOf(26, 31, 22, 38, 44, 36, 52),
                    labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN"),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionHeader("Top sản phẩm", "Xem báo cáo")
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            mockProducts.take(3).forEachIndexed { index, product ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp),
                    )
                    Box(
                        Modifier
                            .width(36.dp)
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Brush.linearGradient(listOf(product.tones.first, product.tones.second))),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Đã bán ${product.sold}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        Formatters.money(product.price * product.sold),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionHeader("Đơn gần đây", "Tất cả")
        Spacer(Modifier.height(4.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            mockOrders.take(3).forEach { order ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(order.code, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${order.customer} · ${order.time}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(Formatters.money(order.total), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    StatusBadge(order.status)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        }
        AdminNavBar("Tổng quan")
    }
}

@Composable
fun AdminDashboardB() {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        AdminHeader("Báo cáo nhanh", "Cập nhật 09:42", action = { DateFilter("Hôm nay") })
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("28.640.000 ₫", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "+12,3%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(
                    "Doanh thu hôm nay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                RevenueBars(
                    values = listOf(12, 18, 9, 22, 26, 20, 31),
                    labels = listOf("00h", "04h", "08h", "12h", "16h", "20h", "24h"),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            item { Chip("Tất cả", selected = true) }
            item { Chip("Chờ xử lý") }
            item { Chip("Đang giao") }
            item { Chip("Đã giao") }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(Modifier.padding(horizontal = 20.dp)) {
            mockOrders.forEach { order ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(order.code, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.width(8.dp))
                            StatusBadge(order.status)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${order.customer} · ${order.time}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        Formatters.money(order.total),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        }
        AdminNavBar("Tổng quan")
    }
}

@Composable
fun AdminProducts() {
    Column(Modifier.fillMaxSize()) {
        AdminHeader(
            "Sản phẩm",
            "42 sản phẩm · 176 variant",
            action = {
                Button(onClick = {}) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Thêm")
                }
            },
        )
        SearchBarStub(hint = "Tìm theo tên hoặc SKU")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            item { Chip("Tất cả", selected = true) }
            item { Chip("Đang bán") }
            item { Chip("Đã ẩn") }
            item { Chip("Tồn thấp") }
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) {
            item { Chip("Áo") }
            item { Chip("Stylo Basics") }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            mockProducts.forEach { product ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(46.dp)
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(Brush.linearGradient(listOf(product.tones.first, product.tones.second))),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            product.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${product.brand} · ${if (product.stock <= 5) "còn ${product.stock}" else "12 variant"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (product.stock <= 5) {
                                MaterialTheme.statusColors.warning
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Formatters.money(product.price), style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(2.dp))
                        StatusBadge(if (product.stock <= 5) "Tồn thấp" else "Đang bán")
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
        AdminNavBar("Sản phẩm")
    }
}

@Composable
fun TokenSheet() {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Design system", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Palette ấm · light-only",
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text("Màu", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Nền" to scheme.background,
                "Bề mặt" to scheme.surface,
                "Chữ" to scheme.onSurface,
                "Nhấn" to scheme.primaryContainer,
                "Accent" to scheme.secondary,
            ).forEach { (label, color) ->
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(color)
                            .border(1.dp, scheme.outline, MaterialTheme.shapes.small),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Chữ", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text("Đồ mặc hằng ngày", style = MaterialTheme.typography.displaySmall)
        Text("Mới về tuần này", style = MaterialTheme.typography.headlineSmall)
        Text("Áo thun oversize Alpha", style = MaterialTheme.typography.titleLarge)
        Text("289.000 ₫", style = MaterialTheme.typography.titleMedium)
        Text(
            "Cotton 2 chiều 240gsm, form oversize, giặt máy 30°C.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "ĐÃ BÁN 214 · MIỄN PHÍ GIAO HÀNG",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text("Thành phần", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {}) { Text("Thêm vào giỏ") }
            OutlinedButton(
                onClick = {},
                border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outline),
            ) { Text("Mua ngay") }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Tất cả", selected = true)
            Chip("Áo")
            Chip("Quần")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusBadge("Đang giao")
            StatusBadge("Đã giao")
            StatusBadge("Tồn thấp")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = "",
            onValueChange = {},
            label = { Text("Email") },
            placeholder = { Text("ban@example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Text("Ảnh sản phẩm · tỉ lệ 3:4", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductImage(mockProducts[0], Modifier.weight(1f), showLabel = false)
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(3f / 4f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("ảnh 2", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}
