package com.stylo.shop.feature.admin.ui.users

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stylo.shop.R
import com.stylo.shop.core.model.UserRole
import com.stylo.shop.core.ui.ErrorMessages
import com.stylo.shop.core.ui.UiEffectHost
import com.stylo.shop.core.ui.UiState
import com.stylo.shop.core.ui.UiTags
import com.stylo.shop.feature.admin.model.AdminUser
import com.stylo.shop.feature.admin.model.AdminUserStatus
import com.stylo.shop.feature.admin.ui.AdminDetailScaffold
import com.stylo.shop.core.ui.components.StyloConfirmDialog
import com.stylo.shop.core.ui.components.StyloEmptyState
import com.stylo.shop.core.ui.components.StyloErrorState
import com.stylo.shop.core.ui.components.StyloListSkeleton
import com.stylo.shop.core.ui.components.StyloSearchField
import com.stylo.shop.core.ui.components.StyloStatusBadge
import com.stylo.shop.core.ui.components.StyloTone
import com.stylo.shop.core.ui.components.StyloFilterChip

@Composable
fun AdminUserListScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminUserListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDisable by remember { mutableStateOf<AdminUser?>(null) }

    UiEffectHost(effects = viewModel.effects, snackbarHostState = snackbarHostState)

    LifecycleResumeEffect(Unit) {
        viewModel.refreshIfLoaded()
        onPauseOrDispose { }
    }

    AdminDetailScaffold(
        title = stringResource(R.string.admin_users_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = if (state.usersState is UiState.Loading) {
                    ""
                } else {
                    stringResource(R.string.admin_users_count, state.total)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )

            StyloSearchField(
                query = state.searchQuery,
                hint = stringResource(R.string.admin_users_search_hint),
                onQueryChange = viewModel::onSearchQueryChange,
                onSubmit = viewModel::onSearch,
                onClear = {
                    viewModel.onSearchQueryChange("")
                    viewModel.onSearch()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag(UiTags.AdminUserSearch),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(AdminUserStatusFilter.entries) { filter ->
                    StyloFilterChip(
                        label = statusFilterLabel(filter),
                        selected = state.statusFilter == filter,
                        onClick = { viewModel.onStatusFilterSelected(filter) },
                        modifier = Modifier.testTag(UiTags.AdminUserStatusChip),
                    )
                }
            }

            when (val usersState = state.usersState) {
                is UiState.Loading -> StyloListSkeleton(rows = 6, thumbnail = false)
                is UiState.Error -> StyloErrorState(
                    error = usersState.error,
                    onRetry = viewModel::onRetry,
                )
                is UiState.Content -> if (usersState.data.isEmpty()) {
                    StyloEmptyState(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.admin_users_empty_title),
                        body = stringResource(R.string.admin_users_empty_body),
                        modifier = Modifier.testTag(UiTags.AdminUserEmpty),
                    )
                } else {
                    UserRows(
                        users = usersState.data,
                        state = state,
                        onDisable = { pendingDisable = it },
                        onEnable = { viewModel.setDisabled(it, disabled = false) },
                        onLoadMore = viewModel::loadMore,
                        onRetryLoadMore = viewModel::retryLoadMore,
                    )
                }
            }
        }
    }

    pendingDisable?.let { user ->
        StyloConfirmDialog(
            title = stringResource(R.string.admin_user_disable_title),
            body = stringResource(R.string.admin_user_disable_body, user.fullName),
            confirmLabel = stringResource(R.string.admin_user_disable),
            busy = state.updatingUserId == user.id,
            onConfirm = {
                pendingDisable = null
                viewModel.setDisabled(user, disabled = true)
            },
            onDismiss = { pendingDisable = null },
        )
    }
}

@Composable
private fun statusFilterLabel(filter: AdminUserStatusFilter): String = stringResource(
    when (filter) {
        AdminUserStatusFilter.ALL -> R.string.admin_filter_all
        AdminUserStatusFilter.ACTIVE -> R.string.admin_user_status_active
        AdminUserStatusFilter.DISABLED -> R.string.admin_user_status_disabled
    },
)

@Composable
private fun UserRows(
    users: List<AdminUser>,
    state: AdminUserListUiState,
    onDisable: (AdminUser) -> Unit,
    onEnable: (AdminUser) -> Unit,
    onLoadMore: () -> Unit,
    onRetryLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .testTag(UiTags.AdminUserList),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(users, key = { it.id }) { user ->
            UserRow(
                user = user,
                updating = state.updatingUserId == user.id,
                actionsEnabled = state.updatingUserId == null,
                onDisable = { onDisable(user) },
                onEnable = { onEnable(user) },
            )
        }
        if (state.isLoadingMore) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else if (state.loadMoreError != null) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(ErrorMessages.resourceId(state.loadMoreError)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetryLoadMore) {
                        Text(stringResource(R.string.action_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun UserRow(
    user: AdminUser,
    updating: Boolean,
    actionsEnabled: Boolean,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UiTags.AdminUserRow),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = user.fullName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.fullName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        when (user.role) {
                            UserRole.ADMIN -> R.string.admin_user_role_admin
                            UserRole.CUSTOMER -> R.string.admin_user_role_customer
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                StyloStatusBadge(
                    text = stringResource(
                        if (user.status == AdminUserStatus.ACTIVE) {
                            R.string.admin_user_status_active
                        } else {
                            R.string.admin_user_status_disabled
                        },
                    ),
                    tone = if (user.status == AdminUserStatus.ACTIVE) {
                        StyloTone.Success
                    } else {
                        StyloTone.Neutral
                    },
                )
                TextButton(
                    onClick = if (user.status == AdminUserStatus.ACTIVE) onDisable else onEnable,
                    enabled = actionsEnabled,
                ) {
                    Text(
                        stringResource(
                            if (user.status == AdminUserStatus.ACTIVE) {
                                R.string.admin_user_disable
                            } else {
                                R.string.admin_user_enable
                            },
                        ),
                    )
                    if (updating) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }
    }
}
