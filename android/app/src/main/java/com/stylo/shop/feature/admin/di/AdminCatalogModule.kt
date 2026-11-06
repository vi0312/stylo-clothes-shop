package com.stylo.shop.feature.admin.di

import com.stylo.shop.feature.admin.data.AdminCatalogApi
import com.stylo.shop.feature.admin.data.AdminCatalogRepository
import com.stylo.shop.feature.admin.data.DefaultAdminCatalogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AdminCatalogApiModule {

    @Provides
    @Singleton
    fun provideAdminCatalogApi(retrofit: Retrofit): AdminCatalogApi =
        retrofit.create(AdminCatalogApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminCatalogBindingsModule {

    @Binds
    abstract fun bindAdminCatalogRepository(
        implementation: DefaultAdminCatalogRepository,
    ): AdminCatalogRepository
}
