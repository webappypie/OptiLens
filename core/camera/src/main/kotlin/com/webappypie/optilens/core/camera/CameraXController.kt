package com.webappypie.optilens.core.camera

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.webappypie.optilens.core.camera.analysis.face.FaceDetector
import com.webappypie.optilens.core.camera.analysis.face.MlKitFaceDetector
import com.webappypie.optilens.core.camera.analysis.motion.GyroMotionTracker
import com.webappypie.optilens.core.camera.analyzer.RealtimeIntelligenceAnalyzer
import com.webappypie.optilens.core.camera.burst.BurstAcquisitionEngine
import com.webappypie.optilens.core.camera.burst.Camera2BurstAcquisitionEngine
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.discovery.CameraCapabilityRepository
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.HistogramData
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.ProCameraState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.night.NightExecutionPlan
import com.webappypie.optilens.core.camera.night.NightModePolicyEngine
import com.webappypie.optilens.core.camera.night.NightPolicyPreference
import com.webappypie.optilens.core.camera.night.NightStabilityDetector
import com.webappypie.optilens.core.camera.night.StabilityAssessment
import com.webappypie.optilens.core.camera.strategy.CaptureStrategy
import com.webappypie.optilens.core.camera.thermal.DeviceThermalMonitor
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.portrait.PortraitAperture
import com.webappypie.optilens.core.camera.portrait.PortraitExecutionPlan
import com.webappypie.optilens.core.camera.portrait.PortraitPolicyEngine
import com.webappypie.optilens.core.camera.portrait.PortraitPolicyPreference
import com.webappypie.optilens.core.camera.model.FocusPeakingData
import com.webappypie.optilens.core.camera.model.ExposureZebraData
import com.webappypie.optilens.core.camera.model.LensMetadata
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import com.webappypie.optilens.core.camera.model.HistogramMode
import com.webappypie.optilens.core.camera.storage.MediaStoreSaver
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import com.webappypie.optilens.core.common.result.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Production implementation of [CameraController] powered by CameraX 1.6+ and Camera2.
 *
 * Implements:
 * - Deterministic lifecycle-bound preview and still capture.
 * - Hardware-derived truthful zoom stops (optical vs digital crop).
 * - Live 64-bin luminance histogram via non-blocking [ImageAnalysis].
 * - Pro photography manual overrides (ISO, Shutter, Focus, WB, EV) via [Camera2CameraControl].
 * - Tap-to-focus and metering.
 * - Exposure compensation.
 * - Flash and torch modes.
 * - Orientation awareness and background MediaStore saving.
 * - Strict non-blocking I/O and deterministic [ImageProxy] closure.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
@Singleton
class CameraXController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityRepository: CameraCapabilityRepository,
    private val mediaStoreSaver: MediaStoreSaver,
    private val thermalMonitor: DeviceThermalMonitor,
    private val nightPolicyEngine: NightModePolicyEngine,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : CameraController {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    override val capabilities: Flow<CameraCapability> = capabilityRepository.legacyCapability

    private val _sessionState = MutableStateFlow(CameraSessionState.IDLE)
    override val sessionState: Flow<CameraSessionState> = _sessionState.asStateFlow()

    private val _zoomState = MutableStateFlow(ZoomState())
    override val zoomState: Flow<ZoomState> = _zoomState.asStateFlow()

    private val _zoomStops = MutableStateFlow(
        listOf(
            ZoomStop(ratio = 1.0f, label = "1x", isOptical = true),
        )
    )
    override val zoomStops: Flow<List<ZoomStop>> = _zoomStops.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashMode.AUTO)
    override val flashMode: Flow<FlashMode> = _flashMode.asStateFlow()

    private val _exposureState = MutableStateFlow(ExposureState())
    override val exposureState: Flow<ExposureState> = _exposureState.asStateFlow()

    private val _proState = MutableStateFlow(ProCameraState())
    override val proState: Flow<ProCameraState> = _proState.asStateFlow()

    private val _histogramData = MutableStateFlow(HistogramData.EMPTY)
    override val histogramData: Flow<HistogramData> = _histogramData.asStateFlow()

    private val _focusPeakingData = MutableStateFlow(FocusPeakingData.EMPTY)
    override val focusPeakingData: Flow<FocusPeakingData> = _focusPeakingData.asStateFlow()

    private val _exposureZebraData = MutableStateFlow(ExposureZebraData.EMPTY)
    override val exposureZebraData: Flow<ExposureZebraData> = _exposureZebraData.asStateFlow()

    private val _lensMetadata = MutableStateFlow(LensMetadata.EMPTY)
    override val lensMetadata: Flow<LensMetadata> = _lensMetadata.asStateFlow()

    private val _sceneClassification = MutableStateFlow(SceneClassification.DEFAULT)
    override val sceneClassification: Flow<SceneClassification> = _sceneClassification.asStateFlow()

    private val _qualityMetrics = MutableStateFlow(QualityMetrics.DEFAULT)
    override val qualityMetrics: Flow<QualityMetrics> = _qualityMetrics.asStateFlow()

    private val _motionState = MutableStateFlow(MotionState.DEFAULT)
    override val motionState: Flow<MotionState> = _motionState.asStateFlow()

    private val _captureStrategy = MutableStateFlow(CaptureStrategy.DEFAULT)
    override val captureStrategy: Flow<CaptureStrategy> = _captureStrategy.asStateFlow()

    private val _detectedFaces = MutableStateFlow<List<DetectedFace>>(emptyList())
    override val detectedFaces: Flow<List<DetectedFace>> = _detectedFaces.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    override val lastCapturedPhoto: Flow<CapturedPhoto?> = _lastCapturedPhoto.asStateFlow()

    @Volatile
    private var analysisPaused = false

    override fun setRealtimeAnalysisPaused(paused: Boolean) {
        analysisPaused = paused
        realtimeAnalyzer?.isEnabled = !paused
    }

    override suspend fun revalidateLastCapturedPhoto() {
        val photo = _lastCapturedPhoto.value ?: return
        val exists = withContext(dispatchers.io) { mediaExists(photo.contentUri ?: android.net.Uri.parse(photo.uri)) }
        // Only clear if nothing newer replaced it while we were checking.
        if (!exists) _lastCapturedPhoto.compareAndSet(photo, null)
    }

    private fun mediaExists(uri: android.net.Uri): Boolean = try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(android.provider.BaseColumns._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } else {
            java.io.File(uri.path ?: uri.toString()).exists()
        }
    } catch (_: SecurityException) {
        true // Cannot tell; keep the thumbnail rather than hide a photo that exists.
    } catch (_: Exception) {
        false
    }

    private val _lastBurstResult = MutableStateFlow<BurstResult?>(null)
    override val lastBurstResult: Flow<BurstResult?> = _lastBurstResult.asStateFlow()

    private val _nightExecutionPlan = MutableStateFlow<NightExecutionPlan?>(null)
    override val nightExecutionPlan: Flow<NightExecutionPlan?> = _nightExecutionPlan.asStateFlow()

    private val portraitPolicyEngine = PortraitPolicyEngine()
    private val _portraitExecutionPlan = MutableStateFlow<PortraitExecutionPlan?>(null)
    override val portraitExecutionPlan: Flow<PortraitExecutionPlan?> = _portraitExecutionPlan.asStateFlow()

    private var portraitPolicyPreference: PortraitPolicyPreference = PortraitPolicyPreference.AUTO
    private var portraitAperture: PortraitAperture = PortraitAperture.DEFAULT
    private var portraitSkinSmoothingStrength: Float = 0.25f

    private val _isPreviewBoostActive = MutableStateFlow(false)
    override val isPreviewBoostActive: Flow<Boolean> = _isPreviewBoostActive.asStateFlow()

    private val stabilityDetector = NightStabilityDetector()
    private val _stabilityAssessment = MutableStateFlow(stabilityDetector.evaluateStability(0.0f))
    override val stabilityAssessment: Flow<StabilityAssessment> = _stabilityAssessment.asStateFlow()

    override val thermalState: Flow<DeviceThermalState> = thermalMonitor.thermalState

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

    private var nightPolicyPreference: NightPolicyPreference = NightPolicyPreference.AUTO
    private var activeCameraProfile: CameraDeviceProfile? = null

    private var _isFrontCamera = false
    override val isFrontCamera: Boolean get() = _isFrontCamera

    private val gyroMotionTracker = GyroMotionTracker(context)
    private val faceDetector: FaceDetector = MlKitFaceDetector()
    private var realtimeAnalyzer: RealtimeIntelligenceAnalyzer? = null

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null

    private val isCapturing = AtomicBoolean(false)

    /** Dedicated single-thread background executor for capture callbacks (prevents UI blocking). */
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "optilens-camera-capture")
    }

    /** Dedicated background executor for image analysis (histogram). */
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "optilens-camera-analysis")
    }

    /** Multi-frame burst acquisition engine using bounded buffer pool and Camera2 interop. */
    private val burstEngine: BurstAcquisitionEngine by lazy {
        Camera2BurstAcquisitionEngine(
            imageCaptureProvider = { imageCaptureUseCase },
            cameraControlProvider = { camera?.cameraControl },
            gyroMotionTracker = gyroMotionTracker,
            captureExecutor = captureExecutor,
            dispatchers = dispatchers,
            logger = logger,
        )
    }

    init {
        // Observe detected hardware capability profile to derive truthful zoom stops and Pro limits
        scope.launch {
            capabilityRepository.capabilityProfile.filterNotNull().collectLatest { profile ->
                refreshActiveProfile(profile)
            }
        }

        // Observe thermal status transitions
        scope.launch {
            thermalMonitor.thermalState.collectLatest {
                recomputeNightPlan()
                recomputePortraitPlan()
            }
        }
    }

    private fun recomputeNightPlan() {
        val angularVel = gyroMotionTracker.angularVelocity.value
        val assessment = stabilityDetector.evaluateStability(angularVel)
        _stabilityAssessment.value = assessment

        val profile = activeCameraProfile
        val tier = capabilityRepository.capabilityProfile.value?.performanceTier ?: PerformanceTier.MID_RANGE
        val plan = nightPolicyEngine.evaluatePolicy(
            activeCameraProfile = profile,
            performanceTier = tier,
            stability = assessment,
            subjectMotion = _motionState.value.subjectMotionLevel,
            luminance = _qualityMetrics.value.luminance,
            thermalState = thermalMonitor.thermalState.value,
            thermalPolicy = thermalMonitor.policy.value,
            preference = nightPolicyPreference,
            isHighContrastOrNeon = _qualityMetrics.value.dynamicRangeScore > 65.0f,
        )
        _nightExecutionPlan.value = plan
    }

    private fun recomputePortraitPlan() {
        val profile = activeCameraProfile
        val tier = capabilityRepository.capabilityProfile.value?.performanceTier ?: PerformanceTier.MID_RANGE
        val plan = portraitPolicyEngine.evaluatePolicy(
            activeCameraProfile = profile,
            performanceTier = tier,
            detectedFaces = _detectedFaces.value,
            sceneLuminance = _qualityMetrics.value.luminance,
            aperture = portraitAperture,
            skinSmoothingStrength = portraitSkinSmoothingStrength,
            preference = portraitPolicyPreference,
        )
        _portraitExecutionPlan.value = plan
    }

    /** Selects the profile of the camera that is (or will be) bound and re-derives limits from it. */
    private fun refreshActiveProfile(
        profile: com.webappypie.optilens.core.camera.model.CameraCapabilityProfile? = capabilityRepository.capabilityProfile.value,
    ) {
        profile ?: return
        val activeCamera = if (_isFrontCamera) profile.primaryFrontCamera else profile.primaryBackCamera
        activeCameraProfile = activeCamera
        updateHardwareProfileCapabilities(activeCamera)
        recomputeNightPlan()
        recomputePortraitPlan()
    }

    /** Latest exposure chosen by auto-exposure on the preview stream (for shutter/ISO priority). */
    @Volatile
    private var meteredExposure: ExposurePriority.AutoExposure? = null

    private val meteringCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            // Only auto-exposed frames are a valid metering baseline.
            if (result.get(CaptureResult.CONTROL_AE_MODE) == CaptureResult.CONTROL_AE_MODE_OFF) return
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: return
            val time = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: return
            val boost = result.get(CaptureResult.CONTROL_POST_RAW_SENSITIVITY_BOOST) ?: 100
            meteredExposure = ExposurePriority.AutoExposure(iso, time, boost)
        }
    }

    private var captureAspectRatio = com.webappypie.optilens.core.camera.model.CaptureAspectRatio.RATIO_4_3

    /** Device range for CONTROL_POST_RAW_SENSITIVITY_BOOST of the bound camera, if supported. */
    private var postRawBoostRange: ClosedRange<Int>? = null

    private fun resolutionSelectorFor(aspect: com.webappypie.optilens.core.camera.model.CaptureAspectRatio): ResolutionSelector =
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(
                when (aspect) {
                    com.webappypie.optilens.core.camera.model.CaptureAspectRatio.RATIO_16_9 ->
                        AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY
                    com.webappypie.optilens.core.camera.model.CaptureAspectRatio.RATIO_4_3 ->
                        AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
                }
            )
            .build()

    override suspend fun setCaptureAspectRatio(
        aspect: com.webappypie.optilens.core.camera.model.CaptureAspectRatio,
    ): OptiResult<Unit> = withContext(dispatchers.main) {
        if (aspect == captureAspectRatio) return@withContext OptiResult.Success(Unit)
        captureAspectRatio = aspect
        if (currentLifecycleOwner != null && currentSurfaceProvider != null) startPreview() else OptiResult.Success(Unit)
    }

    private fun updateHardwareProfileCapabilities(cameraProfile: CameraDeviceProfile?) {
        if (cameraProfile == null) return

        // 1. Truthful zoom stops
        _zoomStops.value = ZoomStop.deriveFromProfile(cameraProfile)

        // 2. Pro bounds & optical lens metadata
        val streamCaps = cameraProfile.streamCapabilities
        val rawCaps = cameraProfile.rawCapabilities
        val controls = cameraProfile.controls

        val isoRange = if (streamCaps.isoRangeMin != null && streamCaps.isoRangeMax != null) {
            streamCaps.isoRangeMin..streamCaps.isoRangeMax
        } else null

        val shutterRange = if (streamCaps.exposureTimeRangeMinNs != null && streamCaps.exposureTimeRangeMaxNs != null) {
            streamCaps.exposureTimeRangeMinNs..streamCaps.exposureTimeRangeMaxNs
        } else null

        val isFocusManualSupported = streamCaps.supportsManualSensor && controls.minFocusDistanceDiopters > 0.0f

        // Derive 35mm equivalent focal length from sensor physical dimensions
        val sensorDiag = kotlin.math.sqrt(
            cameraProfile.sensorInfo.physicalWidthMm * cameraProfile.sensorInfo.physicalWidthMm +
            cameraProfile.sensorInfo.physicalHeightMm * cameraProfile.sensorInfo.physicalHeightMm
        )
        val cropFactor = if (sensorDiag > 0.1f) 43.27f / sensorDiag else 1.0f
        val primaryFocalLength = cameraProfile.focalLengthsMm.firstOrNull() ?: 0f
        val primary35mmEq = kotlin.math.round(primaryFocalLength * cropFactor).toInt()
        val primaryAperture = controls.apertures.firstOrNull() ?: 0f

        val initialLensMeta = LensMetadata(
            focalLengthMm = primaryFocalLength,
            focalLength35mmEquivalent = primary35mmEq,
            apertureFNumber = primaryAperture,
            minFocusDistanceDiopters = controls.minFocusDistanceDiopters,
            sensorWidthMm = cameraProfile.sensorInfo.physicalWidthMm,
            sensorHeightMm = cameraProfile.sensorInfo.physicalHeightMm,
            isFixedFocus = controls.minFocusDistanceDiopters <= 0.0f,
        )
        _lensMetadata.value = initialLensMeta

        _proState.value = _proState.value.copy(
            isoRange = isoRange,
            isIsoManualSupported = streamCaps.supportsManualSensor && isoRange != null,
            shutterSpeedRangeNanos = shutterRange,
            isShutterManualSupported = streamCaps.supportsManualSensor && shutterRange != null,
            isFocusManualSupported = isFocusManualSupported,
            isWhiteBalanceSupported = true,
            isRawSupported = rawCaps.supportsRawSensor,
            supportsRaw10 = rawCaps.supportsRaw10,
            supportsRaw12 = rawCaps.supportsRaw12,
            supportsRawPrivate = rawCaps.supportsRawPrivate,
            lensMetadata = initialLensMeta,
        )
    }

    override suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ): OptiResult<Unit> = withContext(dispatchers.main) {
        try {
            _sessionState.value = CameraSessionState.INITIALIZING
            currentLifecycleOwner = lifecycleOwner
            currentSurfaceProvider = surfaceProvider

            val provider = getCameraProvider()
            cameraProvider = provider

            val selector = if (_isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Unbind previous use cases safely before re-binding
            provider.unbindAll()

            // 1. Build Preview Use Case (with a metering callback for shutter/ISO priority)
            val previewBuilder = Preview.Builder().setResolutionSelector(resolutionSelectorFor(captureAspectRatio))
            Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(meteringCallback)
            val preview = previewBuilder.build()
            preview.setSurfaceProvider(surfaceProvider)
            previewUseCase = preview

            // 2. Build ImageCapture Use Case
            val captureBuilder = ImageCapture.Builder()
                .setResolutionSelector(resolutionSelectorFor(captureAspectRatio))
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashModeToCameraX(_flashMode.value))

            // Configure RAW output format if requested and supported
            if (_proState.value.isRawEnabled && _proState.value.isRawSupported) {
                try {
                    val rawFormat = if (_proState.value.saveCompanionJpeg) {
                        ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                    } else {
                        ImageCapture.OUTPUT_FORMAT_RAW
                    }
                    captureBuilder.setOutputFormat(rawFormat)
                } catch (e: Exception) {
                    logger.w(TAG, "Failed setting RAW output format on ImageCapture: ${e.message}")
                }
            }
            val capture = captureBuilder.build()
            imageCaptureUseCase = capture

            // 3. Start Gyro Motion Tracking
            gyroMotionTracker.start()

            // 4. Build Real-Time Intelligence Analyzer (Histogram, Metrics, Motion, Faces, Scene, Strategy, Assistance)
            val analyzer = RealtimeIntelligenceAnalyzer(
                faceDetector = faceDetector,
                gyroVelocityProvider = { gyroMotionTracker.angularVelocity.value },
                analysisScope = scope,
                onHistogramComputed = { _histogramData.value = it },
                onQualityMetricsComputed = {
                    _qualityMetrics.value = it
                    recomputeNightPlan()
                    recomputePortraitPlan()
                },
                onMotionStateComputed = {
                    _motionState.value = it
                    recomputeNightPlan()
                },
                onSceneClassificationComputed = { _sceneClassification.value = it },
                onFacesDetected = {
                    _detectedFaces.value = it
                    recomputePortraitPlan()
                },
                onStrategyDecided = { _captureStrategy.value = it },
                onFocusPeakingComputed = { _focusPeakingData.value = it },
                onExposureZebraComputed = { _exposureZebraData.value = it },
                onLensDirtyComputed = { _lensDirtyState.value = it },
                onMoonDetected = { _moonDetectionState.value = it },
                onWildlifeDetected = { _wildlifeDetectionState.value = it },
                onTrackedObjectUpdated = { _trackedObjectState.value = it },
                thermalPolicyProvider = { thermalMonitor.policy.value },
                currentZoomProvider = { _zoomState.value.currentZoom },
            )
            analyzer.isFocusPeakingActive = _proState.value.focusPeakingEnabled
            analyzer.isExposureZebraActive = _proState.value.exposureZebraEnabled
            analyzer.isEnabled = !analysisPaused
            realtimeAnalyzer = analyzer

            val analysis = ImageAnalysis.Builder()
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, analyzer)
            imageAnalysisUseCase = analysis

            // 5. Bind to Lifecycle
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                capture,
                analysis,
            )
            camera = boundCamera

            // Limits and zoom stops must describe the camera just bound (also after a flip),
            // and discovery must run at least once per install (it was previously only run
            // from the diagnostics screen, leaving zoom stops and Pro ranges at defaults).
            postRawBoostRange = try {
                androidx.camera.camera2.interop.Camera2CameraInfo.from(boundCamera.cameraInfo)
                    .getCameraCharacteristic(android.hardware.camera2.CameraCharacteristics.CONTROL_POST_RAW_SENSITIVITY_BOOST_RANGE)
                    ?.let { it.lower..it.upper }
            } catch (e: Exception) {
                null
            }
            refreshActiveProfile()
            scope.launch(dispatchers.io) {
                try {
                    capabilityRepository.getOrDiscoverCapabilities()
                } catch (e: Exception) {
                    logger.w(TAG, "Capability discovery failed: ${e.message}")
                }
            }

            // Re-apply preview low-light boost if active
            if (_isPreviewBoostActive.value && android.os.Build.VERSION.SDK_INT >= 35) {
                try {
                    val camera2Control = Camera2CameraControl.from(boundCamera.cameraControl)
                    val options = CaptureRequestOptions.Builder()
                        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, 6)
                        .build()
                    camera2Control.setCaptureRequestOptions(options)
                } catch (e: Exception) {
                    logger.w(TAG, "Failed to apply low light boost on bind: ${e.message}")
                }
            }

            // 5. Observe zoom and exposure bounds
            setupCameraStateObservers(boundCamera)

            // 6. Re-apply any active manual Pro parameters
            applyCamera2CaptureOptions()

            _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
            logger.i(TAG, "Camera preview successfully bound to lifecycle.")
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed binding camera preview: ${e.message}", e)
            _sessionState.value = CameraSessionState.ERROR
            OptiResult.Error(OptiError.CameraUnavailable(reason = e.message ?: "Failed to bind camera preview", cause = e))
        }
    }

    override suspend fun startPreview(): OptiResult<Unit> {
        val owner = currentLifecycleOwner
        val provider = currentSurfaceProvider
        return if (owner != null && provider != null) {
            bindPreview(owner, provider)
        } else {
            OptiResult.Error(OptiError.CameraUnavailable(reason = "Preview not yet initialized with lifecycle and surface"))
        }
    }

    override suspend fun focusAndMeter(meteringPoint: MeteringPoint): OptiResult<Unit> = withContext(dispatchers.main) {
        val control = camera?.cameraControl ?: return@withContext OptiResult.Error(
            OptiError.CameraUnavailable("Camera not active")
        )

        try {
            val action = FocusMeteringAction.Builder(meteringPoint, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(4, TimeUnit.SECONDS)
                .build()

            control.startFocusAndMetering(action)
            logger.d(TAG, "Focus and metering triggered")
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.w(TAG, "Focus and metering failed: ${e.message}")
            OptiResult.Error(OptiError.Unknown(cause = e, message = "Tap-to-focus failed"))
        }
    }

    override suspend fun setExposureCompensation(index: Int): OptiResult<Unit> = withContext(dispatchers.main) {
        val control = camera?.cameraControl ?: return@withContext OptiResult.Error(
            OptiError.CameraUnavailable("Camera not active")
        )

        try {
            control.setExposureCompensationIndex(index)
            _exposureState.value = _exposureState.value.copy(index = index)
            _proState.value = _proState.value.copy(evIndex = index)
            // With AE off, EV compensation only takes effect through the derived manual pair.
            if (_proState.value.isAnyManualActive) awaitOptionsApplied(applyCamera2CaptureOptions())
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.w(TAG, "Failed setting exposure compensation: ${e.message}")
            OptiResult.Error(OptiError.Unknown(cause = e, message = "Exposure adjustment failed"))
        }
    }

    override suspend fun setIso(iso: Int?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(iso = iso)
        _lensMetadata.value = _lensMetadata.value.copy(currentIso = iso)
        awaitOptionsApplied(applyCamera2CaptureOptions())
        OptiResult.Success(Unit)
    }

    override suspend fun setShutterSpeed(nanos: Long?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(shutterSpeedNanos = nanos)
        _lensMetadata.value = _lensMetadata.value.copy(currentShutterSpeedNanos = nanos)
        awaitOptionsApplied(applyCamera2CaptureOptions())
        OptiResult.Success(Unit)
    }

    override suspend fun setFocusDistance(distanceDiopters: Float?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(focusDistanceDiopters = distanceDiopters)
        _lensMetadata.value = _lensMetadata.value.copy(currentFocusDistanceDiopters = distanceDiopters)
        awaitOptionsApplied(applyCamera2CaptureOptions())
        OptiResult.Success(Unit)
    }

    override suspend fun setWhiteBalance(mode: WhiteBalanceMode): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(whiteBalanceMode = mode)
        awaitOptionsApplied(applyCamera2CaptureOptions())
        OptiResult.Success(Unit)
    }

    override suspend fun resetProToAuto(): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(
            iso = null,
            shutterSpeedNanos = null,
            focusDistanceDiopters = null,
            whiteBalanceMode = WhiteBalanceMode.AUTO,
            evIndex = 0,
        )
        _lensMetadata.value = _lensMetadata.value.copy(
            currentIso = null,
            currentShutterSpeedNanos = null,
            currentFocusDistanceDiopters = null,
        )
        setExposureCompensation(0)
        val cam = camera
        if (cam != null) {
            try {
                Camera2CameraControl.from(cam.cameraControl).clearCaptureRequestOptions()
            } catch (e: Exception) {
                logger.w(TAG, "Error clearing Camera2 options: ${e.message}")
            }
        }
        OptiResult.Success(Unit)
    }

    override fun setHistogramEnabled(enabled: Boolean) {
        realtimeAnalyzer?.isEnabled = enabled
    }

    override suspend fun setHistogramMode(mode: HistogramMode): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(histogramMode = mode)
        OptiResult.Success(Unit)
    }

    override suspend fun setRawCaptureEnabled(enabled: Boolean): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(isRawEnabled = enabled)
        reconfigureImageCaptureIfNeeded()
        OptiResult.Success(Unit)
    }

    override suspend fun setRawCaptureFormat(format: RawCaptureFormat): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(rawFormat = format)
        OptiResult.Success(Unit)
    }

    override suspend fun setSaveCompanionJpeg(saveCompanion: Boolean): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(saveCompanionJpeg = saveCompanion)
        reconfigureImageCaptureIfNeeded()
        OptiResult.Success(Unit)
    }

    override suspend fun setFocusPeakingEnabled(enabled: Boolean): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(focusPeakingEnabled = enabled)
        realtimeAnalyzer?.isFocusPeakingActive = enabled
        if (!enabled) _focusPeakingData.value = FocusPeakingData.EMPTY
        OptiResult.Success(Unit)
    }

    override suspend fun setExposureZebraEnabled(enabled: Boolean): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(exposureZebraEnabled = enabled)
        realtimeAnalyzer?.isExposureZebraActive = enabled
        if (!enabled) _exposureZebraData.value = ExposureZebraData.EMPTY
        OptiResult.Success(Unit)
    }

    private suspend fun reconfigureImageCaptureIfNeeded() {
        val owner = currentLifecycleOwner ?: return
        val provider = currentSurfaceProvider ?: return
        if (_sessionState.value == CameraSessionState.PREVIEW_ACTIVE) {
            bindPreview(owner, provider)
        }
    }

    /** Complete manual exposure for [pro], or null when AE should stay on. */
    private fun resolveManualExposure(pro: ProCameraState): ExposurePriority.ManualExposure? {
        val iso = pro.iso
        val time = pro.shutterSpeedNanos
        if (iso != null && time != null) return ExposurePriority.ManualExposure(iso, time, postRawBoost = 100)
        if (iso == null && time == null) return null
        val metered = meteredExposure?.takeIf { it.isValid } ?: return null
        val ev = ExposurePriority.evScale(pro.evIndex, pro.evStep)
        return if (time != null) {
            // Pro mode honours the user's shutter exactly; scene modes (Pet/Moon/Wildlife) cap
            // total gain at 4x the analog maximum and lengthen the shutter instead.
            val gainCap = if (_activeCameraMode.value == com.webappypie.optilens.core.camera.model.CameraMode.PRO) {
                null
            } else {
                pro.isoRange?.endInclusive?.let { it * SCENE_MODE_MAX_BOOST_FACTOR }
            }
            val manual = ExposurePriority.forShutter(
                metered, time, pro.isoRange, postRawBoostRange, ev,
                maxTotalGain = gainCap,
                slowestShutterNs = if (gainCap != null) SCENE_MODE_SLOWEST_SHUTTER_NS else null,
            )
            // Too dark for a faster-than-handheld shutter: manual exposure would only bypass the
            // HAL's low-light processing (device-verified: noisier, tinted, no motion benefit),
            // so scene modes keep auto exposure ("light permitting").
            if (gainCap != null && manual.exposureTimeNs >= SCENE_MODE_SLOWEST_SHUTTER_NS) {
                logger.d(TAG, "Scene-mode fast shutter skipped: insufficient light")
                null
            } else {
                manual
            }
        } else {
            ExposurePriority.forIso(metered, iso!!, pro.shutterSpeedRangeNanos, ev)
        }
    }

    /**
     * Suspends until CameraX reports the new capture-request options are in effect on the
     * repeating request (bounded). Capturing before that can produce a frame that was exposed
     * with the old/transitional settings (observed: near-black 1/250 s Pet shots).
     */
    private suspend fun awaitOptionsApplied(future: com.google.common.util.concurrent.ListenableFuture<*>?) {
        future ?: return
        withTimeoutOrNull(OPTIONS_APPLY_TIMEOUT_MS) {
            suspendCancellableCoroutine<Unit> { cont ->
                future.addListener({ if (cont.isActive) cont.resume(Unit) }, ContextCompat.getMainExecutor(context))
            }
        } ?: logger.w(TAG, "Capture options not confirmed within ${OPTIONS_APPLY_TIMEOUT_MS}ms")
    }

    private fun applyCamera2CaptureOptions(): com.google.common.util.concurrent.ListenableFuture<*>? {
        val cam = camera ?: return null
        val pro = _proState.value
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)

        if (!pro.isAnyManualActive) {
            return camera2Control.clearCaptureRequestOptions()
        }

        val builder = CaptureRequestOptions.Builder()

        // 1. Manual ISO / Shutter. Camera2 needs BOTH with AE off; when only one is set, derive
        // the other from the last auto-exposure metering (shutter / ISO priority).
        val manual = resolveManualExposure(pro)
        if (manual == null && pro.focusDistanceDiopters == null && pro.whiteBalanceMode == WhiteBalanceMode.AUTO) {
            // Nothing manual will actually be applied: clear interop options so the capture uses
            // CameraX/HAL defaults, identical to Photo mode (overriding them changed the HAL's
            // still-capture tuning on device).
            return camera2Control.clearCaptureRequestOptions()
        }
        if (manual != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, manual.iso)
            builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, manual.exposureTimeNs)
            if (postRawBoostRange != null) {
                builder.setCaptureRequestOption(CaptureRequest.CONTROL_POST_RAW_SENSITIVITY_BOOST, manual.postRawBoost)
            }
        } else {
            if (pro.iso != null || pro.shutterSpeedNanos != null) {
                logger.w(TAG, "No auto-exposure metering yet; keeping AE on instead of an undefined manual pair")
            }
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        }

        // 2. Manual Focus Distance
        if (pro.focusDistanceDiopters != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, pro.focusDistanceDiopters)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        }

        // 3. White Balance
        if (pro.whiteBalanceMode != WhiteBalanceMode.AUTO) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, pro.whiteBalanceMode.camera2AwbMode)
        } else {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
        }

        return camera2Control.setCaptureRequestOptions(builder.build())
    }

    override suspend fun setFlashMode(mode: FlashMode): OptiResult<Unit> = withContext(dispatchers.main) {
        _flashMode.value = mode
        imageCaptureUseCase?.flashMode = flashModeToCameraX(mode)
        if (mode == FlashMode.TORCH) {
            camera?.cameraControl?.enableTorch(true)
        } else {
            camera?.cameraControl?.enableTorch(false)
        }
        OptiResult.Success(Unit)
    }

    override suspend fun enableTorch(enabled: Boolean): OptiResult<Unit> = withContext(dispatchers.main) {
        try {
            camera?.cameraControl?.enableTorch(enabled)
            if (enabled) {
                _flashMode.value = FlashMode.TORCH
            } else if (_flashMode.value == FlashMode.TORCH) {
                _flashMode.value = FlashMode.OFF
            }
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.w(TAG, "Failed to toggle torch: ${e.message}")
            OptiResult.Error(OptiError.Unknown(cause = e, message = "Torch toggle failed"))
        }
    }

    override suspend fun capturePhoto(targetRotation: Int, mirrorHorizontal: Boolean): OptiResult<CapturedPhoto> {
        val capture = imageCaptureUseCase ?: return OptiResult.Error(
            OptiError.CameraUnavailable("Camera is not active or preview is uninitialized")
        )

        // Backpressure guard: prevent overlapping captures
        if (!isCapturing.compareAndSet(false, true)) {
            logger.w(TAG, "Capture dropped: previous capture still in progress.")
            return OptiResult.Error(OptiError.ProcessingFailed("Capture in progress", null))
        }

        _sessionState.value = CameraSessionState.CAPTURING

        return try {
            capture.targetRotation = targetRotation

            val isRawCapture = _proState.value.isRawEnabled && _proState.value.isRawSupported
            val rawFormat = _proState.value.rawFormat

            val saveResult = if (isRawCapture && capture.outputFormat == ImageCapture.OUTPUT_FORMAT_RAW_JPEG) {
                val timestamp = System.currentTimeMillis()
                val timeString = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestamp))
                val rawFile = File(context.cacheDir, "OptiLens_$timeString.${rawFormat.extension}")
                val jpegFile = File(context.cacheDir, "OptiLens_$timeString.jpg")

                val rawOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()
                val jpegOptions = ImageCapture.OutputFileOptions.Builder(jpegFile).build()

                suspendCancellableCoroutine<OptiResult<CapturedPhoto>> { continuation ->
                    capture.takePicture(
                        rawOptions,
                        jpegOptions,
                        captureExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                scope.launch(dispatchers.io) {
                                    val rBytes = if (rawFile.exists()) rawFile.readBytes() else ByteArray(0)
                                    val jBytes = if (jpegFile.exists()) jpegFile.readBytes() else ByteArray(0)
                                    rawFile.delete()
                                    jpegFile.delete()

                                    // targetRotation is a Surface.ROTATION_* constant, not degrees;
                                    // CameraX already encoded the true orientation in the JPEG EXIF.
                                    val saved = mediaStoreSaver.saveRawWithCompanionJpeg(
                                        rawBytes = rBytes,
                                        jpegBytes = jBytes,
                                        orientationDegrees = mediaStoreSaver.readExifRotationDegrees(jBytes),
                                        rawFormat = rawFormat,
                                        mirrorHorizontal = mirrorHorizontal,
                                    )
                                    continuation.resume(saved)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                rawFile.delete()
                                jpegFile.delete()
                                logger.e(TAG, "RAW+JPEG capture failed: ${exception.message}", exception)
                                continuation.resume(OptiResult.Error(OptiError.ProcessingFailed("RAW+JPEG capture failed", exception)))
                            }
                        }
                    )
                }
            } else if (isRawCapture && capture.outputFormat == ImageCapture.OUTPUT_FORMAT_RAW) {
                val timestamp = System.currentTimeMillis()
                val timeString = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestamp))
                val rawFile = File(context.cacheDir, "OptiLens_$timeString.${rawFormat.extension}")
                val rawOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()

                suspendCancellableCoroutine<OptiResult<CapturedPhoto>> { continuation ->
                    capture.takePicture(
                        rawOptions,
                        captureExecutor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                scope.launch(dispatchers.io) {
                                    val rBytes = if (rawFile.exists()) rawFile.readBytes() else ByteArray(0)
                                    rawFile.delete()
                                    val saved = mediaStoreSaver.saveDng(
                                        dngBytes = rBytes,
                                        orientationDegrees = mediaStoreSaver.readExifRotationDegrees(rBytes),
                                        rawFormat = rawFormat,
                                    )
                                    continuation.resume(saved)
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                rawFile.delete()
                                logger.e(TAG, "RAW capture failed: ${exception.message}", exception)
                                continuation.resume(OptiResult.Error(OptiError.ProcessingFailed("RAW capture failed", exception)))
                            }
                        }
                    )
                }
            } else {
                // Standard in-memory JPEG capture
                val rawBytesResult = suspendCancellableCoroutine<Pair<ByteArray, Int>> { continuation ->
                    // Safety: if coroutine is cancelled, ensure the capture guard is released
                    continuation.invokeOnCancellation { isCapturing.set(false) }
                    capture.takePicture(
                        captureExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                try {
                                    val rotation = image.imageInfo.rotationDegrees
                                    val buffer = image.planes[0].buffer
                                    val bytes = ByteArray(buffer.remaining())
                                    buffer.get(bytes)
                                    continuation.resume(bytes to rotation)
                                } catch (e: Exception) {
                                    continuation.resumeWith(Result.failure(e))
                                } finally {
                                    image.close()
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                logger.e(TAG, "CameraX image capture failed: ${exception.message}", exception)
                                continuation.resumeWith(Result.failure(exception))
                            }
                        }
                    )
                }

                val (jpegBytes, rotationDegrees) = rawBytesResult

                mediaStoreSaver.saveJpeg(
                    jpegBytes = jpegBytes,
                    orientationDegrees = rotationDegrees,
                    mirrorHorizontal = mirrorHorizontal,
                )
            }

            // Update captured photo BEFORE releasing isCapturing so UI gets photo at same recomposition
            if (saveResult is OptiResult.Success) {
                _lastCapturedPhoto.value = saveResult.data
            }

            saveResult
        } catch (e: Exception) {
            logger.e(TAG, "Capture execution failed: ${e.message}", e)
            OptiResult.Error(OptiError.ProcessingFailed(stage = "Capture execution", cause = e))
        } finally {
            _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
            isCapturing.set(false)
        }
    }

    override suspend fun capturePhoto(): OptiResult<String> {
        return capturePhoto(0, false).map { it.uri }
    }

    override suspend fun saveBurstFrame(
        packet: com.webappypie.optilens.core.camera.burst.model.FramePacket,
        mirrorHorizontal: Boolean,
    ): OptiResult<CapturedPhoto> {
        if (packet.isClosed) {
            return OptiResult.Error(OptiError.ProcessingFailed("Burst frame already released", null))
        }
        if (packet.format != android.graphics.ImageFormat.JPEG) {
            return OptiResult.Error(OptiError.ProcessingFailed("Unsupported burst frame format ${packet.format}", null))
        }
        val jpegBytes = packet.buffer.data.copyOf(packet.buffer.length)
        val saved = mediaStoreSaver.saveJpeg(
            jpegBytes = jpegBytes,
            orientationDegrees = packet.metadata.orientationDegrees,
            expectedWidth = packet.width,
            expectedHeight = packet.height,
            mirrorHorizontal = mirrorHorizontal,
        )
        if (saved is OptiResult.Success) {
            _lastCapturedPhoto.value = saved.data
        }
        return saved
    }

    override suspend fun saveProcessedJpeg(
        jpegBytes: ByteArray,
        orientationDegrees: Int,
        width: Int,
        height: Int,
        mirrorHorizontal: Boolean,
    ): OptiResult<CapturedPhoto> {
        val saved = mediaStoreSaver.saveJpeg(
            jpegBytes = jpegBytes,
            orientationDegrees = orientationDegrees,
            expectedWidth = width,
            expectedHeight = height,
            mirrorHorizontal = mirrorHorizontal,
        )
        if (saved is OptiResult.Success) {
            _lastCapturedPhoto.value = saved.data
        }
        return saved
    }

    override suspend fun acquireBurst(
        frameCount: Int?,
        evOffsets: List<Int>?,
        targetRotation: Int,
    ): OptiResult<BurstResult> = withContext(dispatchers.io) {
        if (!isCapturing.compareAndSet(false, true)) {
            return@withContext OptiResult.Error(OptiError.CameraUnavailable("Capture already in progress"))
        }

        try {
            _sessionState.value = CameraSessionState.CAPTURING

            val currentStrategy = _captureStrategy.value
            val actualCount = frameCount ?: currentStrategy.recommendedFrameCount.coerceAtLeast(1)
            val actualEvOffsets = evOffsets ?: currentStrategy.exposureEvOffsets

            val result = burstEngine.acquireBurst(
                frameCount = actualCount,
                evOffsets = actualEvOffsets,
                mode = currentStrategy.mode,
                targetRotation = targetRotation,
            )

            if (result is OptiResult.Success) {
                _lastBurstResult.value?.close()
                _lastBurstResult.value = result.data
            }
            result
        } catch (e: Exception) {
            logger.e(TAG, "Burst acquisition failed: ${e.message}", e)
            OptiResult.Error(OptiError.ProcessingFailed(stage = "Burst acquisition", cause = e))
        } finally {
            isCapturing.set(false)
            _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
        }
    }

    override suspend fun stopPreview() = withContext(dispatchers.main) {
        try {
            gyroMotionTracker.stop()
            realtimeAnalyzer?.reset()
            cameraProvider?.unbindAll()
            camera = null
            previewUseCase = null
            imageCaptureUseCase = null
            imageAnalysisUseCase = null
            _sessionState.value = CameraSessionState.IDLE
            logger.i(TAG, "Camera preview stopped and resources released.")
        } catch (e: Exception) {
            logger.w(TAG, "Error stopping preview: ${e.message}")
        }
    }

    override suspend fun setZoom(ratio: Float): OptiResult<Unit> = withContext(dispatchers.main) {
        val control = camera?.cameraControl ?: return@withContext OptiResult.Error(
            OptiError.CameraUnavailable("Camera not active")
        )
        try {
            val clamped = ratio.coerceIn(_zoomState.value.minZoom, _zoomState.value.maxZoom)
            control.setZoomRatio(clamped)
            _zoomState.value = _zoomState.value.copy(currentZoom = clamped)
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.w(TAG, "Failed setting zoom ratio: ${e.message}")
            OptiResult.Error(OptiError.Unknown(cause = e, message = "Zoom ratio update failed"))
        }
    }

    override suspend fun flipCamera(): OptiResult<Unit> = withContext(dispatchers.main) {
        _isFrontCamera = !_isFrontCamera
        logger.d(TAG, "Flipping camera to front = $_isFrontCamera")
        startPreview()
    }

    override suspend fun enablePreviewLowLightBoost(enable: Boolean): OptiResult<Boolean> = withContext(dispatchers.main) {
        _isPreviewBoostActive.value = enable
        val cam = camera ?: return@withContext OptiResult.Success(enable)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                val camera2Control = Camera2CameraControl.from(cam.cameraControl)
                val options = CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_MODE,
                        if (enable) 6 else CaptureRequest.CONTROL_AE_MODE_ON
                    )
                    .build()
                camera2Control.setCaptureRequestOptions(options)
            }
            logger.i(TAG, "Preview low light boost toggled: $enable")
            OptiResult.Success(enable)
        } catch (e: Exception) {
            logger.w(TAG, "Low light boost toggle exception: ${e.message}")
            OptiResult.Success(enable)
        }
    }

    override suspend fun setNightPolicyPreference(preference: NightPolicyPreference) {
        nightPolicyPreference = preference
        recomputeNightPlan()
    }

    override suspend fun setPortraitAperture(aperture: PortraitAperture): OptiResult<Unit> {
        portraitAperture = aperture
        recomputePortraitPlan()
        return OptiResult.Success(Unit)
    }

    override suspend fun setPortraitPolicyPreference(preference: PortraitPolicyPreference) {
        portraitPolicyPreference = preference
        recomputePortraitPlan()
    }

    private var activeZoomObserver: Observer<androidx.camera.core.ZoomState>? = null
    private var activeCameraInfo: CameraInfo? = null

    override fun release() {
        try {
            activeZoomObserver?.let { obs ->
                activeCameraInfo?.zoomState?.removeObserver(obs)
            }
            activeZoomObserver = null
            activeCameraInfo = null
            thermalMonitor.stopMonitoring()
            _lastBurstResult.value?.close()
            _lastBurstResult.value = null
            gyroMotionTracker.stop()
            faceDetector.release()
            cameraProvider?.unbindAll()
            camera = null
            captureExecutor.shutdown()
            analysisExecutor.shutdown()
            _sessionState.value = CameraSessionState.IDLE
            logger.d(TAG, "CameraXController fully released.")
        } catch (e: Exception) {
            logger.w(TAG, "Error releasing CameraXController: ${e.message}")
        }
    }

    override fun prewarm() {
        scope.launch {
            try {
                getCameraProvider()
            } catch (e: Exception) {
                logger.w(TAG, "CameraX prewarm failed: ${e.message}")
            }
        }
    }

    override fun unbind() {
        try {
            activeZoomObserver?.let { obs ->
                activeCameraInfo?.zoomState?.removeObserver(obs)
            }
            activeZoomObserver = null
            activeCameraInfo = null
            gyroMotionTracker.stop()
            realtimeAnalyzer?.reset()
            cameraProvider?.unbindAll()
            camera = null
            previewUseCase = null
            imageCaptureUseCase = null
            imageAnalysisUseCase = null
            _sessionState.value = CameraSessionState.IDLE
            logger.d(TAG, "CameraXController unbound (lightweight — executors preserved).")
        } catch (e: Exception) {
            logger.w(TAG, "Error unbinding CameraXController: ${e.message}")
        }
    }

    private suspend fun getCameraProvider(): ProcessCameraProvider = withContext(dispatchers.io) {
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWith(Result.failure(e))
                    }
                },
                ContextCompat.getMainExecutor(context)
            )
        }
    }

    private fun setupCameraStateObservers(cam: Camera) {
        // Clean up any existing observer on previous camera to prevent observer leaks across rebinds
        activeZoomObserver?.let { obs ->
            activeCameraInfo?.zoomState?.removeObserver(obs)
        }

        val info = cam.cameraInfo
        activeCameraInfo = info
        val observer = Observer<androidx.camera.core.ZoomState> { zoom ->
            if (zoom != null) {
                _zoomState.value = ZoomState(
                    currentZoom = zoom.zoomRatio,
                    minZoom = zoom.minZoomRatio,
                    maxZoom = zoom.maxZoomRatio,
                    linearZoom = zoom.linearZoom,
                )
            }
        }
        activeZoomObserver = observer
        info.zoomState.observeForever(observer)

        val exp = info.exposureState
        _exposureState.value = ExposureState(
            index = exp.exposureCompensationIndex,
            minIndex = exp.exposureCompensationRange.lower,
            maxIndex = exp.exposureCompensationRange.upper,
            step = exp.exposureCompensationStep.toFloat(),
        )

        _proState.value = _proState.value.copy(
            evRange = exp.exposureCompensationRange.lower..exp.exposureCompensationRange.upper,
            evStep = exp.exposureCompensationStep.toFloat(),
            evIndex = exp.exposureCompensationIndex,
        )
    }

    private fun flashModeToCameraX(mode: FlashMode): Int = when (mode) {
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.ON, FlashMode.TORCH -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    }

    override fun dismissLensDirtyPrompt() {
        realtimeAnalyzer?.dismissLensDirtyPrompt()
        _lensDirtyState.value = _lensDirtyState.value.copy(isDismissed = true)
    }

    override fun startObjectTracking(normTapX: Float, normTapY: Float) {
        realtimeAnalyzer?.startObjectTracking(normTapX, normTapY)
    }

    override fun stopObjectTracking() {
        realtimeAnalyzer?.stopObjectTracking()
    }

    override suspend fun setCameraMode(mode: com.webappypie.optilens.core.camera.model.CameraMode): OptiResult<Unit> = withContext(dispatchers.main) {
        _activeCameraMode.value = mode
        OptiResult.Success(Unit)
    }

    companion object {
        private const val TAG = "CameraXController"
        private const val OPTIONS_APPLY_TIMEOUT_MS = 1_000L
        private const val SCENE_MODE_MAX_BOOST_FACTOR = 4.0
        private const val SCENE_MODE_SLOWEST_SHUTTER_NS = 16_666_667L // 1/60 s
    }
}
