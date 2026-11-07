package com.stylo.shop.feature.admin.di

import com.stylo.shop.feature.admin.data.AdminReportsApi
import com.stylo.shop.feature.admin.data.AdminReportsRepository
import com.stylo.shop.feature.admin.data.DefaultAdminReportsRepository
import com.stylo.shop.feature.admin.model.REPORT_ZONE
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object AdminReportsApiModule {

    @Provides
    @Singleton
    fun provideAdminReportsApi(retrofit: Retrofit): AdminReportsApi =
        retrofit.create(AdminReportsApi::class.java)

    /** Report ranges are Vietnam calendar days, so "today" must be read in that zone. */
    @Provides
    @Singleton
    fun provideReportClock(): Clock = Clock.system(REPORT_ZONE)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminReportsBindingsModule {

    @Binds
    abstract fun bindAdminReportsRepository(
        implementation: DefaultAdminReportsRepository,
    ): AdminReportsRepository
}
