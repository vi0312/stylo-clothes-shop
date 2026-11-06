package com.stylo.shop

import com.stylo.shop.core.model.User
import com.stylo.shop.core.model.UserRole
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.storage.TokenPair
import com.stylo.shop.core.storage.TokenStore
import com.stylo.shop.feature.auth.data.AuthRepository
import com.stylo.shop.feature.auth.data.AuthSession

val testUser = User(
    id = 7,
    email = "an@example.com",
    fullName = "Nguyễn Văn An",
    phone = "0901234567",
    role = UserRole.CUSTOMER,
)

val testTokens = TokenPair(accessToken = "access-token", refreshToken = "refresh-token")

val testSession = AuthSession(tokens = testTokens, user = testUser)

class FakeTokenStore(var tokens: TokenPair? = null) : TokenStore {

    override suspend fun read(): TokenPair? = tokens

    override suspend fun save(tokens: TokenPair) {
        this.tokens = tokens
    }

    override suspend fun clear() {
        tokens = null
    }
}

class FakeAuthRepository(
    var registerResult: ApiResult<AuthSession> = ApiResult.Success(testSession),
    var loginResult: ApiResult<AuthSession> = ApiResult.Success(testSession),
    var refreshResult: (String) -> ApiResult<TokenPair> = { ApiResult.Success(testTokens) },
    var currentUserResult: (String) -> ApiResult<User> = { ApiResult.Success(testUser) },
) : AuthRepository {

    var registerCalls = 0

    override suspend fun register(
        email: String,
        password: String,
        fullName: String,
        phone: String?,
    ): ApiResult<AuthSession> {
        registerCalls++
        return registerResult
    }

    override suspend fun login(email: String, password: String): ApiResult<AuthSession> = loginResult

    override suspend fun refresh(refreshToken: String): ApiResult<TokenPair> =
        refreshResult(refreshToken)

    override suspend fun currentUser(accessToken: String): ApiResult<User> =
        currentUserResult(accessToken)

    override suspend fun logout(refreshToken: String) {
        logoutCalls++
    }

    var logoutCalls = 0
}
