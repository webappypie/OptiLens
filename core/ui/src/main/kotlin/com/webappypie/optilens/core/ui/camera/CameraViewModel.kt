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
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.ProCameraState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.strategy.CaptureStrategy
import com.webappypie.optilens.core.common.result.OptiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

enum class CameraAspectRatio(val ratio: Float, val label: String) {
    RATIO_4_3(3f / 4f, "4:3"),
    RATIO_16_9(9f / 16f, "16:9"),
    RATIO_1_1(1f / 1f, "1:1");
}

enum class TimerState(val seconds: Int) {
    OFF(0),
    SEC_3(3),
    SEC_10(10);
}

/**
 * UI State representing camera viewfinder, session status, Pro controls, and capture status.
 */
data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val isFrontCamera: Boolean = false,
    val flashMode: FlashMode = FlashMode.AUTO,
    val isTorchEnabled: Boolean = false,
    val zoomState: ZoomState = ZoomState(),
    val zoomStops: List<ZoomStop> = emptyList(),
    val exposureState: ExposureState = ExposureState(),
    val proState: ProCameraState = ProCameraState(),
    val histogramData: HistogramData = HistogramData.EMPTY,
    val isHistogramVisible: Boolean = false,
    val sceneClassification: SceneClassification = SceneClassification.DEFAULT,
    val qualityMetrics: QualityMetrics = QualityMetrics.DEFAULT,
    val motionState: MotionState = MotionState.DEFAULT,
    val captureStrategy: CaptureStrategy = CaptureStrategy.DEFAULT,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val timerState: TimerState = TimerState.OFF,
    val timerCountdown: Int? = null,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
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
    private var timerJob: Job? = null

    private val _opticalHapticFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val opticalHapticFlow: SharedFlow<Unit> = _opticalHapticFlow.asSharedFlow()

    private var previousZoom = 1.0f
    private var activeZoomStops: List<ZoomStop> = emptyList()

    init {
        viewModelScope.launch {
            cameraController.zoomStops.collect { stops ->
                activeZoomStops = stops
            }
        }
    }

    private data class HardwareStreamState1(
        val zoomStops: List<ZoomStop>,
        val flash: FlashMode,
        val exposure: ExposureState,
    )

    private data class HardwareStreamState2(
        val proState: ProCameraState,
        val histogram: HistogramData,
        val lastPhoto: CapturedPhoto?,
    )

    private data class IntelligenceStreamState(
        val scene: SceneClassification,
        val quality: QualityMetrics,
        val motion: MotionState,
        val strategy: CaptureStrategy,
        val faces: List<DetectedFace>,
    )

    private val _stream1 = combine(
        cameraController.zoomStops,
        cameraController.flashMode,
        cameraController.exposureState,
    ) { stops, flash, exp ->
        HardwareStreamState1(stops, flash, exp)
    }

    private val _stream2 = combine(
        cameraController.proState,
        cameraController.histogramData,
        cameraController.lastCapturedPhoto,
    ) { pro, hist, photo ->
        HardwareStreamState2(pro, hist, photo)
    }

    private val _streamIntelligence = combine(
        cameraController.sceneClassification,
        cameraController.qualityMetrics,
        cameraController.motionState,
        cameraController.captureStrategy,
        cameraController.detectedFaces,
    ) { scene, quality, motion, strategy, faces ->
        IntelligenceStreamState(scene, quality, motion, strategy, faces)
    }

    val uiState: StateFlow<CameraUiState> = combine(
        _internalState,
        cameraController.sessionState,
        cameraController.zoomState,
        combine(_stream1, _stream2, _streamIntelligence) { s1, s2, intel -> Triple(s1, s2, intel) }
    ) { internal, session, zoom, (s1, s2, intel) ->
        CameraUiState(
            hasCameraPermission = internal.hasPermission,
            isFrontCamera = internal.isFrontCamera,
            flashMode = s1.flash,
            isTorchEnabled = internal.isTorchEnabled,
            zoomState = zoom,
            zoomStops = s1.zoomStops,
            exposureState = s1.exposure,
            proState = s2.proState,
            histogramData = s2.histogram,
            isHistogramVisible = internal.isHistogramVisible,
            sceneClassification = intel.scene,
            qualityMetrics = intel.quality,
            motionState = intel.motion,
            captureStrategy = intel.strategy,
            detectedFaces = intel.faces,
            timerState = internal.timerState,
            timerCountdown = internal.timerCountdown,
            aspectRatio = internal.aspectRatio,
            sessionState = session,
            isCapturing = internal.isCapturing,
            lastCapturedPhoto = s2.lastPhoto,
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
        val stops = if (activeZoomStops.isNotEmpty()) {
            activeZoomStops.filter { it.isOptical }
        } else {
            uiState.value.zoomStops.filter { it.isOptical }
        }
        for (stop in stops) {
            if (abs(ratio - stop.ratio) < 0.05f && abs(previousZoom - stop.ratio) >= 0.05f) {
                _opticalHapticFlow.tryEmit(Unit)
                break
            }
        }
        previousZoom = ratio

        viewModelScope.launch {
            cameraController.setZoom(ratio)
        }
    }

    fun onExposureCompensationChanged(index: Int) {
        viewModelScope.launch {
            cameraController.setExposureCompensation(index)
        }
    }

    fun setIso(iso: Int?) {
        viewModelScope.launch {
            cameraController.setIso(iso)
        }
    }

    fun setShutterSpeed(nanos: Long?) {
        viewModelScope.launch {
            cameraController.setShutterSpeed(nanos)
        }
    }

    fun setFocusDistance(distance: Float?) {
        viewModelScope.launch {
            cameraController.setFocusDistance(distance)
        }
    }

    fun setWhiteBalance(mode: WhiteBalanceMode) {
        viewModelScope.launch {
            cameraController.setWhiteBalance(mode)
        }
    }

    fun resetProToAuto() {
        viewModelScope.launch {
            cameraController.resetProToAuto()
        }
    }

    fun toggleHistogram() {
        val next = !_internalState.value.isHistogramVisible
        _internalState.update { it.copy(isHistogramVisible = next) }
        cameraController.setHistogramEnabled(next)
    }

    fun toggleAspectRatio() {
        val next = when (_internalState.value.aspectRatio) {
            CameraAspectRatio.RATIO_4_3 -> CameraAspectRatio.RATIO_16_9
            CameraAspectRatio.RATIO_16_9 -> CameraAspectRatio.RATIO_1_1
            CameraAspectRatio.RATIO_1_1 -> CameraAspectRatio.RATIO_4_3
        }
        _internalState.update { it.copy(aspectRatio = next) }
    }

    fun toggleTimer() {
        val next = when (_internalState.value.timerState) {
            TimerState.OFF -> TimerState.SEC_3
            TimerState.SEC_3 -> TimerState.SEC_10
            TimerState.SEC_10 -> TimerState.OFF
        }
        _internalState.update { it.copy(timerState = next) }
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

    fun takePhotoWithTimer(targetRotation: Int = 0) {
        val timer = _internalState.value.timerState
        if (timer == TimerState.OFF) {
            takePhoto(targetRotation)
            return
        }

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            for (sec in timer.seconds downTo 1) {
                _internalState.update { it.copy(timerCountdown = sec) }
                delay(1_000L)
            }
            _internalState.update { it.copy(timerCountdown = null) }
            takePhoto(targetRotation)
        }
    }

    fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
        _internalState.update { it.copy(timerCountdown = null) }
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
        timerJob?.cancel()
        cameraController.release()
    }

    private data class CameraInternalState(
        val hasPermission: Boolean = false,
        val isFrontCamera: Boolean = false,
        val isTorchEnabled: Boolean = false,
        val isCapturing: Boolean = false,
        val focusTarget: Offset? = null,
        val isShutterBlinking: Boolean = false,
        val timerState: TimerState = TimerState.OFF,
        val timerCountdown: Int? = null,
        val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
        val isHistogramVisible: Boolean = false,
        val errorMessage: String? = null,
    )
}
