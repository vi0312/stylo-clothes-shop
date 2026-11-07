package com.stylo.shop.feature.checkout.di

import com.stylo.shop.feature.checkout.data.CheckoutApi
import com.stylo.shop.feature.checkout.data.CheckoutRepository
import com.stylo.shop.feature.checkout.data.DefaultCheckoutRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object CheckoutApiModule {

    @Provides
    @Singleton
    fun provideCheckoutApi(retrofit: Retrofit): CheckoutApi =
        retrofit.create(CheckoutApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CheckoutBindingsModule {

    @Binds
    abstract fun bindCheckoutRepository(
        implementation: DefaultCheckoutRepository,
    ): CheckoutRepository
}
