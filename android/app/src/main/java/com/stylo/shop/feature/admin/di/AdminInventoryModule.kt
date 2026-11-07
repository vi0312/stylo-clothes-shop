package com.stylo.shop.feature.admin.di

import com.stylo.shop.feature.admin.data.AdminInventoryApi
import com.stylo.shop.feature.admin.data.AdminInventoryRepository
import com.stylo.shop.feature.admin.data.DefaultAdminInventoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AdminInventoryApiModule {

    @Provides
    @Singleton
    fun provideAdminInventoryApi(retrofit: Retrofit): AdminInventoryApi =
        retrofit.create(AdminInventoryApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminInventoryBindingsModule {

    @Binds
    abstract fun bindAdminInventoryRepository(
        implementation: DefaultAdminInventoryRepository,
    ): AdminInventoryRepository
}
