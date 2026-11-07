package com.stylo.shop.core.navigation

import kotlinx.serialization.Serializable

@Serializable
data object AuthGraph

@Serializable
data object CustomerGraph

@Serializable
data object AdminGraph

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object HomeRoute


@Serializable
data class ProductDetailRoute(val productId: Long)

@Serializable
data object CartRoute

@Serializable
data object CheckoutRoute

@Serializable
data class OrderDetailRoute(val orderId: Long)

@Serializable
data object AdminDashboardRoute
@Serializable
data object AdminProductsRoute
@Serializable
data object AdminProductCreateRoute
@Serializable
data class AdminProductEditRoute(val productId: Long)
@Serializable
data object AdminTaxonomyRoute

@Serializable
data object ProfileRoute

@Serializable
data object AddressListRoute

@Serializable
data object AddressCreateRoute

@Serializable
data class AddressEditRoute(val addressId: Long)
