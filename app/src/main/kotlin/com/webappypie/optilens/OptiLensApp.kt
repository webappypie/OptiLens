package com.webappypie.optilens

import android.app.Application
import com.webappypie.optilens.core.common.build.BuildInfo
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OptiLensApp : Application() {

    @Inject
    lateinit var logger: AppLogger

    @Inject
    lateinit var buildInfo: BuildInfo

    override fun onCreate() {
        super.onCreate()
        logger.i(TAG, "OptiLens starting up. Version: ${buildInfo.versionName} (${buildInfo.versionCode}), Debug: ${buildInfo.isDebug}")
    }

    companion object {
        private const val TAG = "OptiLensApp"
    }
}
