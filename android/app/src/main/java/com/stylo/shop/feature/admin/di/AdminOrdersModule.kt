package com.stylo.shop.feature.admin.di

import com.stylo.shop.feature.admin.data.AdminOrdersApi
import com.stylo.shop.feature.admin.data.AdminOrdersRepository
import com.stylo.shop.feature.admin.data.DefaultAdminOrdersRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AdminOrdersApiModule {

    @Provides
    @Singleton
    fun provideAdminOrdersApi(retrofit: Retrofit): AdminOrdersApi =
        retrofit.create(AdminOrdersApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminOrdersBindingsModule {

    @Binds
    abstract fun bindAdminOrdersRepository(
        implementation: DefaultAdminOrdersRepository,
    ): AdminOrdersRepository
}
