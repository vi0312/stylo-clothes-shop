package com.stylo.shop.feature.admin

import com.stylo.shop.core.model.UserRole
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.feature.admin.data.AdminUsersRepository
import com.stylo.shop.feature.admin.model.AdminUser
import com.stylo.shop.feature.admin.model.AdminUserQuery
import com.stylo.shop.feature.admin.model.AdminUserStatus
import com.stylo.shop.feature.admin.model.Paginated

fun testAdminUser(
    id: Long = 7,
    email: String = "binh@example.com",
    fullName: String = "Trần Bình",
    phone: String? = "0912345678",
    role: UserRole = UserRole.CUSTOMER,
    status: AdminUserStatus = AdminUserStatus.ACTIVE,
    createdAt: String = "2026-11-07T03:20:00.000Z",
): AdminUser = AdminUser(
    id = id,
    email = email,
    fullName = fullName,
    phone = phone,
    role = role,
    status = status,
    createdAt = createdAt,
)

class FakeAdminUsersRepository(
    pageResults: List<ApiResult<Paginated<AdminUser>>> = listOf(
        ApiResult.Success(
            Paginated(items = listOf(testAdminUser()), page = 1, limit = 20, total = 1),
        ),
    ),
    var updateResult: ApiResult<AdminUser> = ApiResult.Success(testAdminUser()),
) : AdminUsersRepository {

    private val pageResults = pageResults.toMutableList()
    val queries = mutableListOf<AdminUserQuery>()
    val updates = mutableListOf<Pair<Long, AdminUserStatus>>()

    override suspend fun listUsers(query: AdminUserQuery): ApiResult<Paginated<AdminUser>> {
        queries += query
        val result = pageResults.first()
        if (pageResults.size > 1) pageResults.removeAt(0)
        return result
    }

    override suspend fun updateStatus(
        userId: Long,
        status: AdminUserStatus,
    ): ApiResult<AdminUser> {
        updates += userId to status
        return updateResult
    }
}
