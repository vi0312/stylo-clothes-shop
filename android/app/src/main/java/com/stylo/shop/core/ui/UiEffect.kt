package com.stylo.shop.core.ui

sealed interface UiEffect {
    data class ShowMessage(val message: String) : UiEffect
}
