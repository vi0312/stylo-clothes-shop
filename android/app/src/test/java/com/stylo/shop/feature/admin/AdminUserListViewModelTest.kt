package com.stylo.shop.feature.admin

import com.stylo.shop.MainDispatcherRule
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiErrorKind
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.model.AdminUserStatus
import com.stylo.shop.feature.admin.model.Paginated
import com.stylo.shop.feature.admin.model.USER_PAGE_SIZE
import com.stylo.shop.feature.admin.ui.users.AdminUserListViewModel
import com.stylo.shop.feature.admin.ui.users.AdminUserStatusFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminUserListViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private fun advance() = dispatcherRule.dispatcher.scheduler.advanceUntilIdle()

    private fun content(viewModel: AdminUserListViewModel) =
        (viewModel.uiState.value.usersState as UiState.Content).data

    @Test
    fun `loads the first page without filters`() = runTest {
        val repository = FakeAdminUsersRepository()

        val viewModel = AdminUserListViewModel(repository)
        advance()

        val query = repository.queries.single()
        assertEquals(1, query.page)
        assertEquals(USER_PAGE_SIZE, query.limit)
        assertNull(query.q)
        assertNull(query.status)
        assertEquals(1, content(viewModel).size)
        assertEquals(1, viewModel.uiState.value.total)
    }

    @Test
    fun `search trims the query and resets to the first page`() = runTest {
        val repository = FakeAdminUsersRepository(
            pageResults = listOf(
                ApiResult.Success(
                    Paginated(listOf(testAdminUser()), page = 1, limit = 20, total = 1),
                ),
                ApiResult.Success(
                    Paginated(listOf(testAdminUser()), page = 1, limit = 20, total = 1),
                ),
            ),
        )
        val viewModel = AdminUserListViewModel(repository)
        advance()

        viewModel.onSearchQueryChange("  binh  ")
        viewModel.onSearch()
        advance()

        assertEquals("binh", repository.queries.last().q)
        assertEquals(1, repository.queries.last().page)
    }

    @Test
    fun `the status filter overrides the query and reloads the first page`() = runTest {
        val repository = FakeAdminUsersRepository(
            pageResults = listOf(
                ApiResult.Success(
                    Paginated(listOf(testAdminUser()), page = 1, limit = 20, total = 1),
                ),
                ApiResult.Success(
                    Paginated(emptyList(), page = 1, limit = 20, total = 0),
                ),
            ),
        )
        val viewModel = AdminUserListViewModel(repository)
        advance()

        viewModel.onStatusFilterSelected(AdminUserStatusFilter.DISABLED)
        advance()

        assertEquals(AdminUserStatus.DISABLED, repository.queries.last().status)
        assertTrue(content(viewModel).isEmpty())
    }

    @Test
    fun `load more appends the next page and stops at the total`() = runTest {
        val firstPage = (1L..20L).map { testAdminUser(id = it) }
        val repository = FakeAdminUsersRepository(
            pageResults = listOf(
                ApiResult.Success(
                    Paginated(items = firstPage, page = 1, limit = 20, total = 21),
                ),
                ApiResult.Success(
                    Paginated(listOf(testAdminUser(id = 21)), page = 2, limit = 20, total = 21),
                ),
            ),
        )
        val viewModel = AdminUserListViewModel(repository)
        advance()

        viewModel.loadMore()
        advance()

        assertEquals(2, repository.queries.last().page)
        assertEquals((1L..21L).toList(), content(viewModel).map { it.id })
        assertTrue(!viewModel.uiState.value.hasMore)

        viewModel.loadMore()
        advance()
        assertEquals(2, repository.queries.size)
    }

    @Test
    fun `disabling posts DISABLED, reports success and refetches the list`() = runTest {
        val repository = FakeAdminUsersRepository(
            pageResults = listOf(
                ApiResult.Success(
                    Paginated(listOf(testAdminUser()), page = 1, limit = 20, total = 1),
                ),
                ApiResult.Success(
                    Paginated(emptyList(), page = 1, limit = 20, total = 0),
                ),
            ),
        )
        val viewModel = AdminUserListViewModel(repository)
        advance()
        val effect = async { viewModel.effects.first() }

        viewModel.setDisabled(testAdminUser(id = 7), disabled = true)
        advance()

        assertEquals(listOf(7L to AdminUserStatus.DISABLED), repository.updates)
        assertTrue(effect.await() is UiEffect.ShowMessageRes)
        assertEquals(2, repository.queries.size)
        assertNull(viewModel.uiState.value.updatingUserId)
    }

    @Test
    fun `enabling posts ACTIVE`() = runTest {
        val repository = FakeAdminUsersRepository(
            updateResult = ApiResult.Success(
                testAdminUser(status = AdminUserStatus.ACTIVE),
            ),
        )
        val viewModel = AdminUserListViewModel(repository)
        advance()
        val effect = async { viewModel.effects.first() }

        viewModel.setDisabled(
            testAdminUser(status = AdminUserStatus.DISABLED),
            disabled = false,
        )
        advance()

        assertEquals(listOf(7L to AdminUserStatus.ACTIVE), repository.updates)
        assertTrue(effect.await() is UiEffect.ShowMessageRes)
    }

    @Test
    fun `a failed toggle shows the error and refetches`() = runTest {
        val failure = ApiResult.Failure(ApiError(ApiErrorKind.HTTP))
        val repository = FakeAdminUsersRepository(
            pageResults = listOf(
                ApiResult.Success(
                    Paginated(listOf(testAdminUser()), page = 1, limit = 20, total = 1),
                ),
                ApiResult.Success(
                    Paginated(listOf(testAdminUser()), page = 1, limit = 20, total = 1),
                ),
            ),
            updateResult = failure,
        )
        val viewModel = AdminUserListViewModel(repository)
        advance()
        val effect = async { viewModel.effects.first() }

        viewModel.setDisabled(testAdminUser(), disabled = true)
        advance()

        assertTrue(effect.await() is UiEffect.ShowError)
        assertEquals(2, repository.queries.size)
        assertNull(viewModel.uiState.value.updatingUserId)
    }

    @Test
    fun `a second toggle while updating does not post twice`() = runTest {
        val repository = FakeAdminUsersRepository()
        val viewModel = AdminUserListViewModel(repository)
        advance()

        viewModel.setDisabled(testAdminUser(), disabled = true)
        viewModel.setDisabled(testAdminUser(), disabled = true)
        advance()

        assertEquals(1, repository.updates.size)
    }
}
