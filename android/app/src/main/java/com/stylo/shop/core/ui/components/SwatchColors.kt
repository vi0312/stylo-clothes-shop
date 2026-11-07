package com.stylo.shop.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Swatch fill for a colour name coming from the catalog. Unknown names fall back to a neutral
 * sand so a swatch is never invisible; the name is always announced separately.
 */
fun swatchColor(name: String): Color = when (name.trim().lowercase()) {
    "đen", "black" -> Color(0xFF1E1C18)
    "trắng", "white" -> Color(0xFFFFFFFF)
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

/** Check mark drawn on top of a selected swatch: dark ink on light fills, paper on dark fills. */
fun swatchCheckTint(name: String): Color =
    if (swatchColor(name).luminance() > 0.5f) Color(0xFF1E1C18) else Color(0xFFFBF8F1)
