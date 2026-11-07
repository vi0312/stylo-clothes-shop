package com.stylo.shop.feature.admin.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.components.StyloStatusBadge
import com.stylo.shop.core.ui.components.StyloTone
import com.stylo.shop.feature.admin.model.AdminFieldError
import com.stylo.shop.feature.admin.model.AdminProductSummary
import com.stylo.shop.feature.admin.model.CatalogStatus

/** Inline banner for a load failure that does not blank the screen. */
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
fun ProductStatusBadge(product: AdminProductSummary) {
    when {
        product.status == CatalogStatus.INACTIVE -> StyloStatusBadge(
            text = stringResource(R.string.admin_filter_inactive),
            tone = StyloTone.Neutral,
        )
        product.isLowStock -> StyloStatusBadge(
            text = stringResource(R.string.admin_status_low_stock),
            tone = StyloTone.Warning,
        )
        else -> StyloStatusBadge(
            text = stringResource(R.string.admin_filter_active),
            tone = StyloTone.Success,
        )
    }
}

@Composable
fun TaxonomyStatusBadge(status: CatalogStatus) {
    StyloStatusBadge(
        text = stringResource(
            if (status == CatalogStatus.ACTIVE) {
                R.string.admin_status_active_taxonomy
            } else {
                R.string.admin_status_inactive_taxonomy
            },
        ),
        tone = if (status == CatalogStatus.ACTIVE) StyloTone.Success else StyloTone.Neutral,
    )
}
