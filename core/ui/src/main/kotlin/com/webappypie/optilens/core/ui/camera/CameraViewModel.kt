package com.webappypie.optilens.core.ui.camera

import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.camera.CameraController
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.ExposureZebraData
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.FocusPeakingData
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.camera.model.HistogramMode
import com.webappypie.optilens.core.camera.model.LensMetadata
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.ProCameraState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.night.NightExecutionPlan
import com.webappypie.optilens.core.camera.night.NightModeType
import com.webappypie.optilens.core.camera.night.StabilityAssessment
import com.webappypie.optilens.core.camera.strategy.CaptureStrategy
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.portrait.PortraitAperture
import com.webappypie.optilens.core.camera.portrait.PortraitExecutionPlan
import com.webappypie.optilens.core.camera.model.CameraMode
import com.webappypie.optilens.core.camera.analysis.LensDirtyState
import com.webappypie.optilens.core.camera.bestshot.BestShotCandidate
import com.webappypie.optilens.core.camera.bestshot.BestShotEngine
import com.webappypie.optilens.core.camera.bestshot.BestShotResult
import com.webappypie.optilens.core.camera.specialized.PetModeEngine
import com.webappypie.optilens.core.camera.specialized.FoodModeEngine
import com.webappypie.optilens.core.camera.document.DocumentColorMode
import com.webappypie.optilens.core.camera.document.DocumentEngine
import com.webappypie.optilens.core.camera.document.DocumentOcrEngine
import com.webappypie.optilens.core.camera.document.DocumentQuad
import com.webappypie.optilens.core.camera.document.DocumentScanResult
import com.webappypie.optilens.core.settings.AppSettings
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
    val focusPeakingData: FocusPeakingData = FocusPeakingData.EMPTY,
    val exposureZebraData: ExposureZebraData = ExposureZebraData.EMPTY,
    val lensMetadata: LensMetadata = LensMetadata.EMPTY,
    val sceneClassification: SceneClassification = SceneClassification.DEFAULT,
    val qualityMetrics: QualityMetrics = QualityMetrics.DEFAULT,
    val motionState: MotionState = MotionState.DEFAULT,
    val captureStrategy: CaptureStrategy = CaptureStrategy.DEFAULT,
    val detectedFaces: List<DetectedFace> = emptyList(),
    val nightExecutionPlan: NightExecutionPlan? = null,
    val portraitExecutionPlan: PortraitExecutionPlan? = null,
    val portraitAperture: PortraitAperture = PortraitAperture.DEFAULT,
    val isPreviewBoostActive: Boolean = false,
    val holdSteadyRemainingSec: Float? = null,
    val stabilityAssessment: StabilityAssessment? = null,
    val thermalState: DeviceThermalState = DeviceThermalState.NORMAL,
    val isProcessingNightShot: Boolean = false,
    val isProcessingPortraitShot: Boolean = false,
    val mirrorFrontCameraSelfie: Boolean = true,
    val timerState: TimerState = TimerState.OFF,
    val timerCountdown: Int? = null,
    val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
    val sessionState: CameraSessionState = CameraSessionState.IDLE,
    val isCapturing: Boolean = false,
    val isBurstCapturing: Boolean = false,
    val lastCapturedPhoto: CapturedPhoto? = null,
    val lastBurstResult: BurstResult? = null,
    val focusTarget: Offset? = null,
    val isShutterBlinking: Boolean = false,
    val currentMode: CameraMode = CameraMode.PHOTO,
    val lensDirtyState: LensDirtyState = LensDirtyState.CLEAN,
    val bestShotResult: BestShotResult? = null,
    val isEvaluatingBestShot: Boolean = false,
    val isBestShotSheetVisible: Boolean = false,
    val documentColorMode: DocumentColorMode = DocumentColorMode.COLOR,
    val detectedDocumentQuad: DocumentQuad? = null,
    val documentScanResult: DocumentScanResult? = null,
    val isProcessingDocument: Boolean = false,
    val isProcessingPetShot: Boolean = false,
    val isProcessingFoodShot: Boolean = false,
    val ocrExtractedText: String? = null,
    val isExtractingOcr: Boolean = false,
    val errorMessage: String? = null,
) {
    val thermalWarningMessage: String?
        get() = when (thermalState) {
            DeviceThermalState.CRITICAL -> "Device is hot. Camera features reduced to prevent overheating."
            DeviceThermalState.SEVERE -> "Device warm. Camera features limited to cool down."
            else -> null
        }
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val cameraController: CameraController,
    private val appSettings: AppSettings? = null,
    private val bestShotEngine: BestShotEngine = BestShotEngine(),
    private val petModeEngine: PetModeEngine = PetModeEngine(),
    private val foodModeEngine: FoodModeEngine = FoodModeEngine(),
    private val documentEngine: DocumentEngine = DocumentEngine(),
    private val documentOcrEngine: DocumentOcrEngine = DocumentOcrEngine(),
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
        appSettings?.let { settings ->
            viewModelScope.launch {
                settings.mirrorFrontCameraSelfie.collect { mirror ->
                    _internalState.update { it.copy(mirrorFrontCameraSelfie = mirror) }
                }
            }
            viewModelScope.launch {
                settings.rawCaptureEnabled.collect { enabled ->
                    cameraController.setRawCaptureEnabled(enabled)
                }
            }
            viewModelScope.launch {
                settings.rawCaptureFormat.collect { format ->
                    cameraController.setRawCaptureFormat(format.toModel())
                }
            }
            viewModelScope.launch {
                settings.rawCompanionJpegEnabled.collect { companion ->
                    cameraController.setSaveCompanionJpeg(companion)
                }
            }
            viewModelScope.launch {
                settings.focusPeakingEnabled.collect { peaking ->
                    cameraController.setFocusPeakingEnabled(peaking)
                }
            }
            viewModelScope.launch {
                settings.exposureZebraEnabled.collect { zebra ->
                    cameraController.setExposureZebraEnabled(zebra)
                }
            }
            viewModelScope.launch {
                settings.histogramMode.collect { mode ->
                    cameraController.setHistogramMode(mode.toModel())
                }
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
        val lastBurst: BurstResult?,
    )

    private data class IntelligenceStreamState(
        val scene: SceneClassification,
        val quality: QualityMetrics,
        val motion: MotionState,
        val strategy: CaptureStrategy,
        val faces: List<DetectedFace>,
        val lensDirty: LensDirtyState,
    )

    private data class NightStreamState(
        val plan: NightExecutionPlan?,
        val portraitPlan: PortraitExecutionPlan?,
        val isPreviewBoost: Boolean,
        val stability: StabilityAssessment,
        val thermal: DeviceThermalState,
    )

    private data class ProAssistanceStreamState(
        val peaking: FocusPeakingData,
        val zebra: ExposureZebraData,
        val lensMetadata: LensMetadata,
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
        cameraController.lastBurstResult,
    ) { pro, hist, photo, burst ->
        HardwareStreamState2(pro, hist, photo, burst)
    }

    private val _streamIntelligenceBase = combine(
        cameraController.sceneClassification,
        cameraController.qualityMetrics,
        cameraController.motionState,
        cameraController.captureStrategy,
        cameraController.detectedFaces,
    ) { scene, quality, motion, strategy, faces ->
        Tuple5(scene, quality, motion, strategy, faces)
    }

    private val _streamIntelligence = combine(
        _streamIntelligenceBase,
        cameraController.lensDirtyState,
    ) { base, lensDirty ->
        IntelligenceStreamState(base.a, base.b, base.c, base.d, base.e, lensDirty)
    }

    private val _streamNight = combine(
        cameraController.nightExecutionPlan,
        cameraController.portraitExecutionPlan,
        cameraController.isPreviewBoostActive,
        cameraController.stabilityAssessment,
        cameraController.thermalState,
    ) { plan, portPlan, boost, stab, therm ->
        NightStreamState(plan, portPlan, boost, stab, therm)
    }

    private val _streamProAssistance = combine(
        cameraController.focusPeakingData,
        cameraController.exposureZebraData,
        cameraController.lensMetadata,
    ) { peaking, zebra, lens ->
        ProAssistanceStreamState(peaking, zebra, lens)
    }

    val uiState: StateFlow<CameraUiState> = combine(
        _internalState,
        cameraController.sessionState,
        cameraController.zoomState,
        combine(_stream1, _stream2, _streamIntelligence, _streamNight, _streamProAssistance) { s1, s2, intel, night, assist ->
            Tuple5(s1, s2, intel, night, assist)
        }
    ) { internal, session, zoom, (s1, s2, intel, night, assist) ->
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
            focusPeakingData = assist.peaking,
            exposureZebraData = assist.zebra,
            lensMetadata = assist.lensMetadata,
            sceneClassification = intel.scene,
            qualityMetrics = intel.quality,
            motionState = intel.motion,
            captureStrategy = intel.strategy,
            detectedFaces = intel.faces,
            nightExecutionPlan = night.plan,
            portraitExecutionPlan = night.portraitPlan,
            portraitAperture = internal.portraitAperture,
            isPreviewBoostActive = night.isPreviewBoost,
            holdSteadyRemainingSec = internal.holdSteadyRemainingSec,
            stabilityAssessment = night.stability,
            thermalState = night.thermal,
            isProcessingNightShot = internal.isProcessingNightShot,
            isProcessingPortraitShot = internal.isProcessingPortraitShot,
            mirrorFrontCameraSelfie = internal.mirrorFrontCameraSelfie,
            timerState = internal.timerState,
            timerCountdown = internal.timerCountdown,
            aspectRatio = internal.aspectRatio,
            sessionState = session,
            isCapturing = internal.isCapturing,
            isBurstCapturing = internal.isBurstCapturing,
            lastCapturedPhoto = s2.lastPhoto,
            lastBurstResult = s2.lastBurst,
            focusTarget = internal.focusTarget,
            isShutterBlinking = internal.isShutterBlinking,
            currentMode = internal.currentMode,
            lensDirtyState = intel.lensDirty,
            bestShotResult = internal.bestShotResult,
            isEvaluatingBestShot = internal.isEvaluatingBestShot,
            isBestShotSheetVisible = internal.isBestShotSheetVisible,
            documentColorMode = internal.documentColorMode,
            detectedDocumentQuad = internal.detectedDocumentQuad,
            documentScanResult = internal.documentScanResult,
            isProcessingDocument = internal.isProcessingDocument,
            isProcessingPetShot = internal.isProcessingPetShot,
            isProcessingFoodShot = internal.isProcessingFoodShot,
            ocrExtractedText = internal.ocrExtractedText,
            isExtractingOcr = internal.isExtractingOcr,
            errorMessage = internal.errorMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = CameraUiState(),
    )

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

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

    fun setRawCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            cameraController.setRawCaptureEnabled(enabled)
            appSettings?.setRawCaptureEnabled(enabled)
        }
    }

    fun setRawCaptureFormat(format: RawCaptureFormat) {
        viewModelScope.launch {
            cameraController.setRawCaptureFormat(format)
            appSettings?.setRawCaptureFormat(format.toSetting())
        }
    }

    fun setSaveCompanionJpeg(enabled: Boolean) {
        viewModelScope.launch {
            cameraController.setSaveCompanionJpeg(enabled)
            appSettings?.setRawCompanionJpegEnabled(enabled)
        }
    }

    fun toggleFocusPeaking() {
        val next = !uiState.value.focusPeakingData.isEnabled
        viewModelScope.launch {
            cameraController.setFocusPeakingEnabled(next)
            appSettings?.setFocusPeakingEnabled(next)
        }
    }

    fun toggleExposureZebra() {
        val next = !uiState.value.exposureZebraData.isEnabled
        viewModelScope.launch {
            cameraController.setExposureZebraEnabled(next)
            appSettings?.setExposureZebraEnabled(next)
        }
    }

    fun setHistogramMode(mode: HistogramMode) {
        viewModelScope.launch {
            cameraController.setHistogramMode(mode)
            appSettings?.setHistogramMode(mode.toSetting())
        }
    }

    fun cycleHistogramMode() {
        val next = uiState.value.proState.histogramMode.next()
        setHistogramMode(next)
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

            val shouldMirror = _internalState.value.isFrontCamera && _internalState.value.mirrorFrontCameraSelfie
            when (val result = cameraController.capturePhoto(targetRotation, mirrorHorizontal = shouldMirror)) {
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

    fun takeBurstPhoto(frameCount: Int? = null, targetRotation: Int = 0) {
        if (_internalState.value.isBurstCapturing || _internalState.value.isCapturing) return

        _internalState.update { it.copy(isBurstCapturing = true, isShutterBlinking = true) }
        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            val strategy = uiState.value.captureStrategy
            val count = frameCount ?: strategy.recommendedFrameCount
            val evOffsets = strategy.exposureEvOffsets
            when (val result = cameraController.acquireBurst(count, evOffsets, targetRotation)) {
                is OptiResult.Success -> {
                    _internalState.update { it.copy(isBurstCapturing = false) }
                }
                is OptiResult.Error -> {
                    _internalState.update {
                        it.copy(
                            isBurstCapturing = false,
                            errorMessage = result.error.displayMessage,
                        )
                    }
                }
                else -> {
                    _internalState.update { it.copy(isBurstCapturing = false) }
                }
            }
        }
    }

    fun togglePreviewLowLightBoost() {
        val next = !uiState.value.isPreviewBoostActive
        viewModelScope.launch {
            cameraController.enablePreviewLowLightBoost(next)
        }
    }

    fun takeNightPhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing || _internalState.value.isBurstCapturing) return

        val plan = uiState.value.nightExecutionPlan
        val expectedDurationMs = plan?.exposurePlan?.expectedCaptureDurationMs ?: 1200L

        _internalState.update {
            it.copy(
                isCapturing = true,
                isShutterBlinking = true,
                holdSteadyRemainingSec = expectedDurationMs / 1000f,
            )
        }

        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            // Animate hold steady countdown smoothly
            val countdownJob = launch {
                val totalSteps = (expectedDurationMs / 100).toInt().coerceAtLeast(1)
                for (step in totalSteps downTo 0) {
                    val remainingSec = (step * 100) / 1000f
                    _internalState.update { it.copy(holdSteadyRemainingSec = remainingSec) }
                    delay(100L)
                }
            }

            try {
                val burstJob = launch {
                    if (plan?.mode == NightModeType.VENDOR_EXTENSION) {
                        when (val result = cameraController.capturePhoto(targetRotation)) {
                            is OptiResult.Error -> _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                            else -> Unit
                        }
                    } else {
                        val frameCount = plan?.exposurePlan?.frameCount ?: 6
                        val offsets = plan?.exposurePlan?.evOffsets ?: listOf(0)
                        when (val result = cameraController.acquireBurst(frameCount, offsets, targetRotation)) {
                            is OptiResult.Error -> _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                            else -> Unit
                        }
                    }
                }
                burstJob.join()
                countdownJob.join()
            } finally {
                countdownJob.cancel()
                _internalState.update {
                    it.copy(
                        holdSteadyRemainingSec = null,
                        isProcessingNightShot = true,
                    )
                }
                delay(300L) // Brief processing visual state
                _internalState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessingNightShot = false,
                    )
                }
            }
        }
    }

    fun setPortraitAperture(aperture: PortraitAperture) {
        _internalState.update { it.copy(portraitAperture = aperture) }
        viewModelScope.launch {
            cameraController.setPortraitAperture(aperture)
        }
    }

    fun takePortraitPhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing) return

        _internalState.update {
            it.copy(
                isCapturing = true,
                isShutterBlinking = true,
                isProcessingPortraitShot = true,
            )
        }

        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            val shouldMirror = _internalState.value.isFrontCamera && _internalState.value.mirrorFrontCameraSelfie
            try {
                when (val result = cameraController.capturePhoto(targetRotation, mirrorHorizontal = shouldMirror)) {
                    is OptiResult.Error -> {
                        _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                    }
                    else -> Unit
                }
            } finally {
                delay(200L)
                _internalState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessingPortraitShot = false,
                    )
                }
            }
        }
    }

    fun setCameraMode(mode: CameraMode) {
        _internalState.update { it.copy(currentMode = mode) }
        viewModelScope.launch {
            cameraController.setCameraMode(mode)
        }
    }

    fun dismissLensDirtyPrompt() {
        cameraController.dismissLensDirtyPrompt()
    }

    fun takeBestShotPhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing || _internalState.value.isBurstCapturing) return

        _internalState.update {
            it.copy(
                isCapturing = true,
                isBurstCapturing = true,
                isShutterBlinking = true,
                isEvaluatingBestShot = true,
            )
        }

        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            try {
                val burstResult = cameraController.acquireBurst(
                    frameCount = 6,
                    evOffsets = listOf(0),
                    targetRotation = targetRotation,
                )

                when (burstResult) {
                    is OptiResult.Success -> {
                        val result = bestShotEngine.evaluateBurst(
                            burst = burstResult.data,
                            detectedFaces = uiState.value.detectedFaces,
                        )
                        _internalState.update {
                            it.copy(
                                bestShotResult = result,
                                isBestShotSheetVisible = true,
                            )
                        }
                    }
                    is OptiResult.Error -> {
                        _internalState.update { it.copy(errorMessage = burstResult.error.displayMessage) }
                    }
                    else -> Unit
                }
            } finally {
                _internalState.update {
                    it.copy(
                        isCapturing = false,
                        isBurstCapturing = false,
                        isEvaluatingBestShot = false,
                    )
                }
            }
        }
    }

    fun overrideBestShotSelection(candidateIndex: Int) {
        _internalState.update { state ->
            val updatedResult = state.bestShotResult?.withManualOverride(candidateIndex)
            state.copy(bestShotResult = updatedResult)
        }
    }

    fun dismissBestShotSheet() {
        _internalState.update { it.copy(isBestShotSheetVisible = false) }
    }

    fun takePetPhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing) return

        _internalState.update {
            it.copy(
                isCapturing = true,
                isShutterBlinking = true,
                isProcessingPetShot = true,
            )
        }

        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            try {
                val petConfig = petModeEngine.computeCaptureConfig(
                    quality = uiState.value.qualityMetrics,
                    motion = uiState.value.motionState,
                )
                cameraController.setShutterSpeed(petConfig.targetShutterSpeedNanos)
                when (val result = cameraController.capturePhoto(targetRotation)) {
                    is OptiResult.Error -> _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                    else -> Unit
                }
            } finally {
                delay(150L)
                _internalState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessingPetShot = false,
                    )
                }
            }
        }
    }

    fun takeFoodPhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing) return

        _internalState.update {
            it.copy(
                isCapturing = true,
                isShutterBlinking = true,
                isProcessingFoodShot = true,
            )
        }

        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            try {
                foodModeEngine.getStabilizedWhiteBalance(5200)
                when (val result = cameraController.capturePhoto(targetRotation)) {
                    is OptiResult.Error -> _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                    else -> Unit
                }
            } finally {
                delay(150L)
                _internalState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessingFoodShot = false,
                    )
                }
            }
        }
    }

    fun setDocumentColorMode(colorMode: DocumentColorMode) {
        _internalState.update { it.copy(documentColorMode = colorMode) }
    }

    fun takeDocumentPhoto(targetRotation: Int = 0) {
        if (_internalState.value.isCapturing) return

        _internalState.update {
            it.copy(
                isCapturing = true,
                isShutterBlinking = true,
                isProcessingDocument = true,
            )
        }

        viewModelScope.launch {
            launch {
                delay(80L)
                _internalState.update { it.copy(isShutterBlinking = false) }
            }

            try {
                when (val result = cameraController.capturePhoto(targetRotation)) {
                    is OptiResult.Success -> {
                        val scanResult = DocumentScanResult(
                            uri = result.data.uri,
                            quad = _internalState.value.detectedDocumentQuad ?: DocumentQuad.DEFAULT,
                            colorMode = _internalState.value.documentColorMode,
                            width = result.data.width,
                            height = result.data.height,
                        )
                        _internalState.update { it.copy(documentScanResult = scanResult) }
                    }
                    is OptiResult.Error -> {
                        _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                    }
                    else -> Unit
                }
            } finally {
                delay(200L)
                _internalState.update {
                    it.copy(
                        isCapturing = false,
                        isProcessingDocument = false,
                    )
                }
            }
        }
    }

    /**
     * Decoupled OCR text extraction triggered ONLY as an on-demand separate action.
     */
    fun extractDocumentOcr() {
        val uri = _internalState.value.documentScanResult?.uri
            ?: uiState.value.lastCapturedPhoto?.uri
            ?: return

        if (_internalState.value.isExtractingOcr) return

        _internalState.update { it.copy(isExtractingOcr = true) }

        viewModelScope.launch {
            try {
                when (val result = documentOcrEngine.extractTextFromUri(uri)) {
                    is OptiResult.Success -> {
                        _internalState.update { it.copy(ocrExtractedText = result.data) }
                    }
                    is OptiResult.Error -> {
                        _internalState.update { it.copy(errorMessage = result.error.displayMessage) }
                    }
                    else -> Unit
                }
            } finally {
                _internalState.update { it.copy(isExtractingOcr = false) }
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
        val isBurstCapturing: Boolean = false,
        val holdSteadyRemainingSec: Float? = null,
        val isProcessingNightShot: Boolean = false,
        val isProcessingPortraitShot: Boolean = false,
        val portraitAperture: PortraitAperture = PortraitAperture.DEFAULT,
        val mirrorFrontCameraSelfie: Boolean = true,
        val focusTarget: Offset? = null,
        val isShutterBlinking: Boolean = false,
        val timerState: TimerState = TimerState.OFF,
        val timerCountdown: Int? = null,
        val aspectRatio: CameraAspectRatio = CameraAspectRatio.RATIO_4_3,
        val isHistogramVisible: Boolean = false,
        val currentMode: CameraMode = CameraMode.PHOTO,
        val bestShotResult: BestShotResult? = null,
        val isEvaluatingBestShot: Boolean = false,
        val isBestShotSheetVisible: Boolean = false,
        val documentColorMode: DocumentColorMode = DocumentColorMode.COLOR,
        val detectedDocumentQuad: DocumentQuad? = null,
        val documentScanResult: DocumentScanResult? = null,
        val isProcessingDocument: Boolean = false,
        val isProcessingPetShot: Boolean = false,
        val isProcessingFoodShot: Boolean = false,
        val ocrExtractedText: String? = null,
        val isExtractingOcr: Boolean = false,
        val errorMessage: String? = null,
    )
}

private fun com.webappypie.optilens.core.settings.RawCaptureFormatSetting.toModel(): RawCaptureFormat = when (this) {
    com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW_SENSOR -> RawCaptureFormat.RAW_SENSOR
    com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW10 -> RawCaptureFormat.RAW10
    com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW12 -> RawCaptureFormat.RAW12
    com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW_PRIVATE -> RawCaptureFormat.RAW_PRIVATE
}

private fun RawCaptureFormat.toSetting(): com.webappypie.optilens.core.settings.RawCaptureFormatSetting = when (this) {
    RawCaptureFormat.RAW_SENSOR -> com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW_SENSOR
    RawCaptureFormat.RAW10 -> com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW10
    RawCaptureFormat.RAW12 -> com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW12
    RawCaptureFormat.RAW_PRIVATE -> com.webappypie.optilens.core.settings.RawCaptureFormatSetting.RAW_PRIVATE
}

private fun com.webappypie.optilens.core.settings.HistogramModeSetting.toModel(): HistogramMode = when (this) {
    com.webappypie.optilens.core.settings.HistogramModeSetting.LUMINANCE -> HistogramMode.LUMINANCE
    com.webappypie.optilens.core.settings.HistogramModeSetting.RGB -> HistogramMode.RGB
    com.webappypie.optilens.core.settings.HistogramModeSetting.BOTH -> HistogramMode.BOTH
}

private fun HistogramMode.toSetting(): com.webappypie.optilens.core.settings.HistogramModeSetting = when (this) {
    HistogramMode.LUMINANCE -> com.webappypie.optilens.core.settings.HistogramModeSetting.LUMINANCE
    HistogramMode.RGB -> com.webappypie.optilens.core.settings.HistogramModeSetting.RGB
    HistogramMode.BOTH -> com.webappypie.optilens.core.settings.HistogramModeSetting.BOTH
}

