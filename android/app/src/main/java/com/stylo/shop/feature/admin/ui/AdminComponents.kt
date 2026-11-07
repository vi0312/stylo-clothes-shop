package com.stylo.shop.feature.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stylo.shop.BuildConfig
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.theme.statusColors
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.CatalogStatus

@Composable
fun AdminConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(confirmLabel, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun AdminSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun AdminErrorBanner(error: ApiError, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = stringResource(ErrorMessages.resourceId(error)),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun adminFieldErrorText(
    error: AdminFieldError,
    maxLength: Int = 0,
): String = when (error) {
    AdminFieldError.REQUIRED ->
        stringResource(R.string.admin_field_required)
    AdminFieldError.TOO_LONG ->
        stringResource(R.string.admin_field_too_long, maxLength)
    AdminFieldError.INVALID_NUMBER ->
        stringResource(R.string.admin_field_invalid_number)
    AdminFieldError.INVALID_PRICE ->
        stringResource(R.string.admin_field_invalid_price)
    AdminFieldError.INVALID_QUANTITY ->
        stringResource(R.string.admin_field_invalid_quantity)
    AdminFieldError.INVALID_COST ->
        stringResource(R.string.admin_field_invalid_cost)
    AdminFieldError.SKU_TAKEN ->
        stringResource(R.string.admin_variant_sku_taken)
    AdminFieldError.VARIANT_EXISTS ->
        stringResource(R.string.admin_variant_duplicate)
    AdminFieldError.NAME_TAKEN ->
        stringResource(R.string.admin_taxonomy_name_taken)
}

@Composable
fun AdminStatusBadge(
    text: String,
    active: Boolean,
    inactiveColor: Color = MaterialTheme.statusColors.neutral,
    inactiveContainer: Color = MaterialTheme.statusColors.neutralContainer,
) {
    val color = if (active) MaterialTheme.statusColors.success else inactiveColor
    val container = if (active) MaterialTheme.statusColors.successContainer else inactiveContainer
    Surface(
        color = container,
        contentColor = color,
        shape = CircleShape,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
fun ProductStatusBadge(product: AdminProductSummary) {
    when {
        product.status == CatalogStatus.INACTIVE -> AdminStatusBadge(
            text = stringResource(R.string.admin_filter_inactive),
            active = false,
        )
        product.isLowStock -> AdminStatusBadge(
            text = stringResource(R.string.admin_status_low_stock),
            active = false,
            inactiveColor = MaterialTheme.statusColors.warning,
            inactiveContainer = MaterialTheme.statusColors.warningContainer,
        )
        else -> AdminStatusBadge(
            text = stringResource(R.string.admin_filter_active),
            active = true,
        )
    }
}

@Composable
fun TaxonomyStatusBadge(status: CatalogStatus) {
    AdminStatusBadge(
        text = stringResource(
            if (status == CatalogStatus.ACTIVE) {
                R.string.admin_status_active_taxonomy
            } else {
                R.string.admin_status_inactive_taxonomy
            },
        ),
        active = status == CatalogStatus.ACTIVE,
    )
}

@Composable
fun AdminSearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(placeholder, style = MaterialTheme.typography.bodyMedium)
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Clear,
                        contentDescription = stringResource(R.string.catalog_filter_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.height(52.dp),
    )
}

@Composable
fun AdminEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun AdminErrorState(
    error: ApiError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(ErrorMessages.resourceId(error)),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Composable
fun AdminListSkeleton(
    rows: Int = 5,
    thumbnail: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(rows) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (thumbnail) {
                    Box(
                        Modifier
                            .width(46.dp)
                            .height(61.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.shapes.extraSmall,
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .fillMaxWidth(0.45f)
                            .height(11.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(4.dp),
                            ),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .width(64.dp)
                        .height(20.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
fun AdminProductThumbnail(
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
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.shapes.extraSmall,
            ),
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
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
