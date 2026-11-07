package com.stylo.shop.feature.admin.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stylo.shop.R
import com.stylo.shop.core.network.ApiError
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.ui.UiEffect
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.feature.admin.data.AdminUsersRepository
import com.stylo.shop.feature.admin.model.AdminUser
import com.stylo.shop.feature.admin.model.AdminUserQuery
import com.stylo.shop.feature.admin.model.AdminUserStatus
import com.stylo.shop.feature.admin.model.USER_PAGE_SIZE
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminUserStatusFilter(val status: AdminUserStatus?) {
    ALL(null),
    ACTIVE(AdminUserStatus.ACTIVE),
    DISABLED(AdminUserStatus.DISABLED),
}

data class AdminUserListUiState(
    val usersState: UiState<List<AdminUser>> = UiState.Loading,
    val statusFilter: AdminUserStatusFilter = AdminUserStatusFilter.ALL,
    val searchQuery: String = "",
    val page: Int = 1,
    val limit: Int = USER_PAGE_SIZE,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val loadMoreError: ApiError? = null,
    val updatingUserId: Long? = null,
) {
    val hasMore: Boolean
        get() = limit > 0 && page * limit < total
}

@HiltViewModel
class AdminUserListViewModel @Inject constructor(
    private val repository: AdminUsersRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AdminUserListUiState())
    val uiState: StateFlow<AdminUserListUiState> = _uiState.asStateFlow()

    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects: Flow<UiEffect> = _effects.receiveAsFlow()

    private var loadGeneration = 0

    init {
        loadUsers(page = 1)
    }

    /** A status change is applied server-side, so the list reloads on return to keep filters honest. */
    fun refreshIfLoaded() {
        if (_uiState.value.usersState is UiState.Content) {
            loadUsers(page = 1, showLoading = false)
        }
    }

    fun loadUsers(page: Int, showLoading: Boolean = true) {
        val snapshot = _uiState.value
        if (page > 1 && (snapshot.isLoadingMore || !snapshot.hasMore)) return

        val generation = ++loadGeneration
        val isInitial = page == 1
        viewModelScope.launch {
            _uiState.update {
                if (isInitial) {
                    it.copy(
                        usersState = if (showLoading && it.usersState !is UiState.Content) {
                            UiState.Loading
                        } else {
                            it.usersState
                        },
                        page = 1,
                        isLoadingMore = false,
                        loadMoreError = null,
                    )
                } else {
                    it.copy(isLoadingMore = true, loadMoreError = null)
                }
            }

            val query = AdminUserQuery(
                q = snapshot.searchQuery.trim().takeIf { it.isNotEmpty() },
                status = snapshot.statusFilter.status,
                page = page,
                limit = USER_PAGE_SIZE,
            )

            when (val result = repository.listUsers(query)) {
                is ApiResult.Success -> {
                    if (generation != loadGeneration) return@launch
                    _uiState.update { previous ->
                        val existing = if (isInitial) {
                            emptyList()
                        } else {
                            (previous.usersState as? UiState.Content)?.data.orEmpty()
                        }
                        previous.copy(
                            usersState = UiState.Content(existing + result.data.items),
                            page = result.data.page,
                            limit = result.data.limit,
                            total = result.data.total,
                            isLoadingMore = false,
                            loadMoreError = null,
                        )
                    }
                }

                is ApiResult.Failure -> {
                    if (generation != loadGeneration) return@launch
                    if (isInitial) {
                        _uiState.update {
                            it.copy(usersState = UiState.Error(result.error), isLoadingMore = false)
                        }
                    } else {
                        _uiState.update {
                            it.copy(isLoadingMore = false, loadMoreError = result.error)
                        }
                    }
                }
            }
        }
    }

    fun loadMore() = loadUsers(_uiState.value.page + 1)

    fun retryLoadMore() = loadUsers(_uiState.value.page + 1)

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun onSearch() = loadUsers(page = 1)

    fun onStatusFilterSelected(filter: AdminUserStatusFilter) {
        if (_uiState.value.statusFilter == filter) return
        _uiState.update { it.copy(statusFilter = filter) }
        loadUsers(page = 1)
    }

    fun onRetry() = loadUsers(page = 1)

    fun setDisabled(user: AdminUser, disabled: Boolean) {
        if (_uiState.value.updatingUserId != null) return

        _uiState.update { it.copy(updatingUserId = user.id) }
        viewModelScope.launch {
            val target = if (disabled) AdminUserStatus.DISABLED else AdminUserStatus.ACTIVE
            when (val result = repository.updateStatus(user.id, target)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(updatingUserId = null) }
                    _effects.send(
                        UiEffect.ShowMessageRes(
                            if (disabled) {
                                R.string.admin_user_disabled_done
                            } else {
                                R.string.admin_user_enabled_done
                            },
                        ),
                    )
                    loadUsers(page = 1, showLoading = false)
                }

                is ApiResult.Failure -> {
                    _uiState.update { it.copy(updatingUserId = null) }
                    _effects.send(UiEffect.ShowError(result.error))
                    loadUsers(page = 1, showLoading = false)
                }
            }
        }
    }
}
