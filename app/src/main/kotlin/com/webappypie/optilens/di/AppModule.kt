package com.webappypie.optilens.di

import com.webappypie.optilens.BuildConfig
import com.webappypie.optilens.core.common.build.BuildInfo
import com.webappypie.optilens.core.common.feature.LocalFeatureFlags
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBuildInfo(): BuildInfo = object : BuildInfo {
        override val isDebug: Boolean = BuildConfig.DEBUG
        override val versionName: String = BuildConfig.VERSION_NAME
        override val versionCode: Int = BuildConfig.VERSION_CODE
        override val applicationId: String = BuildConfig.APPLICATION_ID
        override val showDiagnostics: Boolean = BuildConfig.DEBUG && LocalFeatureFlags.diagnosticsEnabled
    }

    @Provides
    @Singleton
    fun provideWapCentralCredentials(): com.webappypie.optilens.core.common.wapcentral.WapCentralCredentials =
        com.webappypie.optilens.core.common.wapcentral.WapCentralCredentials(
            baseUrl = BuildConfig.WAPCENTRAL_BASE_URL,
            appId = BuildConfig.WAPCENTRAL_APP_ID,
            appKey = BuildConfig.WAPCENTRAL_APP_KEY,
        )
}
