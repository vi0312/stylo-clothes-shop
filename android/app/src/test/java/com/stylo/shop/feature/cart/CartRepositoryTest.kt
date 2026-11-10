package com.stylo.shop.feature.cart

import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.cart.data.AddCartItemRequest
import com.stylo.shop.feature.cart.data.CartApi
import com.stylo.shop.feature.cart.data.CartBadgeStore
import com.stylo.shop.feature.cart.data.CartDto
import com.stylo.shop.feature.cart.data.DefaultCartRepository
import com.stylo.shop.feature.cart.data.UpdateCartItemRequest
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeCartApi(var cart: CartDto = CartDto()) : CartApi {

    var failWith: Exception? = null

    private fun respond(): CartDto = failWith?.let { throw it } ?: cart

    override suspend fun getCart(): CartDto = respond()

    override suspend fun addItem(body: AddCartItemRequest): CartDto = respond()

    override suspend fun updateItem(itemId: Long, body: UpdateCartItemRequest): CartDto = respond()

    override suspend fun removeItem(itemId: Long): CartDto = respond()

    override suspend fun clearCart(): CartDto = respond()
}

class CartRepositoryTest {

    @Test
    fun `every successful response updates the badge`() = runTest {
        val badge = CartBadgeStore()
        val api = FakeCartApi(CartDto(id = 1, itemCount = 2))
        val repository = DefaultCartRepository(api, badge)

        repository.getCart()
        assertEquals(2, badge.count.value)

        api.cart = CartDto(id = 1, itemCount = 5)
        repository.addItem(variantId = 7, quantity = 3)
        assertEquals(5, badge.count.value)

        api.cart = CartDto(id = 1, itemCount = 0)
        repository.clear()
        assertEquals(0, badge.count.value)
    }

    @Test
    fun `the mapped cart carries the server shipping fee`() = runTest {
        val api = FakeCartApi(CartDto(id = 1, subtotal = 398_000, shippingFee = 30_000))
        val repository = DefaultCartRepository(api, CartBadgeStore())

        val result = repository.getCart()

        val cart = (result as ApiResult.Success).data
        assertEquals(30_000L, cart.shippingFee)
        assertEquals(428_000L, cart.total)
    }

    @Test
    fun `a failed request leaves the badge untouched`() = runTest {
        val badge = CartBadgeStore()
        badge.update(4)
        val api = FakeCartApi().apply { failWith = IOException("offline") }
        val repository = DefaultCartRepository(api, badge)

        val result = repository.getCart()

        assertTrue(result is ApiResult.Failure)
        assertEquals(4, badge.count.value)
    }
}
