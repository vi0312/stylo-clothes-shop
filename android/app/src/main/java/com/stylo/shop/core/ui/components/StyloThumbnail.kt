package com.stylo.shop.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stylo.shop.BuildConfig
import com.stylo.shop.core.network.ApiClient
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.style.TextAlign

/**
 * Product thumbnail with the placeholder the spec asks for: sand block plus the product initial,
 * never a "broken image" icon (docs/ui.md §Ảnh).
 */
@Composable
fun StyloThumbnail(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    width: Dp = 46.dp,
    height: Dp = 61.dp,
) {
    val resolved = url?.let { ApiClient.absoluteUrl(BuildConfig.API_BASE_URL, it) }
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        if (!resolved.isNullOrBlank()) {
            AsyncImage(
                model = resolved,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // docs/ui.md §Ảnh: the placeholder is the product name, never a broken-image icon.
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
