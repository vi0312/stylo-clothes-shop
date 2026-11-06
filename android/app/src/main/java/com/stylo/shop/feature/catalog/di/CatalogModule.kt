package com.stylo.shop.feature.catalog.di

import com.stylo.shop.feature.catalog.data.CatalogApi
import com.stylo.shop.feature.catalog.data.CatalogRepository
import com.stylo.shop.feature.catalog.data.DefaultCatalogRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object CatalogApiModule {

    @Provides
    @Singleton
    fun provideCatalogApi(retrofit: Retrofit): CatalogApi =
        retrofit.create(CatalogApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CatalogBindingsModule {

    @Binds
    abstract fun bindCatalogRepository(
        implementation: DefaultCatalogRepository,
    ): CatalogRepository
}
