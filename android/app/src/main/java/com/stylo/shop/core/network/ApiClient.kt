package com.stylo.shop.core.network

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object ApiClient {

    fun retrofit(baseUrl: String, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(withTrailingSlash(baseUrl))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    /** Resolves [path] against the API origin, e.g. `/health` for the unversioned health route. */
    fun absoluteUrl(baseUrl: String, path: String): String =
        withTrailingSlash(baseUrl).toHttpUrl().resolve(path)?.toString()
            ?: error("Cannot resolve $path against $baseUrl")

    private fun withTrailingSlash(baseUrl: String): String = baseUrl.trimEnd('/') + "/"
}
