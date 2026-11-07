package com.stylo.shop.feature.customer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.core.model.UserRole
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.SessionState
import com.stylo.shop.feature.cart.data.CartBadgeStore
import com.stylo.shop.feature.cart.data.CartRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * Seeds the cart badge from the server once per signed-in customer and clears it for guests, so
 * the tab bar never shows another session's count.
 */
@HiltViewModel
class CustomerShellViewModel @Inject constructor(
    sessionManager: SessionManager,
    private val cartRepository: CartRepository,
    private val badgeStore: CartBadgeStore,
) : ViewModel() {

    val cartCount: StateFlow<Int> = badgeStore.count

    init {
        viewModelScope.launch {
            sessionManager.state
                .distinctUntilChangedBy { state ->
                    (state as? SessionState.Authenticated)?.user?.let { it.id to it.role }
                }
                .collect { state ->
                    val user = (state as? SessionState.Authenticated)?.user
                    if (user != null && user.role == UserRole.CUSTOMER) {
                        cartRepository.getCart()
                    } else {
                        badgeStore.reset()
                    }
                }
        }
    }
}
