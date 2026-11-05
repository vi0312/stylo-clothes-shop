package com.stylo.shop.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind

object ErrorMessages {

    fun resourceId(error: ApiError): Int = when {
        error.code != null -> forCode(error.code)
        error.kind == ApiErrorKind.NETWORK -> R.string.error_network
        error.kind == ApiErrorKind.TIMEOUT -> R.string.error_timeout
        error.kind == ApiErrorKind.PARSE -> R.string.error_parse
        else -> R.string.error_generic
    }

    @StringRes
    fun forCode(code: String): Int = when (code) {
        "VALIDATION_FAILED" -> R.string.error_validation
        "UNAUTHORIZED" -> R.string.error_unauthorized
        "INVALID_CREDENTIALS" -> R.string.error_invalid_credentials
        "INVALID_TOKEN" -> R.string.error_invalid_token
        "FORBIDDEN" -> R.string.error_forbidden
        "ACCOUNT_DISABLED" -> R.string.error_account_disabled
        "NOT_FOUND" -> R.string.error_not_found
        "EMAIL_TAKEN" -> R.string.error_email_taken
        "SKU_TAKEN" -> R.string.error_sku_taken
        "VARIANT_EXISTS" -> R.string.error_variant_exists
        "NAME_TAKEN" -> R.string.error_name_taken
        "IN_USE" -> R.string.error_in_use
        "OUT_OF_STOCK" -> R.string.error_out_of_stock
        "ITEM_UNAVAILABLE" -> R.string.error_item_unavailable
        "CART_EMPTY" -> R.string.error_cart_empty
        "INVALID_ORDER_STATE" -> R.string.error_invalid_order_state
        "ORDER_ALREADY_PAID" -> R.string.error_order_already_paid
        "PAYMENT_NOT_ALLOWED" -> R.string.error_payment_not_allowed
        "CANNOT_DISABLE_SELF" -> R.string.error_cannot_disable_self
        "FILE_TOO_LARGE" -> R.string.error_file_too_large
        "INTERNAL" -> R.string.error_internal
        else -> R.string.error_generic
    }
}

@Composable
fun ApiError.message(): String = stringResource(ErrorMessages.resourceId(this))
