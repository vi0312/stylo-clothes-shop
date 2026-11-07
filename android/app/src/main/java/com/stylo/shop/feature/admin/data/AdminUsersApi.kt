package com.stylo.shop.feature.admin.data

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query

interface AdminUsersApi {

    @GET("admin/users")
    suspend fun listUsers(
        @Query("q") q: String? = null,
        @Query("status") status: String? = null,
        @Query("role") role: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): AdminUserPageDto

    @PATCH("admin/users/{id}/status")
    suspend fun updateStatus(
        @Path("id") id: Long,
        @Body body: UpdateAdminUserStatusRequest,
    ): AdminUserDto
}

@Serializable
data class UpdateAdminUserStatusRequest(val status: String)

@Serializable
data class AdminUserPageDto(
    val items: List<AdminUserDto> = emptyList(),
    val page: Int = 1,
    val limit: Int = 20,
    val total: Int = 0,
)

@Serializable
data class AdminUserDto(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String? = null,
    val role: String,
    val status: String,
    val createdAt: String,
)
