package com.stylo.shop.feature.customer

import com.stylo.shop.FakeAuthRepository
import com.stylo.shop.FakeTokenStore
import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.storage.SessionManager
import com.stylo.shop.core.storage.SessionState
import com.stylo.shop.feature.cart.FakeCartApi
import com.stylo.shop.feature.cart.data.CartBadgeStore
import com.stylo.shop.feature.cart.data.CartDto
import com.stylo.shop.feature.cart.data.DefaultCartRepository
import com.stylo.shop.feature.customer.ui.CustomerShellViewModel
import com.stylo.shop.testTokens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CustomerShellViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    @Test
    fun `a guest keeps the badge empty`() = runTest(dispatcherRule.dispatcher) {
        val badge = CartBadgeStore().apply { update(6) }
        val api = FakeCartApi(CartDto(id = 1, itemCount = 3))
        val repository = DefaultCartRepository(api, badge)
        val session = SessionManager(FakeTokenStore(null), FakeAuthRepository(), this)
        CustomerShellViewModel(session, repository, badge)

        session.bootstrap()
        advanceUntilIdle()

        assertTrue(session.state.value is SessionState.Unauthenticated)
        assertEquals(0, badge.count.value)
    }

    @Test
    fun `an authenticated customer refreshes the badge from the server`() = runTest(dispatcherRule.dispatcher) {
        val badge = CartBadgeStore()
        val api = FakeCartApi(CartDto(id = 1, itemCount = 3))
        val repository = DefaultCartRepository(api, badge)
        val session = SessionManager(FakeTokenStore(testTokens), FakeAuthRepository(), this)
        CustomerShellViewModel(session, repository, badge)

        session.bootstrap()
        advanceUntilIdle()

        assertTrue(session.state.value is SessionState.Authenticated)
        assertEquals(3, badge.count.value)
    }

    @Test
    fun `logging out empties the badge`() = runTest(dispatcherRule.dispatcher) {
        val badge = CartBadgeStore()
        val api = FakeCartApi(CartDto(id = 1, itemCount = 3))
        val repository = DefaultCartRepository(api, badge)
        val session = SessionManager(FakeTokenStore(testTokens), FakeAuthRepository(), this)
        CustomerShellViewModel(session, repository, badge)
        session.bootstrap()
        advanceUntilIdle()
        assertEquals(3, badge.count.value)

        session.logout()
        advanceUntilIdle()

        assertEquals(0, badge.count.value)
    }
}
