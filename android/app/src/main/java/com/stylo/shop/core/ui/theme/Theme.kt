package com.stylo.shop.core.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

private val Ink = Color(0xFF1E1C18)
private val Ivory = Color(0xFFF6F1E8)
private val Paper = Color(0xFFFBF8F1)
private val Sand = Color(0xFFEDE5D8)
private val Muted = Color(0xFF6C6255)
private val Terracotta = Color(0xFFA6512B)
private val OutlineStrong = Color(0xFF8F8069)
private val OutlineSoft = Color(0xFFE8DFD1)

/**
 * Palette is intentionally light-only and static (no dynamic color): the product
 * direction is the warm ivory/ink/terracotta scheme chosen in ticket 07.
 */
internal val StyloScheme = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = Color(0xFFE4DACB),
    onPrimaryContainer = Ink,
    secondary = Terracotta,
    onSecondary = Color(0xFFFFF6EF),
    secondaryContainer = Color(0xFFF5DFD4),
    onSecondaryContainer = Color(0xFF3F1B0C),
    tertiary = Color(0xFF4F5E3F),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E9D9),
    onTertiaryContainer = Color(0xFF1A2412),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Ivory,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Sand,
    onSurfaceVariant = Muted,
    surfaceDim = Color(0xFFE9E3D8),
    surfaceBright = Color(0xFFFFFBF5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Paper,
    surfaceContainer = Ivory,
    surfaceContainerHigh = Color(0xFFF0E9DD),
    surfaceContainerHighest = Sand,
    inverseSurface = Color(0xFF332F29),
    inverseOnSurface = Color(0xFFF8F3EA),
    inversePrimary = Color(0xFFD9CEBD),
    outline = OutlineStrong,
    outlineVariant = OutlineSoft,
    scrim = Color(0xFF000000),
    surfaceTint = Ink,
)

/** Status roles are not part of [androidx.compose.material3.ColorScheme]; keep them paired. */
@Immutable
data class StyloStatusColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
    val indigo: Color,
    val indigoContainer: Color,
    val onIndigoContainer: Color,
    val neutral: Color,
    val neutralContainer: Color,
    val onNeutralContainer: Color,
)

internal val WarmStatusColors = StyloStatusColors(
    success = Color(0xFF256B45),
    successContainer = Color(0xFFE3F4EA),
    onSuccessContainer = Color(0xFF256B45),
    warning = Color(0xFF8A5300),
    warningContainer = Color(0xFFFDF0D9),
    onWarningContainer = Color(0xFF8A5300),
    info = Color(0xFF1C4E80),
    infoContainer = Color(0xFFE4EFFA),
    onInfoContainer = Color(0xFF1C4E80),
    indigo = Color(0xFF3D3A8C),
    indigoContainer = Color(0xFFE9E8FA),
    onIndigoContainer = Color(0xFF3D3A8C),
    neutral = Color(0xFF5B5B5B),
    neutralContainer = Color(0xFFEFEFEF),
    onNeutralContainer = Color(0xFF5B5B5B),
)

private val LocalStyloStatusColors = staticCompositionLocalOf { WarmStatusColors }

val MaterialTheme.statusColors: StyloStatusColors
    @Composable
    @ReadOnlyComposable
    get() = LocalStyloStatusColors.current

private fun heading(size: Int, lineHeight: Int): TextStyle = TextStyle(
    fontFamily = FontFamily.Serif,
    fontWeight = FontWeight.SemiBold,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private fun sans(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: TextUnit = 0.sp,
): TextStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking,
)

private val StyloTypography = Typography(
    displayLarge = heading(57, 64),
    displayMedium = heading(45, 52),
    displaySmall = heading(40, 44),
    headlineLarge = heading(32, 40),
    headlineMedium = heading(28, 36),
    headlineSmall = heading(24, 30),
    titleLarge = heading(18, 24),
    titleMedium = sans(16, 24, FontWeight.SemiBold),
    titleSmall = sans(14, 20, FontWeight.SemiBold),
    bodyLarge = sans(16, 24),
    bodyMedium = sans(14, 20),
    bodySmall = sans(12, 16),
    labelLarge = sans(14, 20, FontWeight.Medium, 0.1.sp),
    labelMedium = sans(12, 16, FontWeight.Medium, 0.5.sp),
    labelSmall = sans(11, 16, FontWeight.Normal, 1.5.sp),
)

private val StyloShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun StyloTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
    }
    CompositionLocalProvider(LocalStyloStatusColors provides WarmStatusColors) {
        MaterialTheme(
            colorScheme = StyloScheme,
            typography = StyloTypography,
            shapes = StyloShapes,
            content = content,
        )
    }
}
