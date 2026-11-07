package com.stylo.shop.feature.admin.data

import com.stylo.shop.core.model.UserRole
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.feature.admin.model.AdminUser
import com.stylo.shop.feature.admin.model.AdminUserQuery
import com.stylo.shop.feature.admin.model.AdminUserStatus
import com.stylo.shop.feature.admin.model.Paginated
import javax.inject.Inject

interface AdminUsersRepository {

    suspend fun listUsers(query: AdminUserQuery): ApiResult<Paginated<AdminUser>>

    suspend fun updateStatus(userId: Long, status: AdminUserStatus): ApiResult<AdminUser>
}

class DefaultAdminUsersRepository @Inject constructor(
    private val api: AdminUsersApi,
) : AdminUsersRepository {

    override suspend fun listUsers(query: AdminUserQuery): ApiResult<Paginated<AdminUser>> =
        apiCall {
            val page = api.listUsers(
                q = query.q,
                status = query.status?.apiValue,
                page = query.page,
                limit = query.limit,
            )
            Paginated(
                items = page.items.map { it.toModel() },
                page = page.page,
                limit = page.limit,
                total = page.total,
            )
        }

    override suspend fun updateStatus(
        userId: Long,
        status: AdminUserStatus,
    ): ApiResult<AdminUser> = apiCall {
        api.updateStatus(userId, UpdateAdminUserStatusRequest(status.apiValue)).toModel()
    }
}

private fun AdminUserDto.toModel(): AdminUser = AdminUser(
    id = id,
    email = email,
    fullName = fullName,
    phone = phone,
    role = when (role) {
        "ADMIN" -> UserRole.ADMIN
        else -> UserRole.CUSTOMER
    },
    status = AdminUserStatus.fromApi(status),
    createdAt = createdAt,
)
