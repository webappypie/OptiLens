package com.webappypie.optilens.core.camera.burst.model

import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode

/**
 * Output model produced by multi-frame burst acquisition.
 *
 * Implements [AutoCloseable] to cascade closure to all constituent [packets]
 * and release pooled buffers deterministically.
 *
 * @param packets Synchronized frames with metadata and gyro windows.
 * @param modeUsed Computational strategy mode active during the capture.
 * @param diagnostics Timing and latency profiling metrics.
 * @param isFallbackSingleFrame True if hardware or session issues triggered graceful degradation to single frame.
 */
data class BurstResult(
    val packets: List<FramePacket>,
    val modeUsed: CaptureStrategyMode = CaptureStrategyMode.SINGLE_FRAME,
    val diagnostics: BurstDiagnostics = BurstDiagnostics.EMPTY,
    val isFallbackSingleFrame: Boolean = false,
) : AutoCloseable {

    val frameCount: Int
        get() = packets.size

    override fun close() {
        for (packet in packets) {
            try {
                packet.close()
            } catch (_: Exception) {
                // Drop gracefully on cleanup
            }
        }
    }
}
