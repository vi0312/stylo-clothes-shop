package com.stylo.shop.core.ui

import com.stylo.shop.core.network.ApiError

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data class Error(val error: ApiError) : UiState<Nothing>
}
