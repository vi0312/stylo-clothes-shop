package com.stylo.shop.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import com.stylo.shop.R

/**
 * Brand marks ship as monochrome vectors: the ink comes from [MaterialTheme] so the logo can never
 * drift away from the palette in `docs/ui.md`.
 */
@Composable
fun StyloLockupHorizontal(modifier: Modifier = Modifier) {
    BrandMark(R.drawable.ic_stylo_lockup_horizontal, modifier)
}

@Composable
fun StyloLockupStacked(modifier: Modifier = Modifier) {
    BrandMark(R.drawable.ic_stylo_lockup_stacked, modifier)
}

@Composable
private fun BrandMark(@DrawableRes drawable: Int, modifier: Modifier) {
    Image(
        painter = painterResource(drawable),
        contentDescription = "Stylo",
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
        modifier = modifier,
    )
}
