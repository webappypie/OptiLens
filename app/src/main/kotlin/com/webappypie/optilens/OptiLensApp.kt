package com.webappypie.optilens

import android.app.Application
import android.os.Process
import android.os.SystemClock
import com.webappypie.optilens.core.common.build.BuildInfo
import com.webappypie.optilens.core.common.performance.StartupMetricsTracker
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class OptiLensApp : Application() {

    @Inject
    lateinit var logger: AppLogger

    @Inject
    lateinit var buildInfo: BuildInfo

    @Inject
    lateinit var startupMetrics: StartupMetricsTracker

    override fun onCreate() {
        super.onCreate()
        // Wall-clock process start, derived from the kernel's process start time.
        val now = System.currentTimeMillis()
        startupMetrics.recordProcessStart(now - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()))
        startupMetrics.recordAppCreate(now)
        logger.i(TAG, "OptiLens starting up. Version: ${buildInfo.versionName} (${buildInfo.versionCode}), Debug: ${buildInfo.isDebug}")
    }

    companion object {
        private const val TAG = "OptiLensApp"
    }
}
