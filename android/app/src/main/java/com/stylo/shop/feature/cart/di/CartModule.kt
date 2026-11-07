package com.stylo.shop.feature.cart.di

import com.stylo.shop.feature.cart.data.CartApi
import com.stylo.shop.feature.cart.data.CartRepository
import com.stylo.shop.feature.cart.data.DefaultCartRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object CartApiModule {

    @Provides
    @Singleton
    fun provideCartApi(retrofit: Retrofit): CartApi =
        retrofit.create(CartApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CartBindingsModule {

    @Binds
    abstract fun bindCartRepository(
        implementation: DefaultCartRepository,
    ): CartRepository
}
