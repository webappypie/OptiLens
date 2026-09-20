package com.webappypie.optilens.core.camera

import android.content.Context
import android.hardware.camera2.CaptureRequest
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
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
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
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
                val activeCamera = if (_isFrontCamera) profile.primaryFrontCamera else profile.primaryBackCamera
                activeCameraProfile = activeCamera
                updateHardwareProfileCapabilities(activeCamera)
                recomputeNightPlan()
                recomputePortraitPlan()
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

            // 1. Build Preview Use Case
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(surfaceProvider)
            previewUseCase = preview

            // 2. Build ImageCapture Use Case
            val captureBuilder = ImageCapture.Builder()
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
            logger.d(TAG, "Focus and metering triggered at (${meteringPoint.x}, ${meteringPoint.y})")
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
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.w(TAG, "Failed setting exposure compensation: ${e.message}")
            OptiResult.Error(OptiError.Unknown(cause = e, message = "Exposure adjustment failed"))
        }
    }

    override suspend fun setIso(iso: Int?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(iso = iso)
        _lensMetadata.value = _lensMetadata.value.copy(currentIso = iso)
        applyCamera2CaptureOptions()
        OptiResult.Success(Unit)
    }

    override suspend fun setShutterSpeed(nanos: Long?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(shutterSpeedNanos = nanos)
        _lensMetadata.value = _lensMetadata.value.copy(currentShutterSpeedNanos = nanos)
        applyCamera2CaptureOptions()
        OptiResult.Success(Unit)
    }

    override suspend fun setFocusDistance(distanceDiopters: Float?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(focusDistanceDiopters = distanceDiopters)
        _lensMetadata.value = _lensMetadata.value.copy(currentFocusDistanceDiopters = distanceDiopters)
        applyCamera2CaptureOptions()
        OptiResult.Success(Unit)
    }

    override suspend fun setWhiteBalance(mode: WhiteBalanceMode): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(whiteBalanceMode = mode)
        applyCamera2CaptureOptions()
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

    private fun applyCamera2CaptureOptions() {
        val cam = camera ?: return
        val pro = _proState.value
        val camera2Control = Camera2CameraControl.from(cam.cameraControl)

        if (!pro.isAnyManualActive) {
            camera2Control.clearCaptureRequestOptions()
            return
        }

        val builder = CaptureRequestOptions.Builder()

        // 1. Manual ISO / Shutter
        if (pro.iso != null || pro.shutterSpeedNanos != null) {
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF)
            pro.iso?.let { builder.setCaptureRequestOption(CaptureRequest.SENSOR_SENSITIVITY, it) }
            pro.shutterSpeedNanos?.let { builder.setCaptureRequestOption(CaptureRequest.SENSOR_EXPOSURE_TIME, it) }
        } else {
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

        camera2Control.setCaptureRequestOptions(builder.build())
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

                                    val saved = mediaStoreSaver.saveRawWithCompanionJpeg(
                                        rawBytes = rBytes,
                                        jpegBytes = jBytes,
                                        orientationDegrees = targetRotation,
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
                                        orientationDegrees = targetRotation,
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

            if (saveResult is OptiResult.Success) {
                _lastCapturedPhoto.value = saveResult.data
            }

            saveResult
        } catch (e: Exception) {
            logger.e(TAG, "Capture execution failed: ${e.message}", e)
            OptiResult.Error(OptiError.ProcessingFailed(stage = "Capture execution", cause = e))
        } finally {
            isCapturing.set(false)
            _sessionState.value = CameraSessionState.PREVIEW_ACTIVE
        }
    }

    override suspend fun capturePhoto(): OptiResult<String> {
        return capturePhoto(0, false).map { it.uri }
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

    override fun release() {
        try {
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
        val info = cam.cameraInfo
        info.zoomState.observeForever { zoom ->
            if (zoom != null) {
                _zoomState.value = ZoomState(
                    currentZoom = zoom.zoomRatio,
                    minZoom = zoom.minZoomRatio,
                    maxZoom = zoom.maxZoomRatio,
                    linearZoom = zoom.linearZoom,
                )
            }
        }

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
    }
}
