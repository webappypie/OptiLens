package com.webappypie.optilens.core.camera

import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
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
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the camera hardware controller.
 *
 * Exposes core capture, preview, hardware-derived zoom stops,
 * live histogram analysis, real-time scene/quality intelligence,
 * and Pro manual photography controls.
 */
interface CameraController {

    /** Stream of detected camera capabilities. */
    val capabilities: Flow<CameraCapability>

    /** Current state of the camera pipeline session. */
    val sessionState: Flow<CameraSessionState>

    /** Current optical and digital zoom state. */
    val zoomState: Flow<ZoomState>

    /** Stream of truthful hardware-derived zoom stops (optical vs digital). */
    val zoomStops: Flow<List<ZoomStop>>

    /** Active flash mode. */
    val flashMode: Flow<FlashMode>

    /** Current exposure compensation index and bounds. */
    val exposureState: Flow<ExposureState>

    /** Stream of Pro manual controls state and hardware support flags. */
    val proState: Flow<ProCameraState>

    /** Live 64-bin luminance histogram from incoming viewfinder frames. */
    val histogramData: Flow<HistogramData>

    /** Real-time scene classification stream. */
    val sceneClassification: Flow<SceneClassification>

    /** Real-time optical and radiometric quality metrics stream. */
    val qualityMetrics: Flow<QualityMetrics>

    /** Real-time physical device and subject motion state stream. */
    val motionState: Flow<MotionState>

    /** Real-time computational capture strategy and UI hint recommendation stream. */
    val captureStrategy: Flow<CaptureStrategy>

    /** Stream of detected faces and landmarks. */
    val detectedFaces: Flow<List<DetectedFace>>

    /** Most recently captured photo saved to MediaStore. */
    val lastCapturedPhoto: Flow<CapturedPhoto?>

    /** Whether the front camera is currently active. */
    val isFrontCamera: Boolean

    /**
     * Binds CameraX Preview, ImageCapture, and ImageAnalysis use cases to the given
     * lifecycle owner and UI surface provider.
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
     * Set manual ISO sensitivity, or null for AUTO AE.
     */
    suspend fun setIso(iso: Int?): OptiResult<Unit>

    /**
     * Set manual exposure time in nanoseconds, or null for AUTO AE.
     */
    suspend fun setShutterSpeed(nanos: Long?): OptiResult<Unit>

    /**
     * Set manual focus distance in diopters (0.0f = infinity), or null for AUTO AF.
     */
    suspend fun setFocusDistance(distanceDiopters: Float?): OptiResult<Unit>

    /**
     * Set white balance mode.
     */
    suspend fun setWhiteBalance(mode: WhiteBalanceMode): OptiResult<Unit>

    /**
     * Reset all manual Pro settings back to default AUTO 3A operation.
     */
    suspend fun resetProToAuto(): OptiResult<Unit>

    /**
     * Enable or disable the live histogram analysis stream to conserve resources.
     */
    fun setHistogramEnabled(enabled: Boolean)

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
