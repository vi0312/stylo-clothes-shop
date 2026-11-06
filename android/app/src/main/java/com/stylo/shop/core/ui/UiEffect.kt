package com.stylo.shop.core.ui

import androidx.annotation.StringRes
import com.stylo.shop.core.network.ApiError

sealed interface UiEffect {
    data class ShowMessage(val message: String) : UiEffect
    data class ShowMessageRes(
        @param:StringRes val messageRes: Int,
        val args: List<Any> = emptyList(),
    ) : UiEffect
    data class ShowError(val error: ApiError) : UiEffect
}
