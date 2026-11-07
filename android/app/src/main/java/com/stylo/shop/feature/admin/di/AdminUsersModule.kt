package com.stylo.shop.feature.admin.di

import com.stylo.shop.feature.admin.data.AdminUsersApi
import com.stylo.shop.feature.admin.data.AdminUsersRepository
import com.stylo.shop.feature.admin.data.DefaultAdminUsersRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AdminUsersApiModule {

    @Provides
    @Singleton
    fun provideAdminUsersApi(retrofit: Retrofit): AdminUsersApi =
        retrofit.create(AdminUsersApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminUsersBindingsModule {

    @Binds
    abstract fun bindAdminUsersRepository(
        implementation: DefaultAdminUsersRepository,
    ): AdminUsersRepository
}
