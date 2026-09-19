package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Test and stub implementation of [CameraController] used for testing
 * and during Phase 01 before real CameraX integration in Phase 04.
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

    var isPreviewActive: Boolean = false
        private set

    var currentZoom: Float = 1.0f
        private set

    var isFrontFacing: Boolean = false
        private set

    override suspend fun startPreview(): OptiResult<Unit> {
        isPreviewActive = true
        return OptiResult.Success(Unit)
    }

    override suspend fun capturePhoto(): OptiResult<String> {
        return OptiResult.Success("content://media/external/images/media/fake_captured_photo")
    }

    override suspend fun stopPreview() {
        isPreviewActive = false
    }

    override suspend fun setZoom(ratio: Float): OptiResult<Unit> {
        currentZoom = ratio
        return OptiResult.Success(Unit)
    }

    override suspend fun flipCamera(): OptiResult<Unit> {
        isFrontFacing = !isFrontFacing
        return OptiResult.Success(Unit)
    }
}
