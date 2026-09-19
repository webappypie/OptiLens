package com.webappypie.optilens.core.camera

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

/**
 * Abstraction over the camera hardware controller.
 *
 * Implemented with CameraX in Phase 04.
 */
interface CameraController {

    /** Stream of detected camera capabilities. */
    val capabilities: Flow<CameraCapability>

    /** Current state of the camera pipeline session. */
    val sessionState: Flow<CameraSessionState>

    /** Current optical and digital zoom state. */
    val zoomState: Flow<ZoomState>

    /** Active flash mode. */
    val flashMode: Flow<FlashMode>

    /** Current exposure compensation index and bounds. */
    val exposureState: Flow<ExposureState>

    /** Most recently captured photo saved to MediaStore. */
    val lastCapturedPhoto: Flow<CapturedPhoto?>

    /** Whether the front camera is currently active. */
    val isFrontCamera: Boolean

    /**
     * Binds CameraX Preview and ImageCapture use cases to the given lifecycle owner
     * and UI surface provider.
     */
    suspend fun bindPreview(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
    ): OptiResult<Unit>

    /**
     * Start the camera preview session.
     * @return [OptiResult.Success] when preview is active.
     */
    suspend fun startPreview(): OptiResult<Unit>

    /**
     * Trigger optical auto-focus and auto-exposure metering at the given point.
     */
    suspend fun focusAndMeter(meteringPoint: MeteringPoint): OptiResult<Unit>

    /**
     * Set exposure compensation index.
     */
    suspend fun setExposureCompensation(index: Int): OptiResult<Unit>

    /**
     * Set flash mode for still captures.
     */
    suspend fun setFlashMode(mode: FlashMode): OptiResult<Unit>

    /**
     * Enable or disable torch (continuous flash) mode.
     */
    suspend fun enableTorch(enabled: Boolean): OptiResult<Unit>

    /**
     * Capture a single still image with the specified display rotation.
     * @return [OptiResult.Success] with the [CapturedPhoto] record.
     */
    suspend fun capturePhoto(targetRotation: Int = 0): OptiResult<CapturedPhoto>

    /**
     * Legacy capturePhoto returning photo URI string for backward compatibility.
     */
    suspend fun capturePhoto(): OptiResult<String>

    /** Stop the camera preview and release hardware resources. */
    suspend fun stopPreview()

    /** Set the zoom ratio. */
    suspend fun setZoom(ratio: Float): OptiResult<Unit>

    /** Switch between front and rear camera. */
    suspend fun flipCamera(): OptiResult<Unit>

    /** Release all active camera and executor resources. */
    fun release()
}
