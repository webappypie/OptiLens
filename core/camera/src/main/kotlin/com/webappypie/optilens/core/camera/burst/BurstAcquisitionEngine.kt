package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.result.OptiResult

/**
 * Interface for synchronized multi-frame computational photography acquisition.
 */
interface BurstAcquisitionEngine {

    /**
     * Executes a synchronized multi-frame capture sequence.
     *
     * @param frameCount Total number of frames to acquire.
     * @param evOffsets List of EV exposure bracket offsets (e.g. [-2, 0, 2] for HDR).
     * @param mode Active computational strategy mode.
     * @param targetRotation Display orientation degrees.
     * @param timeoutMs Maximum allowable duration before aborting to prevent hangs.
     * @param onProgress Optional callback receiving (completedFrames, totalFrames).
     */
    suspend fun acquireBurst(
        frameCount: Int,
        evOffsets: List<Int> = listOf(0),
        mode: CaptureStrategyMode = CaptureStrategyMode.SINGLE_FRAME,
        targetRotation: Int = 0,
        timeoutMs: Long = 6_000L,
        onProgress: ((completed: Int, total: Int) -> Unit)? = null,
    ): OptiResult<BurstResult>

    /**
     * Aborts any currently executing burst sequence immediately and cleans up resources.
     */
    fun cancel()
}
