package com.webappypie.optilens.core.common.wapcentral

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** [WapCentralCredentials] are provided by the app module from build configuration. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WapCentralModule {
    @Binds
    abstract fun bindWapCentralHttp(impl: UrlConnectionWapCentralHttp): WapCentralHttp
}
