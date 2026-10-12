package com.webappypie.optilens.core.ui.screens

import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.camera.diagnostics.CameraDiagnosticsExporter
import com.webappypie.optilens.core.camera.discovery.CameraCapabilityRepository
import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.camera.thermal.DeviceThermalMonitor
import com.webappypie.optilens.core.common.performance.FrameMetricsCollector
import com.webappypie.optilens.core.common.performance.MemoryProfileManager
import com.webappypie.optilens.core.common.performance.StartupMetricsTracker
import com.webappypie.optilens.core.imaging.performance.BitmapPool
import com.webappypie.optilens.core.ui.performance.PerformanceDashboardData
import com.webappypie.optilens.core.ui.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraDiagnosticsUiState(
    val profile: CameraCapabilityProfile,
    val selectedCameraIndex: Int = 0,
    val jsonExport: String = "",
    val markdownExport: String = "",
    val performanceData: PerformanceDashboardData = PerformanceDashboardData(),
    /** WAPCentral ads configuration status (never includes credentials). */
    val wapCentralAdsStatus: String = "Not checked",
) {
    val selectedCamera: CameraDeviceProfile?
        get() = profile.cameras.getOrNull(selectedCameraIndex)
            ?: profile.cameras.firstOrNull()
}

@HiltViewModel
class CameraDiagnosticsViewModel @Inject constructor(
    private val repository: CameraCapabilityRepository,
    private val exporter: CameraDiagnosticsExporter,
    private val startupTracker: StartupMetricsTracker,
    private val memoryManager: MemoryProfileManager,
    private val frameCollector: FrameMetricsCollector,
    private val thermalMonitor: DeviceThermalMonitor,
    private val bitmapPool: BitmapPool,
    private val wapAds: com.webappypie.optilens.core.common.wapcentral.WapCentralAdsRepository,
    dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : BaseViewModel<CameraDiagnosticsUiState>(dispatchers) {

    init {
        loadCapabilities(forceRefresh = false)
    }

    fun refresh() {
        loadCapabilities(forceRefresh = true)
    }

    fun selectCamera(index: Int) {
        val current = (currentState as? com.webappypie.optilens.core.ui.state.UiState.Success)?.data ?: return
        if (index in current.profile.cameras.indices) {
            setSuccess(current.copy(selectedCameraIndex = index))
        }
    }

    private fun loadCapabilities(forceRefresh: Boolean) {
        setLoading("Detecting camera hardware capabilities...")
        viewModelScope.launch(dispatchers.io) {
            runSafely(retry = { loadCapabilities(forceRefresh) }) {
                val profile = if (forceRefresh) {
                    repository.refreshCapabilities()
                } else {
                    repository.getOrDiscoverCapabilities()
                }
                val json = exporter.exportToJson(profile)
                val markdown = exporter.exportToMarkdown(profile)
                val perfData = collectPerformanceData()
                if (forceRefresh || wapAds.state.value !is com.webappypie.optilens.core.common.wapcentral.WapAdsState.Ready) {
                    wapAds.refresh()
                }
                val adsStatus = when (val s = wapAds.state.value) {
                    is com.webappypie.optilens.core.common.wapcentral.WapAdsState.Ready ->
                        "Connected · ads ${if (s.config.enabled) "on" else "off"}" +
                            (if (s.config.testMode) " (test mode)" else "") +
                            " · banner ${if (s.config.adMobUnit(com.webappypie.optilens.core.common.wapcentral.WapAdsConfig.FORMAT_BANNER) != null) "enabled" else "disabled"}"
                    is com.webappypie.optilens.core.common.wapcentral.WapAdsState.Unavailable -> "Unavailable: ${s.reason}"
                    com.webappypie.optilens.core.common.wapcentral.WapAdsState.Unknown -> "Not checked"
                }

                setSuccess(
                    CameraDiagnosticsUiState(
                        profile = profile,
                        selectedCameraIndex = 0,
                        jsonExport = json,
                        markdownExport = markdown,
                        performanceData = perfData,
                        wapCentralAdsStatus = adsStatus,
                    )
                )
            }
        }
    }

    private fun collectPerformanceData(): PerformanceDashboardData {
        val poolMetrics = bitmapPool.getMetrics()
        val totalPoolRequests = poolMetrics.hitCount + poolMetrics.missCount
        val hitRate = if (totalPoolRequests > 0L) poolMetrics.hitRatePercent / 100f else 0f

        return PerformanceDashboardData(
            startupMetrics = startupTracker.metrics.value,
            frameMetrics = frameCollector.getSnapshot(),
            memorySnapshot = memoryManager.sampleMemorySnapshot(),
            thermalState = thermalMonitor.thermalState.value,
            poolHitRate = hitRate,
            poolAcquisitions = totalPoolRequests,
            poolEvictions = poolMetrics.evictionCount,
            maxBurstFrames = thermalMonitor.policy.value.maxBurstFrames,
            userThermalWarning = thermalMonitor.policy.value.userWarningMessage,
        )
    }

    companion object {
        private const val TAG = "CameraDiagnosticsVM"
    }
}
