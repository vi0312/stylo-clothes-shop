package com.stylo.shop.prototype

import androidx.compose.ui.graphics.Color

/**
 * Throwaway mock data for the UI prototype (ticket 07). Not wired to the API.
 */
data class MockProduct(
    val name: String,
    val brand: String,
    val category: String,
    val price: Long,
    val compareAt: Long? = null,
    val badge: String? = null,
    val swatches: List<Color>,
    val sold: Int,
    val tones: Pair<Color, Color>,
    val stock: Int = 12,
)

val mockProducts = listOf(
    MockProduct(
        name = "Áo thun oversize Alpha",
        brand = "Stylo Basics",
        category = "Áo",
        price = 289_000,
        badge = "Mới",
        swatches = listOf(Color(0xFF1B1B1B), Color(0xFFF2EEE6), Color(0xFFC9B99A)),
        sold = 214,
        tones = Color(0xFF3A3A38) to Color(0xFF101010),
    ),
    MockProduct(
        name = "Quần jogger Kaki",
        brand = "Stylo",
        category = "Quần",
        price = 459_000,
        compareAt = 520_000,
        swatches = listOf(Color(0xFF8C7452), Color(0xFF2C2C2C), Color(0xFF5C6650)),
        sold = 158,
        tones = Color(0xFFC2AE8C) to Color(0xFF7A6647),
    ),
    MockProduct(
        name = "Sơ mi linen Hạ",
        brand = "Local Atelier",
        category = "Áo",
        price = 520_000,
        swatches = listOf(Color(0xFFF4F1E8), Color(0xFF9FB4C7), Color(0xFFD9C7B8)),
        sold = 96,
        tones = Color(0xFFD8E2EA) to Color(0xFF8FA6B6),
    ),
    MockProduct(
        name = "Áo khoác bomber Nocturne",
        brand = "Nocturne",
        category = "Khoác",
        price = 890_000,
        badge = "Còn 3",
        swatches = listOf(Color(0xFF161616), Color(0xFF3D4A3A)),
        sold = 41,
        tones = Color(0xFF2B2E33) to Color(0xFF0C0D0F),
        stock = 3,
    ),
    MockProduct(
        name = "Váy midi Saigon",
        brand = "Local Atelier",
        category = "Váy",
        price = 610_000,
        swatches = listOf(Color(0xFF7E3B2F), Color(0xFF1F2933)),
        sold = 73,
        tones = Color(0xFFA6624F) to Color(0xFF6B2E22),
    ),
    MockProduct(
        name = "Polo pique Essential",
        brand = "Stylo Basics",
        category = "Áo",
        price = 349_000,
        swatches = listOf(Color(0xFF23384D), Color(0xFFF0EDE6)),
        sold = 302,
        tones = Color(0xFF37516B) to Color(0xFF1B2A3A),
    ),
    MockProduct(
        name = "Áo thun basic Nhi",
        brand = "Stylo Basics",
        category = "Áo",
        price = 249_000,
        swatches = listOf(Color(0xFFF2EEE6), Color(0xFF9FB4C7)),
        sold = 188,
        tones = Color(0xFFE3DDD2) to Color(0xFFB7AC9C),
    ),
    MockProduct(
        name = "Quần tây Thủy",
        brand = "Stylo",
        category = "Quần",
        price = 529_000,
        swatches = listOf(Color(0xFF2C2C2C), Color(0xFF8C7452)),
        sold = 84,
        tones = Color(0xFF3B3B3B) to Color(0xFF141414),
    ),
    MockProduct(
        name = "Quần short Kaki",
        brand = "Stylo",
        category = "Quần",
        price = 359_000,
        swatches = listOf(Color(0xFFC9B99A), Color(0xFF5C6650)),
        sold = 127,
        tones = Color(0xFFD2BE9C) to Color(0xFF8A754F),
    ),
    MockProduct(
        name = "Quần jeans Slim",
        brand = "Local Atelier",
        category = "Quần",
        price = 649_000,
        swatches = listOf(Color(0xFF23384D), Color(0xFF161616)),
        sold = 66,
        tones = Color(0xFF3E5570) to Color(0xFF1A2431),
    ),
)

data class MockOrder(
    val code: String,
    val customer: String,
    val total: Long,
    val status: String,
    val time: String,
)

val mockOrders = listOf(
    MockOrder("DH-24101", "Nguyễn Thu Hà", 1_219_000, "Chờ xử lý", "05/11 09:42"),
    MockOrder("DH-24100", "Trần Minh Khoa", 890_000, "Đang giao", "05/11 08:15"),
    MockOrder("DH-24099", "Lê Phương Anh", 459_000, "Đã giao", "04/11 21:03"),
    MockOrder("DH-24098", "Phạm Quốc Bảo", 2_040_000, "Đã xác nhận", "04/11 17:28"),
    MockOrder("DH-24097", "Đỗ Khánh Linh", 349_000, "Đã huỷ", "04/11 14:52"),
)

data class MockCartLine(
    val product: MockProduct,
    val size: String,
    val color: String,
    val quantity: Int,
)

val mockCart = listOf(
    MockCartLine(mockProducts[0], "L", "Đen", 1),
    MockCartLine(mockProducts[1], "M", "Be", 1),
)

val categories = listOf("Áo", "Quần", "Váy", "Khoác", "Phụ kiện")
