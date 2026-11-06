package com.stylo.shop.feature.profile.di

import com.stylo.shop.feature.profile.data.DefaultProfileRepository
import com.stylo.shop.feature.profile.data.ProfileApi
import com.stylo.shop.feature.profile.data.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object ProfileApiModule {

    @Provides
    @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileBindingsModule {

    @Binds
    abstract fun bindProfileRepository(
        implementation: DefaultProfileRepository,
    ): ProfileRepository
}
