package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.common.result.OptiResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fake implementation of [ImagingPipeline] for tests and Phase 01 architecture.
 */
@Singleton
class FakeImagingPipeline @Inject constructor() : ImagingPipeline {

    var isCancelled: Boolean = false
        private set

    var lastRequest: ProcessingRequest? = null
        private set

    override suspend fun process(
        request: ProcessingRequest,
        onProgress: ((Float) -> Unit)?,
    ): OptiResult<ProcessingResult> {
        isCancelled = false
        lastRequest = request

        onProgress?.invoke(0.5f)
        onProgress?.invoke(1.0f)

        return OptiResult.Success(
            ProcessingResult(
                outputUri = "content://media/external/images/media/fake_processed_photo",
                originalUri = if (request.keepOriginal) request.inputUri else null,
                modeUsed = request.mode,
                processingDurationMs = 120L,
                width = 4000,
                height = 3000,
                isHdrApplied = request.mode == ProcessingMode.HDR,
            )
        )
    }

    override suspend fun cancel() {
        isCancelled = true
    }
}
