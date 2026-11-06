package com.stylo.shop.feature.auth.di

import com.stylo.shop.core.di.BareClient
import com.stylo.shop.core.network.HttpTokenRefresher
import com.stylo.shop.core.network.TokenRefresher
import com.stylo.shop.feature.auth.data.AuthApi
import com.stylo.shop.feature.auth.data.AuthRepository
import com.stylo.shop.feature.auth.data.DefaultAuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AuthApiModule {

    @Provides
    @Singleton
    fun provideAuthApi(@BareClient retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {

    @Binds
    abstract fun bindAuthRepository(implementation: DefaultAuthRepository): AuthRepository

    @Binds
    abstract fun bindTokenRefresher(implementation: HttpTokenRefresher): TokenRefresher
}
