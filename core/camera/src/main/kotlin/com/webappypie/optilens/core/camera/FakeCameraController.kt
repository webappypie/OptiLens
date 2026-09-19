package com.webappypie.optilens.core.camera

import android.net.Uri
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.camera.model.ExposureState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.ZoomState
import com.webappypie.optilens.core.common.result.OptiResult
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

    private val _lastCapturedPhoto = MutableStateFlow<CapturedPhoto?>(null)
    override val lastCapturedPhoto: Flow<CapturedPhoto?> = _lastCapturedPhoto.asStateFlow()

    private var _isFrontCamera = false
    override val isFrontCamera: Boolean get() = _isFrontCamera

    var isPreviewActive: Boolean = false
        private set

    var currentZoom: Float = 1.0f
        private set

    var isTorchEnabled: Boolean = false
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

    override suspend fun capturePhoto(targetRotation: Int): OptiResult<CapturedPhoto> {
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
        val result = capturePhoto(0)
        return when (result) {
            is OptiResult.Success -> OptiResult.Success(result.data.uri)
            is OptiResult.Error -> OptiResult.Error(result.error)
            is OptiResult.Loading -> OptiResult.Loading(result.fraction)
        }
    }

    override suspend fun stopPreview() {
        isPreviewActive = false
        _sessionState.value = CameraSessionState.IDLE
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

    override fun release() {
        isPreviewActive = false
        _sessionState.value = CameraSessionState.IDLE
    }
}
