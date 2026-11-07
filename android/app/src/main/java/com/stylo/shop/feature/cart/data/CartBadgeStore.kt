package com.stylo.shop.feature.cart.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The bottom-bar cart badge outlives every screen: every cart response updates it in one place,
 * so adding from product detail or clearing at checkout shows up without a manual refresh.
 */
@Singleton
class CartBadgeStore @Inject constructor() {

    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count.asStateFlow()

    fun update(itemCount: Int) {
        _count.value = itemCount.coerceAtLeast(0)
    }

    fun reset() {
        _count.value = 0
    }
}
