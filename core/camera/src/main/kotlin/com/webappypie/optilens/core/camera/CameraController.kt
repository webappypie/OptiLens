package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the camera hardware controller.
 *
 * Implemented with CameraX in Phase 04.
 * Phase 01 defines the contract only — no real implementation yet.
 */
interface CameraController {

    /** Stream of detected camera capabilities. Emits once on startup. */
    val capabilities: Flow<CameraCapability>

    /**
     * Start the camera preview session.
     * @return [OptiResult.Success] when preview is active.
     */
    suspend fun startPreview(): OptiResult<Unit>

    /**
     * Capture a single still image.
     * @return [OptiResult.Success] with the captured photo URI.
     */
    suspend fun capturePhoto(): OptiResult<String>

    /** Stop the camera preview and release hardware resources. */
    suspend fun stopPreview()

    /** Set the zoom ratio. [ratio] must be within [CameraCapability.availableZoomRatios]. */
    suspend fun setZoom(ratio: Float): OptiResult<Unit>

    /** Switch between front and rear camera. */
    suspend fun flipCamera(): OptiResult<Unit>
}
