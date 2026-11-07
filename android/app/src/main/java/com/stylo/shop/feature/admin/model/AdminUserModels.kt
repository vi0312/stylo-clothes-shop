package com.stylo.shop.feature.admin.model

import com.stylo.shop.core.model.UserRole

enum class AdminUserStatus(val apiValue: String) {
    ACTIVE("ACTIVE"),
    DISABLED("DISABLED"),
    ;

    companion object {
        fun fromApi(value: String): AdminUserStatus =
            entries.firstOrNull { it.apiValue == value } ?: ACTIVE
    }
}

data class AdminUser(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String?,
    val role: UserRole,
    val status: AdminUserStatus,
    val createdAt: String,
)

data class AdminUserQuery(
    val q: String? = null,
    val status: AdminUserStatus? = null,
    val page: Int = 1,
    val limit: Int = USER_PAGE_SIZE,
)

const val USER_PAGE_SIZE = 20
