package com.stylo.shop.core.di

import com.stylo.shop.BuildConfig
import com.stylo.shop.core.network.ApiClient
import com.stylo.shop.core.network.AuthInterceptor
import com.stylo.shop.core.network.RefreshApi
import com.stylo.shop.core.network.HttpTokenRefresher
import com.stylo.shop.core.network.TokenAuthenticator
import com.stylo.shop.core.network.TokenRefresher
import com.stylo.shop.feature.health.data.DefaultHealthRepository
import com.stylo.shop.feature.health.data.HealthApi
import com.stylo.shop.feature.health.data.HealthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        ApiClient.retrofit(BuildConfig.API_BASE_URL, client, json)

    @Provides
    @Singleton
    @BareClient
    fun provideBareOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @BareClient
    fun provideBareRetrofit(@BareClient client: OkHttpClient, json: Json): Retrofit =
        ApiClient.retrofit(BuildConfig.API_BASE_URL, client, json)

    @Provides
    @Singleton
    fun provideHealthApi(retrofit: Retrofit): HealthApi = retrofit.create(HealthApi::class.java)

    @Provides
    @Singleton
    fun provideRefreshApi(@BareClient retrofit: Retrofit): RefreshApi = retrofit.create(RefreshApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindHealthRepository(implementation: DefaultHealthRepository): HealthRepository

    @Binds
    abstract fun bindTokenRefresher(implementation: HttpTokenRefresher): TokenRefresher
}
