package com.webappypie.optilens.core.ui.di

import com.webappypie.optilens.core.ui.ads.AdProvider
import com.webappypie.optilens.core.ui.ads.GoogleMobileAdProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdModule {

    @Binds
    @Singleton
    abstract fun bindAdProvider(
        impl: GoogleMobileAdProvider,
    ): AdProvider
}
