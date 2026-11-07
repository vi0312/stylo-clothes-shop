package com.stylo.shop.feature.orders.di

import com.stylo.shop.feature.orders.data.DefaultOrdersRepository
import com.stylo.shop.feature.orders.data.OrdersApi
import com.stylo.shop.feature.orders.data.OrdersRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object OrdersApiModule {

    @Provides
    @Singleton
    fun provideOrdersApi(retrofit: Retrofit): OrdersApi =
        retrofit.create(OrdersApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class OrdersBindingsModule {

    @Binds
    abstract fun bindOrdersRepository(
        implementation: DefaultOrdersRepository,
    ): OrdersRepository
}
