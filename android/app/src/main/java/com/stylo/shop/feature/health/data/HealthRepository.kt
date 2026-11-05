package com.stylo.shop.feature.health.data

import com.stylo.shop.BuildConfig
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.network.ApiResult
import com.stylo.shop.core.network.apiCall
import javax.inject.Inject
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Url

interface HealthApi {
    @GET
    suspend fun health(@Url url: String): HealthDto
}

@Serializable
data class HealthDto(val status: String)

interface HealthRepository {
    suspend fun check(): ApiResult<HealthDto>
}

class DefaultHealthRepository @Inject constructor(
    private val api: HealthApi,
) : HealthRepository {

    override suspend fun check(): ApiResult<HealthDto> =
        apiCall { api.health(ApiClient.absoluteUrl(BuildConfig.API_BASE_URL, "/health")) }
}
