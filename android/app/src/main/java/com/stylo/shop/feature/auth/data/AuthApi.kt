package com.stylo.shop.feature.auth.data

import com.stylo.shop.core.model.User
import com.stylo.shop.core.model.UserRole
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Auth endpoints travel on the bare client: register/login need no token, refresh must not
 * re-enter [com.stylo.shop.core.network.TokenAuthenticator], and /me carries its token explicitly
 * so the launch check never waits on a refresh chain.
 */
interface AuthApi {

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthSessionDto

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthSessionDto

    @POST("auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenPairDto

    @POST("auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    @GET("me")
    suspend fun me(@Header("Authorization") authorization: String): UserDto
}

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
    val user: UserDto,
)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class UserDto(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String? = null,
    val role: String,
)

fun UserDto.toUser(): User = User(
    id = id,
    email = email,
    fullName = fullName,
    phone = phone,
    role = when (role.uppercase()) {
        "ADMIN" -> UserRole.ADMIN
        "CUSTOMER" -> UserRole.CUSTOMER
        else -> throw IllegalArgumentException("Unknown role $role")
    },
)
