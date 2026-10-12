package com.webappypie.optilens

import android.app.Application
import android.os.Process
import android.os.SystemClock
import com.webappypie.optilens.core.common.build.BuildInfo
import com.webappypie.optilens.core.common.performance.StartupMetricsTracker
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltAndroidApp
class OptiLensApp : Application() {

    @Inject
    lateinit var logger: AppLogger

    @Inject
    lateinit var buildInfo: BuildInfo

    @Inject
    lateinit var startupMetrics: StartupMetricsTracker

    @Inject
    lateinit var wapAds: com.webappypie.optilens.core.common.wapcentral.WapCentralAdsRepository

    /** Process-lifetime scope for fire-and-forget startup work that must never block launch. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )

    override fun onCreate() {
        super.onCreate()
        // Wall-clock process start, derived from the kernel's process start time.
        val now = System.currentTimeMillis()
        startupMetrics.recordProcessStart(now - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()))
        startupMetrics.recordAppCreate(now)
        logger.i(TAG, "OptiLens starting up. Version: ${buildInfo.versionName} (${buildInfo.versionCode}), Debug: ${buildInfo.isDebug}")
        // WAPCentral ads config: background fetch; failure only means "no ads".
        appScope.launch {
            wapAds.refresh()
            logger.i(TAG, "WAPCentral ads: ${wapAds.state.value.let { s -> if (s is com.webappypie.optilens.core.common.wapcentral.WapAdsState.Ready) "ready (enabled=${s.config.enabled}, test=${s.config.testMode})" else s.toString() }}")
        }
    }

    companion object {
        private const val TAG = "OptiLensApp"
    }
}
