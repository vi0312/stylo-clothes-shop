package com.stylo.shop.feature.auth.data

import com.stylo.shop.core.model.User
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import com.stylo.shop.core.storage.TokenPair
import javax.inject.Inject

data class AuthSession(val tokens: TokenPair, val user: User)

interface AuthRepository {
    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
    ): ApiResult<AuthSession>

    suspend fun login(email: String, password: String): ApiResult<AuthSession>

    suspend fun refresh(refreshToken: String): ApiResult<TokenPair>

    suspend fun currentUser(accessToken: String): ApiResult<User>

    /** The server always answers 202, so a success only means "if the account exists, a code went out". */
    suspend fun requestPasswordReset(email: String): ApiResult<Unit>

    suspend fun resetPassword(email: String, code: String, password: String): ApiResult<Unit>

    /** Best effort: a failed revoke must never keep the user signed in locally. */
    suspend fun logout(refreshToken: String)
}

class DefaultAuthRepository @Inject constructor(
    private val api: AuthApi,
) : AuthRepository {

    override suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
    ): ApiResult<AuthSession> = apiCall {
        api.register(RegisterRequest(email, password, fullName, phone)).toSession()
    }

    override suspend fun login(email: String, password: String): ApiResult<AuthSession> = apiCall {
        api.login(LoginRequest(email, password)).toSession()
    }

    override suspend fun refresh(refreshToken: String): ApiResult<TokenPair> = apiCall {
        api.refresh(RefreshRequest(refreshToken)).toTokenPair()
    }

    override suspend fun currentUser(accessToken: String): ApiResult<User> = apiCall {
        api.me("Bearer $accessToken").toUser()
    }

    override suspend fun requestPasswordReset(email: String): ApiResult<Unit> = apiCall {
        api.forgotPassword(ForgotPasswordRequest(email))
    }

    override suspend fun resetPassword(
        email: String,
        code: String,
        password: String,
    ): ApiResult<Unit> = apiCall {
        api.resetPassword(ResetPasswordRequest(email, code, password))
    }

    override suspend fun logout(refreshToken: String) {
        runCatching { api.logout(RefreshRequest(refreshToken)) }
    }

    private fun AuthSessionDto.toSession(): AuthSession = AuthSession(
        tokens = TokenPair(accessToken, refreshToken),
        user = user.toUser(),
    )

    private fun TokenPairDto.toTokenPair(): TokenPair = TokenPair(accessToken, refreshToken)
}
