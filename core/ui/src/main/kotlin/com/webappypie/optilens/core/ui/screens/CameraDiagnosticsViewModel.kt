package com.webappypie.optilens.core.ui.screens

import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.camera.diagnostics.CameraDiagnosticsExporter
import com.webappypie.optilens.core.camera.discovery.CameraCapabilityRepository
import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.ui.viewmodel.BaseViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraDiagnosticsUiState(
    val profile: CameraCapabilityProfile,
    val selectedCameraIndex: Int = 0,
    val jsonExport: String = "",
    val markdownExport: String = "",
) {
    val selectedCamera: CameraDeviceProfile?
        get() = profile.cameras.getOrNull(selectedCameraIndex)
            ?: profile.cameras.firstOrNull()
}

@HiltViewModel
class CameraDiagnosticsViewModel @Inject constructor(
    private val repository: CameraCapabilityRepository,
    private val exporter: CameraDiagnosticsExporter,
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

                setSuccess(
                    CameraDiagnosticsUiState(
                        profile = profile,
                        selectedCameraIndex = 0,
                        jsonExport = json,
                        markdownExport = markdown,
                    )
                )
            }
        }
    }

    companion object {
        private const val TAG = "CameraDiagnosticsVM"
    }
}
