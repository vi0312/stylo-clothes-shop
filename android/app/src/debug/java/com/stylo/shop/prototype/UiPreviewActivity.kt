package com.stylo.shop.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.stylo.shop.core.ui.theme.StyloTheme

/**
 * Throwaway host for the ticket-07 UI prototype. Debug source set only; never shipped.
 * Launch with extras: screen, variant, chrome. The prototype always uses the
 * final warm light theme.
 */
class UiPreviewActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startScreen = intent.getIntExtra("screen", PreviewScreen.HOME.ordinal)
        val startVariant = intent.getIntExtra("variant", 0)
        val chrome = intent.getBooleanExtra("chrome", true)

        setContent {
            var screen by rememberSaveable { mutableIntStateOf(startScreen) }
            var variant by rememberSaveable { mutableIntStateOf(startVariant) }

            StyloTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .then(if (!chrome) Modifier.navigationBarsPadding() else Modifier),
                    ) {
                        PreviewContent(
                            screen = PreviewScreen.entries[screen.coerceIn(0, PreviewScreen.entries.lastIndex)],
                            variant = variant,
                        )
                    }
                    if (chrome) {
                        PreviewBar(
                            screenIndex = screen,
                            variant = variant,
                            onScreen = { screen = it; variant = 0 },
                            onVariant = { variant = it },
                        )
                    }
                }
            }
        }
    }
}
}

enum class PreviewScreen(val label: String, val variants: List<String>) {
    TOKENS("Design system", listOf("Màu + thành phần")),
    HOME("Trang chủ", listOf("A · Editorial feed", "B · Catalog dày", "C · Lookbook bento")),
    PRODUCT("Chi tiết SP", listOf("A · Ảnh trước", "B · Thông tin trước")),
    CART("Giỏ hàng", listOf("A · Giỏ hàng", "B · Thanh toán")),
    ADMIN_DASH("Dashboard", listOf("A · Thẻ KPI", "B · Bảng dày")),
    ADMIN_PRODUCTS("Sản phẩm admin", listOf("A · Danh sách")),
}

@Composable
private fun PreviewContent(screen: PreviewScreen, variant: Int) {
    when (screen) {
        PreviewScreen.TOKENS -> TokenSheet()
        PreviewScreen.HOME -> when (variant.coerceIn(0, 2)) {
            0 -> HomeA()
            1 -> HomeB()
            else -> HomeC()
        }

        PreviewScreen.PRODUCT -> if (variant == 0) ProductDetailA() else ProductDetailB()
        PreviewScreen.CART -> if (variant == 0) CartScreen() else CheckoutScreen()
        PreviewScreen.ADMIN_DASH -> if (variant == 0) AdminDashboardA() else AdminDashboardB()
        PreviewScreen.ADMIN_PRODUCTS -> AdminProducts()
    }
}

@Composable
private fun MiniChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = CircleShape,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun PreviewBar(
    screenIndex: Int,
    variant: Int,
    onScreen: (Int) -> Unit,
    onVariant: (Int) -> Unit,
) {
    val screen = PreviewScreen.entries[screenIndex.coerceIn(0, PreviewScreen.entries.lastIndex)]
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PreviewScreen.entries.forEachIndexed { index, item ->
                    MiniChip(item.label, screenIndex == index) { onScreen(index) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiniChip("‹", false) { if (variant > 0) onVariant(variant - 1) }
                Spacer(Modifier.width(8.dp))
                Text(
                    screen.variants.getOrElse(variant) { screen.variants.first() },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                MiniChip("›", false) {
                    if (variant < screen.variants.lastIndex) onVariant(variant + 1)
                }
            }
        }
    }
}
