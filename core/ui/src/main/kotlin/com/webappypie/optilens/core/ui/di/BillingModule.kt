package com.webappypie.optilens.core.ui.di

import com.webappypie.optilens.core.ui.billing.BillingManager
import com.webappypie.optilens.core.ui.billing.PlayBillingManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {

    @Binds
    @Singleton
    abstract fun bindBillingManager(
        impl: PlayBillingManager,
    ): BillingManager
}
