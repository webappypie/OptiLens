package com.webappypie.optilens.core.camera

import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.camera.model.ProCameraState
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.common.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake and test implementation of [CameraController] used for unit testing
 * without requiring physical camera hardware.
 */
@Singleton
class FakeCameraController @Inject constructor() : CameraController {

    private val _capabilities = MutableStateFlow(
        CameraCapability(
            hasRearCamera = true,
            hasFrontCamera = true,
            hasOis = true,
            supportsRaw = true,
            supportsHdrCapture = true,
            maxCaptureStreams = 2,
            hasLogicalMultiCamera = true,
            availableZoomRatios = listOf(0.6f, 1.0f, 2.0f, 5.0f),
        )
    )
    override val capabilities: Flow<CameraCapability> = _capabilities.asStateFlow()

    private val _sessionState = MutableStateFlow(CameraSessionState.IDLE)
    override val sessionState: Flow<CameraSessionState> = _sessionState.asStateFlow()

    private val _zoomState = MutableStateFlow(
        ZoomState(
            currentZoom = 1.0f,
            minZoom = 0.6f,
            maxZoom = 10.0f,
            linearZoom = 0.1f,
        )
    )
    override val zoomState: Flow<ZoomState> = _zoomState.asStateFlow()

    private val _zoomStops = MutableStateFlow(
        listOf(
            ZoomStop(ratio = 0.6f, label = "0.6x", isOptical = true, physicalSensorId = "2"),
            ZoomStop(ratio = 1.0f, label = "1x", isOptical = true, physicalSensorId = "0"),
            ZoomStop(ratio = 2.0f, label = "2x", isOptical = false, physicalSensorId = null), // Truthful digital crop
            ZoomStop(ratio = 5.0f, label = "5x", isOptical = true, physicalSensorId = "3"),
        )
    )
    override val zoomStops: Flow<List<ZoomStop>> = _zoomStops.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashMode.AUTO)
    override val flashMode: Flow<FlashMode> = _flashMode.asStateFlow()

    private val _exposureState = MutableStateFlow(
        ExposureState(
            index = 0,
            minIndex = -12,
            maxIndex = 12,
            step = 0.333f,
        )
    )
    override val exposureState: Flow<ExposureState> = _exposureState.asStateFlow()

    private val _proState = MutableStateFlow(
        ProCameraState(
            isoRange = 50..3200,
            isIsoManualSupported = true,
            shutterSpeedRangeNanos = 100_000L..1_000_000_000L,
            isShutterManualSupported = true,
            isFocusManualSupported = true,
            isWhiteBalanceSupported = true,
            evRange = -12..12,
            evStep = 0.333f,
        )
    )
    override val proState: Flow<ProCameraState> = _proState.asStateFlow()

    private val _histogramData = MutableStateFlow(
        HistogramData(lumaBins = FloatArray(64) { (it / 64f) })
    )
    override val histogramData: Flow<HistogramData> = _histogramData.asStateFlow()

    private val _focusPeakingData = MutableStateFlow(com.webappypie.optilens.core.camera.model.FocusPeakingData.EMPTY)
    override val focusPeakingData: Flow<com.webappypie.optilens.core.camera.model.FocusPeakingData> = _focusPeakingData.asStateFlow()

    private val _exposureZebraData = MutableStateFlow(com.webappypie.optilens.core.camera.model.ExposureZebraData.EMPTY)
    override val exposureZebraData: Flow<com.webappypie.optilens.core.camera.model.ExposureZebraData> = _exposureZebraData.asStateFlow()

    private val _lensMetadata = MutableStateFlow(
        com.webappypie.optilens.core.camera.model.LensMetadata(
            focalLengthMm = 4.5f,
            focalLength35mmEquivalent = 24,
            apertureFNumber = 1.9f,
            minFocusDistanceDiopters = 10.0f,
            currentFocusDistanceDiopters = 1.0f,
            currentIso = 100,
            currentShutterSpeedNanos = 16_666_666L,
            sensorWidthMm = 6.4f,
            sensorHeightMm = 4.8f,
            isFixedFocus = false,
        )
    )
    override val lensMetadata: Flow<com.webappypie.optilens.core.camera.model.LensMetadata> = _lensMetadata.asStateFlow()

    private val _sceneClassification = MutableStateFlow(com.webappypie.optilens.core.camera.model.SceneClassification.DEFAULT)
    override val sceneClassification: Flow<com.webappypie.optilens.core.camera.model.SceneClassification> = _sceneClassification.asStateFlow()

    private val _qualityMetrics = MutableStateFlow(com.webappypie.optilens.core.camera.model.QualityMetrics.DEFAULT)
    override val qualityMetrics: Flow<com.webappypie.optilens.core.camera.model.QualityMetrics> = _qualityMetrics.asStateFlow()

    private val _motionState = MutableStateFlow(com.webappypie.optilens.core.camera.model.MotionState.DEFAULT)
    override val motionState: Flow<com.webappypie.optilens.core.camera.model.MotionState> = _motionState.asStateFlow()

    private val _captureStrategy = MutableStateFlow(com.webappypie.optilens.core.camera.strategy.CaptureStrategy.DEFAULT)
    override val captureStrategy: Flow<com.webappypie.optilens.core.camera.strategy.CaptureStrategy> = _captureStrategy.asStateFlow()

    private val _detectedFaces = MutableStateFlow<List<com.webappypie.optilens.core.camera.model.DetectedFace>>(emptyList())
    override val detectedFaces: Flow<List<com.webappypie.optilens.core.camera.model.DetectedFace>> = _detectedFaces.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    override val lastCapturedPhoto: Flow<CapturedPhoto?> = _lastCapturedPhoto.asStateFlow()

    private val _lastBurstResult = MutableStateFlow<com.webappypie.optilens.core.camera.burst.model.BurstResult?>(null)
    override val lastBurstResult: Flow<com.webappypie.optilens.core.camera.burst.model.BurstResult?> = _lastBurstResult.asStateFlow()

    private val _nightExecutionPlan = MutableStateFlow<com.webappypie.optilens.core.camera.night.NightExecutionPlan?>(null)
    override val nightExecutionPlan: Flow<com.webappypie.optilens.core.camera.night.NightExecutionPlan?> = _nightExecutionPlan.asStateFlow()

    private val _portraitExecutionPlan = MutableStateFlow<com.webappypie.optilens.core.camera.portrait.PortraitExecutionPlan?>(null)
    override val portraitExecutionPlan: Flow<com.webappypie.optilens.core.camera.portrait.PortraitExecutionPlan?> = _portraitExecutionPlan.asStateFlow()

    private val _isPreviewBoostActive = MutableStateFlow(false)
    override val isPreviewBoostActive: Flow<Boolean> = _isPreviewBoostActive.asStateFlow()

    private val _stabilityAssessment = MutableStateFlow(
        com.webappypie.optilens.core.camera.night.StabilityAssessment(
            classification = com.webappypie.optilens.core.camera.night.StabilityClassification.HANDHELD_STABLE,
            stabilityScore = 90.0f,
            stabilityConfidence = 0.90f,
            averageAngularVelocity = 0.05f,
            isTripod = false,
            timestampMs = System.currentTimeMillis(),
        )
    )
    override val stabilityAssessment: Flow<com.webappypie.optilens.core.camera.night.StabilityAssessment> = _stabilityAssessment.asStateFlow()

    private val _thermalState = MutableStateFlow(com.webappypie.optilens.core.camera.thermal.DeviceThermalState.NORMAL)
    override val thermalState: Flow<com.webappypie.optilens.core.camera.thermal.DeviceThermalState> = _thermalState.asStateFlow()

    private val _lensDirtyState = MutableStateFlow(com.webappypie.optilens.core.camera.analysis.LensDirtyState.CLEAN)
    override val lensDirtyState: Flow<com.webappypie.optilens.core.camera.analysis.LensDirtyState> = _lensDirtyState.asStateFlow()

    private val _activeCameraMode = MutableStateFlow(com.webappypie.optilens.core.camera.model.CameraMode.PHOTO)
    override val activeCameraMode: Flow<com.webappypie.optilens.core.camera.model.CameraMode> = _activeCameraMode.asStateFlow()

    private val _moonDetectionState = MutableStateFlow(com.webappypie.optilens.core.camera.moon.MoonDetectionState.EMPTY)
    override val moonDetectionState: Flow<com.webappypie.optilens.core.camera.moon.MoonDetectionState> = _moonDetectionState.asStateFlow()

    private val _wildlifeDetectionState = MutableStateFlow(com.webappypie.optilens.core.camera.wildlife.WildlifeDetectionState.EMPTY)
    override val wildlifeDetectionState: Flow<com.webappypie.optilens.core.camera.wildlife.WildlifeDetectionState> = _wildlifeDetectionState.asStateFlow()

    private val _trackedObjectState = MutableStateFlow(com.webappypie.optilens.core.camera.tracking.TrackedObjectState.INACTIVE)
    override val trackedObjectState: Flow<com.webappypie.optilens.core.camera.tracking.TrackedObjectState> = _trackedObjectState.asStateFlow()

    override fun dismissLensDirtyPrompt() {
        _lensDirtyState.value = _lensDirtyState.value.copy(isDismissed = true)
    }

    override fun startObjectTracking(normTapX: Float, normTapY: Float) {
        val half = 0.09f
        val bounds = com.webappypie.optilens.core.camera.tracking.TrackedObjectBounds(
            left = (normTapX - half).coerceAtLeast(0f),
            top = (normTapY - half).coerceAtLeast(0f),
            right = (normTapX + half).coerceAtMost(1f),
            bottom = (normTapY + half).coerceAtMost(1f),
        )
        _trackedObjectState.value = com.webappypie.optilens.core.camera.tracking.TrackedObjectState(
            bounds = bounds,
            status = com.webappypie.optilens.core.camera.tracking.TrackingStatus.TRACKING,
            confidence = 0.95f,
        )
    }

    override fun stopObjectTracking() {
        _trackedObjectState.value = com.webappypie.optilens.core.camera.tracking.TrackedObjectState.INACTIVE
    }

    override suspend fun setCameraMode(mode: com.webappypie.optilens.core.camera.model.CameraMode): OptiResult<Unit> {
        _activeCameraMode.value = mode
        return OptiResult.Success(Unit)
    }

    fun emitLensDirtyState(state: com.webappypie.optilens.core.camera.analysis.LensDirtyState) {
        _lensDirtyState.value = state
    }

    fun emitMoonState(state: com.webappypie.optilens.core.camera.moon.MoonDetectionState) {
        _moonDetectionState.value = state
    }

    fun emitWildlifeState(state: com.webappypie.optilens.core.camera.wildlife.WildlifeDetectionState) {
        _wildlifeDetectionState.value = state
    }

    fun emitTrackedObjectState(state: com.webappypie.optilens.core.camera.tracking.TrackedObjectState) {
        _trackedObjectState.value = state
    }

    val fakeBurstEngine = com.webappypie.optilens.core.camera.burst.FakeBurstAcquisitionEngine()

    fun emitScene(scene: com.webappypie.optilens.core.camera.model.SceneClassification) { _sceneClassification.value = scene }
    fun emitQuality(quality: com.webappypie.optilens.core.camera.model.QualityMetrics) { _qualityMetrics.value = quality }
    fun emitMotion(motion: com.webappypie.optilens.core.camera.model.MotionState) { _motionState.value = motion }
    fun emitStrategy(strategy: com.webappypie.optilens.core.camera.strategy.CaptureStrategy) { _captureStrategy.value = strategy }
    fun emitFaces(faces: List<com.webappypie.optilens.core.camera.model.DetectedFace>) { _detectedFaces.value = faces }
    fun emitBurst(burst: com.webappypie.optilens.core.camera.burst.model.BurstResult?) { _lastBurstResult.value = burst }
    fun emitNightPlan(plan: com.webappypie.optilens.core.camera.night.NightExecutionPlan?) { _nightExecutionPlan.value = plan }
    fun emitPortraitPlan(plan: com.webappypie.optilens.core.camera.portrait.PortraitExecutionPlan?) { _portraitExecutionPlan.value = plan }
    fun emitStability(assessment: com.webappypie.optilens.core.camera.night.StabilityAssessment) { _stabilityAssessment.value = assessment }
    fun emitThermalState(state: com.webappypie.optilens.core.camera.thermal.DeviceThermalState) { _thermalState.value = state }
    fun emitFocusPeaking(data: com.webappypie.optilens.core.camera.model.FocusPeakingData) { _focusPeakingData.value = data }
    fun emitExposureZebra(data: com.webappypie.optilens.core.camera.model.ExposureZebraData) { _exposureZebraData.value = data }
    fun emitLensMetadata(metadata: com.webappypie.optilens.core.camera.model.LensMetadata) { _lensMetadata.value = metadata }

    private var _isFrontCamera = false
    override val isFrontCamera: Boolean get() = _isFrontCamera

    var isPreviewActive: Boolean = false
        private set

    var currentZoom: Float = 1.0f
        private set

    var isTorchEnabled: Boolean = false
        private set

    var isHistogramEnabled: Boolean = false
        private set

    var lastFocusedPoint: MeteringPoint? = null
        private set

    override suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ): OptiResult<Unit> {
        _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
        isPreviewActive = true
        return OptiResult.Success(Unit)
    }

    override suspend fun startPreview(): OptiResult<Unit> {
        _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
        isPreviewActive = true
        return OptiResult.Success(Unit)
    }

    override suspend fun focusAndMeter(meteringPoint: MeteringPoint): OptiResult<Unit> {
        lastFocusedPoint = meteringPoint
        return OptiResult.Success(Unit)
    }

    override suspend fun setExposureCompensation(index: Int): OptiResult<Unit> {
        _exposureState.value = _exposureState.value.copy(index = index)
        _proState.value = _proState.value.copy(evIndex = index)
        return OptiResult.Success(Unit)
    }

    override suspend fun setIso(iso: Int?): OptiResult<Unit> {
        _proState.value = _proState.value.copy(iso = iso)
        return OptiResult.Success(Unit)
    }

    override suspend fun setShutterSpeed(nanos: Long?): OptiResult<Unit> {
        _proState.value = _proState.value.copy(shutterSpeedNanos = nanos)
        return OptiResult.Success(Unit)
    }

    override suspend fun setFocusDistance(distanceDiopters: Float?): OptiResult<Unit> {
        _proState.value = _proState.value.copy(focusDistanceDiopters = distanceDiopters)
        return OptiResult.Success(Unit)
    }

    override suspend fun setWhiteBalance(mode: WhiteBalanceMode): OptiResult<Unit> {
        _proState.value = _proState.value.copy(whiteBalanceMode = mode)
        return OptiResult.Success(Unit)
    }

    override suspend fun resetProToAuto(): OptiResult<Unit> {
        _proState.value = _proState.value.copy(
            iso = null,
            shutterSpeedNanos = null,
            focusDistanceDiopters = null,
            whiteBalanceMode = WhiteBalanceMode.AUTO,
            evIndex = 0,
        )
        _exposureState.value = _exposureState.value.copy(index = 0)
        return OptiResult.Success(Unit)
    }

    override fun setHistogramEnabled(enabled: Boolean) {
        isHistogramEnabled = enabled
    }

    override suspend fun setHistogramMode(mode: com.webappypie.optilens.core.camera.model.HistogramMode): OptiResult<Unit> {
        _proState.value = _proState.value.copy(histogramMode = mode)
        return OptiResult.Success(Unit)
    }

    override suspend fun setRawCaptureEnabled(enabled: Boolean): OptiResult<Unit> {
        _proState.value = _proState.value.copy(isRawEnabled = enabled)
        return OptiResult.Success(Unit)
    }

    override suspend fun setRawCaptureFormat(format: com.webappypie.optilens.core.camera.model.RawCaptureFormat): OptiResult<Unit> {
        _proState.value = _proState.value.copy(rawFormat = format)
        return OptiResult.Success(Unit)
    }

    override suspend fun setSaveCompanionJpeg(saveCompanion: Boolean): OptiResult<Unit> {
        _proState.value = _proState.value.copy(saveCompanionJpeg = saveCompanion)
        return OptiResult.Success(Unit)
    }

    override suspend fun setFocusPeakingEnabled(enabled: Boolean): OptiResult<Unit> {
        _proState.value = _proState.value.copy(focusPeakingEnabled = enabled)
        _focusPeakingData.value = _focusPeakingData.value.copy(isEnabled = enabled)
        return OptiResult.Success(Unit)
    }

    override suspend fun setExposureZebraEnabled(enabled: Boolean): OptiResult<Unit> {
        _proState.value = _proState.value.copy(exposureZebraEnabled = enabled)
        _exposureZebraData.value = _exposureZebraData.value.copy(isEnabled = enabled)
        return OptiResult.Success(Unit)
    }

    override suspend fun setFlashMode(mode: FlashMode): OptiResult<Unit> {
        _flashMode.value = mode
        return OptiResult.Success(Unit)
    }

    override suspend fun enableTorch(enabled: Boolean): OptiResult<Unit> {
        isTorchEnabled = enabled
        return OptiResult.Success(Unit)
    }

    override suspend fun capturePhoto(targetRotation: Int, mirrorHorizontal: Boolean): OptiResult<CapturedPhoto> {
        _sessionState.value = CameraSessionState.CAPTURING
        val dummyUri = "content://media/external/images/media/fake_photo_${System.currentTimeMillis()}"
        val photo = CapturedPhoto(
            uri = dummyUri,
            contentUri = null,
            width = 4032,
            height = 3024,
            timestampMs = System.currentTimeMillis(),
            thumbnail = null,
            orientationDegrees = targetRotation,
            fileSizeBytes = 2_500_000L,
        )
        _lastCapturedPhoto.value = photo
        _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
        return OptiResult.Success(photo)
    }

    override suspend fun capturePhoto(): OptiResult<String> {
        return capturePhoto(0).map { it.uri }
    }

    override suspend fun acquireBurst(
        frameCount: Int?,
        evOffsets: List<Int>?,
        targetRotation: Int,
    ): OptiResult<com.webappypie.optilens.core.camera.burst.model.BurstResult> {
        _sessionState.value = CameraSessionState.CAPTURING
        val actualCount = frameCount ?: _captureStrategy.value.recommendedFrameCount
        val actualOffsets = evOffsets ?: _captureStrategy.value.exposureEvOffsets

        val result = fakeBurstEngine.acquireBurst(
            frameCount = actualCount,
            evOffsets = actualOffsets,
            mode = _captureStrategy.value.mode,
            targetRotation = targetRotation,
        )

        if (result is OptiResult.Success) {
            _lastBurstResult.value = result.data
        }

        _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
        return result
    }

    override suspend fun stopPreview() {
        _sessionState.value = CameraSessionState.IDLE
        isPreviewActive = false
    }

    override suspend fun setZoom(ratio: Float): OptiResult<Unit> {
        currentZoom = ratio
        _zoomState.value = _zoomState.value.copy(currentZoom = ratio)
        return OptiResult.Success(Unit)
    }

    override suspend fun flipCamera(): OptiResult<Unit> {
        _isFrontCamera = !_isFrontCamera
        return OptiResult.Success(Unit)
    }

    override suspend fun enablePreviewLowLightBoost(enable: Boolean): OptiResult<Boolean> {
        _isPreviewBoostActive.value = enable
        return OptiResult.Success(enable)
    }

    override suspend fun setNightPolicyPreference(preference: com.webappypie.optilens.core.camera.night.NightPolicyPreference) {
        // Mock preference storage
    }

    override suspend fun setPortraitAperture(aperture: com.webappypie.optilens.core.camera.portrait.PortraitAperture): OptiResult<Unit> {
        return OptiResult.Success(Unit)
    }

    override suspend fun setPortraitPolicyPreference(preference: com.webappypie.optilens.core.camera.portrait.PortraitPolicyPreference) {
        // Mock preference storage
    }

    override fun release() {
        stopPreviewSync()
    }

    private fun stopPreviewSync() {
        _sessionState.value = CameraSessionState.IDLE
        isPreviewActive = false
    }
}
