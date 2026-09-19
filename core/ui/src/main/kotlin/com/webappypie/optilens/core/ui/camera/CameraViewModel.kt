package com.webappypie.optilens.core.ui.camera

import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.camera.CameraController
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.common.result.OptiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State representing camera viewfinder, session status, controls, and capture status.
 */
data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val isFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.AUTO,
    val isTorchEnabled: Boolean = false,
    val zoomState: ZoomState = ZoomState(),
    val exposureState: ExposureState = ExposureState(),
    val sessionState: CameraSessionState = CameraSessionState.IDLE,
    val isCapturing: Boolean = false,
    val lastCapturedPhoto: CapturedPhoto? = null,
    val focusTarget: Offset? = null,
    val isShutterBlinking: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraController: CameraController,
) : ViewModel() {

    private val _internalState = MutableStateFlow(
        CameraInternalState(isFrontCamera = cameraController.isFrontCamera)
    )
    private var focusResetJob: Job? = null

    private data class CameraHardwareState(
        val flash: FlashMode,
        val exposure: ExposureState,
        val lastPhoto: CapturedPhoto?,
    )

    private val _cameraHardwareState = combine(
        cameraController.flashMode,
        cameraController.exposureState,
        cameraController.lastCapturedPhoto,
    ) { flash, exposure, lastPhoto ->
        CameraHardwareState(flash, exposure, lastPhoto)
    }

    val uiState: StateFlow<CameraUiState> = combine(
        _internalState,
        cameraController.sessionState,
        cameraController.zoomState,
        _cameraHardwareState,
    ) { internal, session, zoom, hardware ->
        CameraUiState(
            hasCameraPermission = internal.hasPermission,
            isFrontCamera = internal.isFrontCamera,
            flashMode = hardware.flash,
            isTorchEnabled = internal.isTorchEnabled,
            zoomState = zoom,
            exposureState = hardware.exposure,
            sessionState = session,
            isCapturing = internal.isCapturing,
            lastCapturedPhoto = hardware.lastPhoto,
            focusTarget = internal.focusTarget,
            isShutterBlinking = internal.isShutterBlinking,
            errorMessage = internal.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CameraUiState(),
    )

    fun onPermissionResult(isGranted: Boolean) {
        _internalState.update { it.copy(hasPermission = isGranted) }
    }

    fun bindPreview(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        viewModelScope.launch {
            when (val result = cameraController.bindPreview(lifecycleOwner, surfaceProvider)) {
                is OptiResult.Error -> {
                    _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                }
                else -> Unit
            }
        }
    }

    fun onTapToFocus(offset: Offset, meteringPoint: MeteringPoint) {
        _internalState.update { it.copy(focusTarget = offset) }
        focusResetJob?.cancel()
        focusResetJob = viewModelScope.launch {
            cameraController.focusAndMeter(meteringPoint)
            delay(2_000L)
            _internalState.update { it.copy(focusTarget = null) }
        }
    }

    fun onZoomRatioChanged(ratio: Float) {
        viewModelScope.launch {
            cameraController.setZoom(ratio)
        }
    }

    fun onExposureCompensationChanged(index: Int) {
        viewModelScope.launch {
            cameraController.setExposureCompensation(index)
        }
    }

    fun toggleFlashMode() {
        val nextMode = when (uiState.value.flashMode) {
            FlashMode.AUTO -> FlashMode.ON
            FlashMode.ON -> FlashMode.OFF
            FlashMode.OFF -> FlashMode.AUTO
            FlashMode.TORCH -> FlashMode.AUTO
        }
        viewModelScope.launch {
            cameraController.setFlashMode(nextMode)
        }
    }

    fun setFlashMode(mode: FlashMode) {
        viewModelScope.launch {
            cameraController.setFlashMode(mode)
        }
    }

    fun toggleTorch() {
        val next = !(_internalState.value.isTorchEnabled)
        viewModelScope.launch {
            when (val result = cameraController.enableTorch(next)) {
                is OptiResult.Success -> {
                    _internalState.update { it.copy(isTorchEnabled = next) }
                }
                is OptiResult.Error -> {
                    _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                }
                else -> Unit
            }
        }
    }

    fun flipCamera() {
        viewModelScope.launch {
            when (val result = cameraController.flipCamera()) {
                is OptiResult.Success -> {
                    _internalState.update { it.copy(isFrontCamera = cameraController.isFrontCamera) }
                }
                is OptiResult.Error -> {
                    _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                }
                else -> Unit
            }
        }
    }

    fun takePhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing) return

        _internalState.update { it.copy(isCapturing = true, isShutterBlinking = true) }
        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            when (val result = cameraController.capturePhoto(targetRotation)) {
                is OptiResult.Success -> {
                    _internalState.update { it.copy(isCapturing = false) }
                }
                is OptiResult.Error -> {
                    _internalState.update {
                        it.copy(
                            isCapturing = false,
                            errorMessage = result.error.displayMessage,
                        )
                    }
                }
                else -> {
                    _internalState.update { it.copy(isCapturing = false) }
                }
            }
        }
    }

    fun clearErrorMessage() {
        _internalState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        focusResetJob?.cancel()
        cameraController.release()
    }

    private data class CameraInternalState(
        val hasPermission: Boolean = false,
        val isFrontCamera: Boolean = false,
        val isTorchEnabled: Boolean = false,
        val isCapturing: Boolean = false,
        val focusTarget: Offset? = null,
        val isShutterBlinking: Boolean = false,
        val errorMessage: String? = null,
    )
}
