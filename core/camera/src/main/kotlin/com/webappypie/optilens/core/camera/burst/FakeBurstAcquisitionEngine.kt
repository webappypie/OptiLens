package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.BurstDiagnostics
import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult

/**
 * Fake implementation of [BurstAcquisitionEngine] for deterministic unit testing.
 */
class FakeBurstAcquisitionEngine(
    val bufferPool: BoundedBufferPool = BoundedBufferPool(maxCapacity = 12),
) : BurstAcquisitionEngine {

    var shouldFail: Boolean = false
    var shouldFallbackToSingle: Boolean = false
    var lastAcquiredCount: Int = 0
    var lastEvOffsets: List<Int> = emptyList()

    override suspend fun acquireBurst(
        frameCount: Int,
        evOffsets: List<Int>,
        mode: CaptureStrategyMode,
        targetRotation: Int,
        timeoutMs: Long,
        onProgress: ((completed: Int, total: Int) -> Unit)?,
    ): OptiResult<BurstResult> {
        lastAcquiredCount = frameCount
        lastEvOffsets = evOffsets

        if (shouldFail) {
            return OptiResult.Error(OptiError.ProcessingFailed("Simulated burst failure", null))
        }

        val actualCount = if (shouldFallbackToSingle) 1 else frameCount.coerceAtLeast(1)
        val packets = mutableListOf<FramePacket>()

        for (i in 0 until actualCount) {
            val pooledBuffer = bufferPool.acquire(1024)
            val packet = FramePacket(
                sequenceIndex = i,
                totalSequenceCount = actualCount,
                buffer = pooledBuffer,
                metadata = FrameMetadata(
                    timestampNs = System.nanoTime(),
                    exposureTimeNs = 10_000_000L, // 1/100s
                    iso = 100,
                    orientationDegrees = targetRotation,
                ),
                gyroWindow = GyroWindow.EMPTY,
                width = 4032,
                height = 3024,
            )
            packets.add(packet)
            onProgress?.invoke(i + 1, actualCount)
        }

        return OptiResult.Success(
            BurstResult(
                packets = packets,
                modeUsed = mode,
                diagnostics = BurstDiagnostics(
                    burstPreparationTimeMs = 10L,
                    interFrameIntervalsMs = List(actualCount) { 33L },
                    totalBurstDurationMs = (actualCount * 33L),
                    averageFrameLatencyMs = 33.0f,
                    droppedFramesCount = 0,
                    poolStats = bufferPool.getStats(),
                ),
                isFallbackSingleFrame = shouldFallbackToSingle,
            )
        )
    }

    override fun cancel() = Unit
}
