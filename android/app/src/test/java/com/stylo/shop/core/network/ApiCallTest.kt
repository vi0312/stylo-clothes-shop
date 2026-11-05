package com.stylo.shop.core.network

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.HttpException

class ApiCallTest {

    @Test
    fun `http errors keep the server code, status and details`() = runTest {
        val body = """
            {"error":{"code":"OUT_OF_STOCK","message":"Not enough stock","details":[{"sku":"AT-DEN-M"}]}}
        """.trimIndent()
        val exception = HttpException(
            retrofit2.Response.error<Unit>(409, body.toResponseBody("application/json".toMediaType())),
        )

        val failure = apiCall<Unit> { throw exception } as ApiResult.Failure

        assertEquals(ApiErrorKind.HTTP, failure.error.kind)
        assertEquals("OUT_OF_STOCK", failure.error.code)
        assertEquals(409, failure.error.httpStatus)
        assertEquals("Not enough stock", failure.error.developerMessage)
        assertNotNull(failure.error.details)
    }

    @Test
    fun `an http error without an envelope keeps the transport message`() = runTest {
        val exception = HttpException(
            retrofit2.Response.error<Unit>(502, "Bad gateway".toResponseBody("text/plain".toMediaType())),
        )

        val failure = apiCall<Unit> { throw exception } as ApiResult.Failure

        assertEquals(ApiErrorKind.HTTP, failure.error.kind)
        assertEquals(null, failure.error.code)
        assertEquals(502, failure.error.httpStatus)
    }

    @Test
    fun `timeouts map to TIMEOUT`() = runTest {
        val failure = apiCall<Unit> { throw SocketTimeoutException("slow") } as ApiResult.Failure
        assertEquals(ApiErrorKind.TIMEOUT, failure.error.kind)
    }

    @Test
    fun `io failures map to NETWORK`() = runTest {
        val failure = apiCall<Unit> { throw IOException("offline") } as ApiResult.Failure
        assertEquals(ApiErrorKind.NETWORK, failure.error.kind)
    }

    @Test
    fun `serialization failures map to PARSE`() = runTest {
        val failure = apiCall<Unit> {
            throw SerializationException("unexpected shape")
        } as ApiResult.Failure
        assertEquals(ApiErrorKind.PARSE, failure.error.kind)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is rethrown`() {
        runBlocking { apiCall<Unit> { throw CancellationException("cancelled") } }
    }
}
