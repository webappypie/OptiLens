package com.webappypie.optilens.core.camera.burst.model

import com.webappypie.optilens.core.camera.burst.pool.PoolStats

/**
 * Performance instrumentation and timing metrics for an acquired burst sequence.
 *
 * @param burstPreparationTimeMs Milliseconds spent configuring Camera2 capture parameters.
 * @param interFrameIntervalsMs Durations in milliseconds between consecutive frame deliveries.
 * @param totalBurstDurationMs Total wall-clock time from burst initiation to last frame receipt.
 * @param averageFrameLatencyMs Mean delivery latency per frame.
 * @param droppedFramesCount Number of frames requested but dropped or failed.
 * @param poolStats Snapshot of memory pool allocations during the burst.
 */
data class BurstDiagnostics(
    val burstPreparationTimeMs: Long = 0L,
    val interFrameIntervalsMs: List<Long> = emptyList(),
    val totalBurstDurationMs: Long = 0L,
    val averageFrameLatencyMs: Float = 0.0f,
    val droppedFramesCount: Int = 0,
    val poolStats: PoolStats? = null,
) {
    companion object {
        val EMPTY = BurstDiagnostics()
    }
}
