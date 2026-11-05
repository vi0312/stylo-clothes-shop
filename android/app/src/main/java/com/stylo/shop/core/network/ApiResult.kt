package com.stylo.shop.core.network

import kotlinx.serialization.json.JsonElement

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

enum class ApiErrorKind { NETWORK, TIMEOUT, HTTP, PARSE }

data class ApiError(
    val kind: ApiErrorKind,
    val code: String? = null,
    val httpStatus: Int? = null,
    val details: JsonElement? = null,
    val developerMessage: String? = null,
)
