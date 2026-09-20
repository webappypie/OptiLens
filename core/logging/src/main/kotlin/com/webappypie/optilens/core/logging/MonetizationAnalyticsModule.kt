package com.webappypie.optilens.core.logging

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MonetizationAnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindMonetizationAnalytics(
        impl: DefaultMonetizationAnalytics,
    ): MonetizationAnalytics
}
