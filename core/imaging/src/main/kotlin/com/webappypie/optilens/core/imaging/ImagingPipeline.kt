package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.common.result.OptiResult

/**
 * High-level interface for image processing pipeline.
 *
 * Real implementations will integrate multi-frame HDR, night fusion,
 * portrait effects, and AI enhance in subsequent phases (Phases 07–13).
 */
interface ImagingPipeline {

    /**
     * Executes the imaging pipeline on the given [request].
     * Can report progress fractions (0.0 to 1.0) via [onProgress].
     */
    suspend fun process(
        request: ProcessingRequest,
        onProgress: ((Float) -> Unit)? = null,
    ): OptiResult<ProcessingResult>

    /**
     * Cancels any active processing operation.
     */
    suspend fun cancel()
}
