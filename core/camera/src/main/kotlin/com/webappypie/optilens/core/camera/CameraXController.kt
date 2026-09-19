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
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.camera.strategy.CaptureStrategy
import com.webappypie.optilens.core.camera.storage.MediaStoreSaver
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
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

    init {
        // Observe detected hardware capability profile to derive truthful zoom stops and Pro limits
        scope.launch {
            capabilityRepository.capabilityProfile.filterNotNull().collectLatest { profile ->
                val activeCamera = if (_isFrontCamera) profile.primaryFrontCamera else profile.primaryBackCamera
                updateHardwareProfileCapabilities(activeCamera)
            }
        }
    }

    private fun updateHardwareProfileCapabilities(cameraProfile: CameraDeviceProfile?) {
        if (cameraProfile == null) return

        // 1. Truthful zoom stops
        _zoomStops.value = ZoomStop.deriveFromProfile(cameraProfile)

        // 2. Pro bounds
        val streamCaps = cameraProfile.streamCapabilities
        val isoRange = if (streamCaps.isoRangeMin != null && streamCaps.isoRangeMax != null) {
            streamCaps.isoRangeMin..streamCaps.isoRangeMax
        } else null

        val shutterRange = if (streamCaps.exposureTimeRangeMinNs != null && streamCaps.exposureTimeRangeMaxNs != null) {
            streamCaps.exposureTimeRangeMinNs..streamCaps.exposureTimeRangeMaxNs
        } else null

        _proState.value = _proState.value.copy(
            isoRange = isoRange,
            isIsoManualSupported = streamCaps.supportsManualSensor && isoRange != null,
            shutterSpeedRangeNanos = shutterRange,
            isShutterManualSupported = streamCaps.supportsManualSensor && shutterRange != null,
            isFocusManualSupported = true,
            isWhiteBalanceSupported = true,
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
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(flashModeToCameraX(_flashMode.value))
                .build()
            imageCaptureUseCase = capture

            // 3. Start Gyro Motion Tracking
            gyroMotionTracker.start()

            // 4. Build Real-Time Intelligence Analyzer (Histogram, Metrics, Motion, Faces, Scene, Strategy)
            val analyzer = RealtimeIntelligenceAnalyzer(
                faceDetector = faceDetector,
                gyroVelocityProvider = { gyroMotionTracker.angularVelocity.value },
                analysisScope = scope,
                onHistogramComputed = { _histogramData.value = it },
                onQualityMetricsComputed = { _qualityMetrics.value = it },
                onMotionStateComputed = { _motionState.value = it },
                onSceneClassificationComputed = { _sceneClassification.value = it },
                onFacesDetected = { _detectedFaces.value = it },
                onStrategyDecided = { _captureStrategy.value = it },
            )
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
        applyCamera2CaptureOptions()
        OptiResult.Success(Unit)
    }

    override suspend fun setShutterSpeed(nanos: Long?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(shutterSpeedNanos = nanos)
        applyCamera2CaptureOptions()
        OptiResult.Success(Unit)
    }

    override suspend fun setFocusDistance(distanceDiopters: Float?): OptiResult<Unit> = withContext(dispatchers.main) {
        _proState.value = _proState.value.copy(focusDistanceDiopters = distanceDiopters)
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

    override suspend fun capturePhoto(targetRotation: Int): OptiResult<CapturedPhoto> {
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
                                // Task 15: Strict deterministic ImageProxy closure
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

            // Save via scoped MediaStore on Dispatchers.IO
            val saveResult = mediaStoreSaver.saveJpeg(
                jpegBytes = jpegBytes,
                orientationDegrees = rotationDegrees,
            )

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
        return capturePhoto(0).map { it.uri }
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

    override fun release() {
        try {
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

    companion object {
        private const val TAG = "CameraXController"
    }
}
