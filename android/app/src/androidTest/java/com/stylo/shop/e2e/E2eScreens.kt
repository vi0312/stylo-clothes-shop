package com.stylo.shop.e2e

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.stylo.shop.core.ui.UiTags
import io.github.kakaocup.compose.node.element.ComposeScreen

/**
 * Screen objects shared by the Kaspresso journeys: one selector per tag a journey touches, so a
 * tag rename breaks here once instead of inside every test body.
 */
internal class E2eAuthScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eAuthScreen>(provider) {
    val loginTab = onNode { hasTestTag(UiTags.AuthLoginTab) }
    val registerTab = onNode { hasTestTag(UiTags.AuthRegisterTab) }
    val fullName = onNode { hasTestTag(UiTags.AuthFullName) }
    val phone = onNode { hasTestTag(UiTags.AuthPhone) }
    val email = onNode { hasTestTag(UiTags.AuthEmail) }
    val password = onNode { hasTestTag(UiTags.AuthPassword) }
    val submit = onNode { hasTestTag(UiTags.AuthSubmit) }
}

internal class E2eCatalogScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eCatalogScreen>(provider) {
    val search = onNode { hasTestTag(UiTags.CatalogSearch) }
    val profile = onNode { hasTestTag(UiTags.CatalogProfile) }
    val cart = onNode { hasTestTag(UiTags.CatalogCart) }
    val filter = onNode { hasTestTag(UiTags.FilterOpen) }
    val apply = onNode { hasTestTag(UiTags.FilterApply) }
    val sort = onNode { hasTestTag(UiTags.CatalogSort) }

    fun category(name: String) = onNode {
        hasTestTag(UiTags.FilterCategory)
        hasText(name)
    }

    fun sortOption(name: String) = onNode { hasText(name) }

    fun card(name: String) = onNode {
        hasTestTag(UiTags.ProductCard)
        hasText(name)
    }
}

internal class E2eProductDetailScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eProductDetailScreen>(provider) {
    val name = onNode { hasTestTag(UiTags.ProductDetailName) }
    val price = onNode { hasTestTag(UiTags.ProductDetailPrice) }
    val back = onNode { hasTestTag(UiTags.ProductDetailBack) }
    val cta = onNode { hasTestTag(UiTags.ProductDetailCta) }
}

internal class E2eCartScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eCartScreen>(provider) {
    val back = onNode { hasTestTag(UiTags.CartBack) }
    val itemName = onNode { hasTestTag(UiTags.CartItemName) }
    val subtotal = onNode { hasTestTag(UiTags.CartSubtotal) }
    val checkout = onNode { hasTestTag(UiTags.CartCheckout) }
}

internal class E2eCheckoutScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eCheckoutScreen>(provider) {
    val addressChange = onNode { hasTestTag(UiTags.CheckoutAddressChange) }
    val paymentCod = onNode { hasTestTag(UiTags.CheckoutPaymentCod) }
    val paymentOnline = onNode { hasTestTag(UiTags.CheckoutPaymentOnline) }
    val note = onNode { hasTestTag(UiTags.CheckoutNote) }
    val submit = onNode { hasTestTag(UiTags.CheckoutSubmit) }
    val error = onNode { hasTestTag(UiTags.CheckoutError) }
}

internal class E2eOrderDetailScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eOrderDetailScreen>(provider) {
    val code = onNode { hasTestTag(UiTags.OrderDetailCode) }
    val status = onNode { hasTestTag(UiTags.OrderDetailStatus) }
    val payment = onNode { hasTestTag(UiTags.OrderDetailPayment) }
    val total = onNode { hasTestTag(UiTags.OrderDetailTotal) }
    val paymentLabel = onNode { hasText("Tải lại đơn") }
}

internal class E2eProfileScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eProfileScreen>(provider) {
    val email = onNode { hasTestTag(UiTags.ProfileEmail) }
    val addresses = onNode { hasTestTag(UiTags.ProfileAddresses) }
    val logout = onNode { hasTestTag(UiTags.ProfileLogout) }
    val confirmLogout = onNode { hasTestTag(UiTags.ProfileLogoutConfirm) }
}

internal class E2eAddressListScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eAddressListScreen>(provider) {
    val add = onNode { hasTestTag(UiTags.AddressAdd) }
    val back = onNode { hasTestTag(UiTags.ProfileBack) }
    val edit = onNode { hasText("Sửa") }
    val setDefault = onNode { hasText("Đặt làm mặc định") }

    fun header(recipient: String) = onNode {
        hasTestTag(UiTags.AddressHeader)
        hasText(recipient)
    }
}

internal class E2eAddressFormScreen(provider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<E2eAddressFormScreen>(provider) {
    val recipient = onNode { hasTestTag(UiTags.AddressRecipient) }
    val phone = onNode { hasTestTag(UiTags.AddressPhone) }
    val line = onNode { hasTestTag(UiTags.AddressLine) }
    val save = onNode { hasTestTag(UiTags.AddressSave) }
}
