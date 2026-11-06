package com.stylo.shop.core.model

enum class UserRole { CUSTOMER, ADMIN }

data class User(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String?,
    val role: UserRole,
)
