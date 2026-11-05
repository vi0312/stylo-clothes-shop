package com.stylo.shop.prototype

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stylo.shop.core.ui.Formatters

private val heroTones = listOf(Color(0xFF2B2722), Color(0xFF6B5B49))

@Composable
private fun BrandRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "STYLO",
            style = MaterialTheme.typography.titleLarge,
            letterSpacing = 6.sp,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Search, contentDescription = "Tìm", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = {}) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = "Giỏ", modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = {}) {
            Icon(Icons.Filled.Person, contentDescription = "Tôi", modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HeroBanner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .aspectRatio(1.15f)
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(heroTones)),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 28.dp, end = 24.dp)
                .size(150.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.08f)),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(22.dp),
        ) {
            Text(
                "BỘ SƯU TẬP THU 2026",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Đồ mặc hằng ngày,\ncắt may gọn gàng.",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {},
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text("Xem bộ sưu tập", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ProductCard(product: MockProduct, modifier: Modifier = Modifier) {
    Column(modifier) {
        ProductImage(product, Modifier.fillMaxWidth(), showLabel = false)
        Spacer(Modifier.height(8.dp))
        Text(
            product.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            product.brand,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        PriceLine(product.price, product.compareAt)
    }
}

@Composable
private fun ProductGrid(products: List<MockProduct>) {
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        products.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { product ->
                    ProductCard(product, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun HomeA() {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        Spacer(Modifier.height(14.dp))
        BrandRow()
        Spacer(Modifier.height(16.dp))
        HeroBanner()
        Spacer(Modifier.height(20.dp))
        CategoryChips(categories)
        Spacer(Modifier.height(22.dp))
        SectionHeader("Mới về", "Xem tất cả")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(minOf(8, mockProducts.size)) { index ->
                ProductCard(mockProducts[index], Modifier.width(172.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Miễn phí giao hàng từ 500.000 ₫",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionHeader("Áo", "Xem tất cả")
        Spacer(Modifier.height(14.dp))
        ProductGrid(mockProducts.filter { it.category == "Áo" }.take(4))
        Spacer(Modifier.height(24.dp))
        SectionHeader("Quần", "Xem tất cả")
        Spacer(Modifier.height(14.dp))
        ProductGrid(mockProducts.filter { it.category == "Quần" }.take(4))
        Spacer(Modifier.height(16.dp))
        }
        BottomNavBar("Trang chủ")
    }
}

@Composable
fun HomeB() {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Stylo",
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = 2.sp,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ShoppingCart, contentDescription = "Giỏ", modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            SearchBarStub()
            Spacer(Modifier.height(12.dp))
            CategoryChips(listOf("Tất cả") + categories, selected = "Tất cả")
            Spacer(Modifier.height(4.dp))
            SortRow(48, "Mới nhất")
            Spacer(Modifier.height(14.dp))
            ProductGrid(mockProducts)
            Spacer(Modifier.height(24.dp))
        }
        BottomNavBar("Tìm kiếm")
    }
}

@Composable
fun HomeC() {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("STYLO", style = MaterialTheme.typography.titleSmall, letterSpacing = 5.sp, modifier = Modifier.weight(1f))
            Text("VN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                "THU / 2026",
                style = MaterialTheme.typography.displayMedium,
                lineHeight = 52.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Đồ thường ngày, làm kỹ. Chọn lọc từ các xưởng nhỏ ở Sài Gòn.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(280.dp),
            )
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1.2f)
                    .aspectRatio(0.62f)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Brush.linearGradient(heroTones)),
            ) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                ) {
                    Text("LOOK 01", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f), letterSpacing = 2.sp)
                    Text("Linen & kaki", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.86f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text(
                        "Đồ đôi",
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.86f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.secondary),
                ) {
                    Text(
                        "SALE\n−20%",
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(12.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        SectionHeader("Đồ đôi", "Tất cả")
        Spacer(Modifier.height(12.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
        ) {
            items(mockProducts.size) { index ->
                ProductCard(mockProducts[index], Modifier.width(150.dp))
            }
        }
        Spacer(Modifier.height(26.dp))
        Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("NOCTURNE", style = MaterialTheme.typography.labelSmall, letterSpacing = 3.sp)
                    Text("Bomber limited — còn 3", style = MaterialTheme.typography.titleMedium)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        Spacer(Modifier.height(26.dp))
        SectionHeader("Mới về")
        Spacer(Modifier.height(14.dp))
        ProductGrid(mockProducts.take(4))
        Spacer(Modifier.height(16.dp))
        }
        BottomNavBar("Trang chủ")
    }
}

@Composable
private fun GalleryPlaceholder(ratio: Float = 1f) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .background(Brush.linearGradient(heroTones)),
    ) {
        IconButton(
            onClick = {},
            modifier = Modifier
                .padding(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = MaterialTheme.colorScheme.onPrimary)
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(4) { index ->
                Box(
                    Modifier
                        .size(if (index == 0) 18.dp else 6.dp, 6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = if (index == 0) 1f else 0.45f)),
                )
            }
        }
    }
}

@Composable
private fun ColorSwatches(product: MockProduct) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        product.swatches.forEachIndexed { index, color ->
            val selected = index == 0
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {})
                    .semantics { contentDescription = "Màu ${index + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (selected) 0.dp else 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }
        Text(
            product.swatches.size.toString() + " màu",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SizeRow(sizes: List<String> = listOf("S", "M", "L", "XL")) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sizes.forEachIndexed { index, size ->
            val selected = index == 2
            Box(
                Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .border(
                        1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        MaterialTheme.shapes.small,
                    )
                    .selectable(selected = selected, role = Role.RadioButton, onClick = {}),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    size,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AddToCartBar(price: Long, secondaryLabel: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    Formatters.money(price),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (secondaryLabel != null) {
                    OutlinedButton(onClick = {}, modifier = Modifier.height(48.dp)) { Text(secondaryLabel) }
                    Spacer(Modifier.width(10.dp))
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.height(48.dp),
                ) {
                    Text("Thêm vào giỏ")
                }
            }
        }
    }
}

@Composable
private fun ProductInfo(product: MockProduct) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Text(product.brand.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(product.name, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        PriceLine(product.price, product.compareAt, large = true)
        Spacer(Modifier.height(6.dp))
        Text(
            "Đã bán ${product.sold} · SKU AT-OVS-01",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Text("Màu", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        ColorSwatches(product)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Size", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text("Bảng size", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(8.dp))
        SizeRow()
        Spacer(Modifier.height(6.dp))
        Text(
            "Còn ${product.stock} sản phẩm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(18.dp))
        Text("Mô tả", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            "Cotton 2 chiều 240gsm, form oversize, vai rộng. Giặt máy 30°C, " +
                "không sấy. Sản xuất tại Việt Nam.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))
        Text("Chính sách đổi trả trong 7 ngày", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ProductDetailA() {
    val product = mockProducts[0]
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            GalleryPlaceholder(ratio = 1f)
            Spacer(Modifier.height(18.dp))
            ProductInfo(product)
            Spacer(Modifier.height(24.dp))
            SectionHeader("Có thể bạn thích")
            Spacer(Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
            ) {
                items(mockProducts.size) { index ->
                    ProductCard(mockProducts[index], Modifier.width(150.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        AddToCartBar(product.price)
    }
}

@Composable
fun ProductDetailB() {
    val product = mockProducts[1]
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                }
                Text("Chi tiết sản phẩm", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = "Giỏ")
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(product.name, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                PriceLine(product.price, product.compareAt, large = true)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                mockProducts.take(4).forEach { item ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(3f / 4f)
                            .clip(MaterialTheme.shapes.small)
                            .background(Brush.linearGradient(listOf(item.tones.first, item.tones.second))),
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ProductInfo(product)
            Spacer(Modifier.height(20.dp))
        }
        AddToCartBar(product.price, secondaryLabel = "Mua ngay")
    }
}

@Composable
private fun TopBar(title: String, trailing: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }
    }
}

@Composable
private fun CartLine(line: MockCartLine) {
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .width(84.dp)
                .aspectRatio(3f / 4f)
                .clip(MaterialTheme.shapes.small)
                .background(Brush.linearGradient(listOf(line.product.tones.first, line.product.tones.second))),
        )
        Column(Modifier.weight(1f)) {
            Text(line.product.name, style = MaterialTheme.typography.titleSmall)
            Text(
                "${line.color} / ${line.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            PriceLine(line.product.price)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                QtyStepper(line.quantity)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {}) { Text("Xoá", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun CartScreen() {
    val subtotal = mockCart.sumOf { it.product.price * it.quantity }
    Column(Modifier.fillMaxSize()) {
        TopBar("Giỏ hàng", "2 sản phẩm")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            mockCart.forEach { line ->
                CartLine(line)
                Spacer(Modifier.height(18.dp))
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Mã giảm giá", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("Chọn", style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryRow("Tạm tính", Formatters.money(subtotal))
                SummaryRow("Phí vận chuyển", "Miễn phí")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryRow("Tổng cộng", Formatters.money(subtotal), strong = true)
            }
            Spacer(Modifier.height(20.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(48.dp),
                ) {
                    Text("Thanh toán · ${Formatters.money(subtotal)}")
                }
            }
        }
        BottomNavBar("Giỏ hàng")
    }
}

@Composable
private fun PaymentOption(title: String, subtitle: String, selected: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                MaterialTheme.shapes.medium,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = {})
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CheckoutScreen() {
    val subtotal = mockCart.sumOf { it.product.price * it.quantity }
    Column(Modifier.fillMaxSize()) {
        TopBar("Thanh toán")
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Địa chỉ giao hàng", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = {}) {
                            Text(
                                "Đổi",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Nguyễn Thu Hà · 0901 234 567", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "27 Nguyễn Trãi, Phường Bến Thành, Quận 1, TP.HCM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                        Text(
                            "Mặc định",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Phương thức thanh toán",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PaymentOption("Thanh toán khi nhận hàng", "Trả tiền mặt cho shipper", selected = true)
                PaymentOption("VNPay", "Thẻ ATM / QR", selected = false)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Đơn hàng",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                mockCart.forEach { line ->
                    Row {
                        Text(
                            "${line.product.name} × ${line.quantity}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Formatters.money(line.product.price * line.quantity), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryRow("Phí vận chuyển", "Miễn phí")
                SummaryRow("Tổng cộng", Formatters.money(subtotal), strong = true)
            }
            Spacer(Modifier.height(20.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(48.dp),
                ) {
                    Text("Đặt hàng · ${Formatters.money(subtotal)}")
                }
            }
        }
    }
}
