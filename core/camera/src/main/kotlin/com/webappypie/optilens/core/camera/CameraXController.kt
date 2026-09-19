package com.webappypie.optilens.core.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.webappypie.optilens.core.camera.discovery.CameraCapabilityRepository
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.camera.storage.MediaStoreSaver
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * - Tap-to-focus and metering.
 * - Smooth optical/digital zoom.
 * - Exposure compensation.
 * - Flash and torch modes.
 * - Orientation awareness and background MediaStore saving.
 * - Strict non-blocking I/O and deterministic ImageProxy closure.
 */
@Singleton
class CameraXController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityRepository: CameraCapabilityRepository,
    private val mediaStoreSaver: MediaStoreSaver,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : CameraController {

    override val capabilities: Flow<CameraCapability> = capabilityRepository.legacyCapability

    private val _sessionState = MutableStateFlow(CameraSessionState.IDLE)
    override val sessionState: Flow<CameraSessionState> = _sessionState.asStateFlow()

    private val _zoomState = MutableStateFlow(ZoomState())
    override val zoomState: Flow<ZoomState> = _zoomState.asStateFlow()

    private val _flashMode = MutableStateFlow(FlashMode.AUTO)
    override val flashMode: Flow<FlashMode> = _flashMode.asStateFlow()

    private val _exposureState = MutableStateFlow(ExposureState())
    override val exposureState: Flow<ExposureState> = _exposureState.asStateFlow()

    private val _lastCapturedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    override val lastCapturedPhoto: Flow<CapturedPhoto?> = _lastCapturedPhoto.asStateFlow()

    private var _isFrontCamera = false
    override val isFrontCamera: Boolean get() = _isFrontCamera

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageCaptureUseCase: ImageCapture? = null

    private var currentLifecycleOwner: LifecycleOwner? = null
    private var currentSurfaceProvider: Preview.SurfaceProvider? = null

    private val isCapturing = AtomicBoolean(false)

    /** Dedicated single-thread background executor for capture callbacks (prevents UI blocking). */
    private val captureExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "optilens-camera-capture")
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

            // 3. Bind to Lifecycle
            val boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                capture,
            )
            camera = boundCamera

            // 4. Observe zoom and exposure bounds
            setupCameraStateObservers(boundCamera)

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
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.w(TAG, "Failed setting exposure compensation: ${e.message}")
            OptiResult.Error(OptiError.Unknown(cause = e, message = "Exposure adjustment failed"))
        }
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
        val result = capturePhoto(0)
        return when (result) {
            is OptiResult.Success -> OptiResult.Success(result.data.uri)
            is OptiResult.Error -> OptiResult.Error(result.error)
            is OptiResult.Loading -> OptiResult.Loading(result.fraction)
        }
    }

    override suspend fun stopPreview() = withContext(dispatchers.main) {
        try {
            cameraProvider?.unbindAll()
            camera = null
            previewUseCase = null
            imageCaptureUseCase = null
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
            cameraProvider?.unbindAll()
            camera = null
            captureExecutor.shutdown()
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
