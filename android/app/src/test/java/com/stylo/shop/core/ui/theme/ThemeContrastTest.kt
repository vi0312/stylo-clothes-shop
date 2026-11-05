package com.stylo.shop.core.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the WCAG checks from the MD3 audit: control boundaries need 3:1, text pairs 4.5:1.
 */
class ThemeContrastTest {

    @Test
    fun `control outline meets non text contrast`() {
        assertTrue(contrast(StyloScheme.outline, StyloScheme.surface) >= 3.0)
        assertTrue(contrast(StyloScheme.outline, StyloScheme.background) >= 3.0)
        assertTrue(contrast(StyloScheme.outline, StyloScheme.surfaceVariant) >= 3.0)
    }

    @Test
    fun `primary text pairs meet AA`() {
        assertTrue(contrast(StyloScheme.onSurface, StyloScheme.surface) >= 4.5)
        assertTrue(contrast(StyloScheme.onSurfaceVariant, StyloScheme.surfaceVariant) >= 4.5)
        assertTrue(contrast(StyloScheme.secondary, StyloScheme.background) >= 4.5)
        assertTrue(contrast(StyloScheme.error, StyloScheme.surface) >= 4.5)
    }

    @Test
    fun `status pairs meet AA`() {
        val status = WarmStatusColors
        assertTrue(contrast(status.success, status.successContainer) >= 4.5)
        assertTrue(contrast(status.warning, status.warningContainer) >= 4.5)
        assertTrue(contrast(status.info, status.infoContainer) >= 4.5)
        assertTrue(contrast(status.indigo, status.indigoContainer) >= 4.5)
        assertTrue(contrast(status.neutral, status.neutralContainer) >= 4.5)
    }

    private fun contrast(foreground: Color, background: Color): Double {
        val lighter = maxOf(luminance(foreground), luminance(background))
        val darker = minOf(luminance(foreground), luminance(background))
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun luminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
