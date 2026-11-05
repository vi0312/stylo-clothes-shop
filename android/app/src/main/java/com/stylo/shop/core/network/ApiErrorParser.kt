package com.stylo.shop.core.network

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import retrofit2.HttpException

/**
 * Runs a repository network call and converts every transport failure into a typed [ApiError].
 * Exceptions never cross a repository boundary; callers only fold [ApiResult].
 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(
        ApiErrorParser.fromHttp(
            status = e.code(),
            body = e.response()?.errorBody()?.string(),
            fallbackMessage = e.message(),
        ),
    )
} catch (e: SocketTimeoutException) {
    ApiResult.Failure(ApiError(ApiErrorKind.TIMEOUT, developerMessage = e.message))
} catch (e: IOException) {
    ApiResult.Failure(ApiError(ApiErrorKind.NETWORK, developerMessage = e.message))
} catch (e: SerializationException) {
    ApiResult.Failure(ApiError(ApiErrorKind.PARSE, developerMessage = e.message))
} catch (e: Exception) {
    ApiResult.Failure(ApiError(ApiErrorKind.PARSE, developerMessage = e.message))
}

object ApiErrorParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun fromHttp(status: Int, body: String?, fallbackMessage: String?): ApiError {
        val payload = body
            ?.let { runCatching { json.decodeFromString<ErrorEnvelope>(it) }.getOrNull() }
            ?.error
        return ApiError(
            kind = ApiErrorKind.HTTP,
            code = payload?.code,
            httpStatus = status,
            details = payload?.details,
            developerMessage = payload?.message ?: fallbackMessage,
        )
    }

    @Serializable
    internal data class ErrorEnvelope(val error: ErrorBody)

    @Serializable
    internal data class ErrorBody(
        val code: String,
        val message: String? = null,
        val details: JsonElement? = null,
    )
}
