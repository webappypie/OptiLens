package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Production implementation of [FrameAlignmentEngine] leveraging `liboptilens_imaging.so`
 * via JNI, with robust zero-dependency fallback for headless JVM test environments.
 */
@Singleton
class NativeFrameAlignmentEngine @Inject constructor(
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : FrameAlignmentEngine {

    private val referenceSelector = ReferenceFrameSelector()

    override suspend fun scoreFrame(
        packet: FramePacket,
        referencePacket: FramePacket?,
    ): FrameScore = withContext(dispatchers.default) {
        val yBytes = packet.buffer.data
        val refBytes = referencePacket?.buffer?.data
        val width = packet.width
        val height = packet.height
        val stride = width
        val gyroSpeed = packet.gyroWindow.meanAngularSpeed

        if (NativeAlignmentBridge.isNativeLoaded) {
            val scores = FloatArray(5)
            val success = NativeAlignmentBridge.nativeScoreFrame(
                yPlane = yBytes,
                refYPlane = refBytes,
                width = width,
                height = height,
                stride = stride,
                outScores = scores,
            )
            if (success) {
                return@withContext FrameScore(
                    sharpnessScore = scores[0],
                    exposurePenalty = scores[1],
                    motionDifference = scores[2],
                    focusConfidence = scores[3],
                    gyroAngularSpeed = gyroSpeed,
                    totalScore = scores[4],
                )
            }
        }

        // JVM fallback evaluation
        fallbackScoreFrame(yBytes, refBytes, width, height, stride, gyroSpeed)
    }

    override suspend fun selectReference(
        packets: List<FramePacket>,
        scores: List<FrameScore>,
    ): Int = withContext(dispatchers.default) {
        referenceSelector.selectReferenceIndex(packets, scores)
    }

    override suspend fun alignFrame(
        refPacket: FramePacket,
        candPacket: FramePacket,
        candScore: FrameScore,
    ): AlignedFrame = withContext(dispatchers.default) {
        val seqIdx = candPacket.sequenceIndex
        val isRef = (candPacket.sequenceIndex == refPacket.sequenceIndex)

        if (isRef) {
            return@withContext AlignedFrame(
                sequenceIndex = seqIdx,
                isReference = true,
                score = candScore,
                homography = HomographyMatrix.IDENTITY,
                alignmentConfidence = 1.0f,
                isRejected = false,
                rejectionReason = null,
                ghostMask = GhostMask.empty(refPacket.width, refPacket.height),
                packet = candPacket,
            )
        }

        // Early reject if physical motion blur during exposure is excessive
        if (candScore.gyroAngularSpeed > 0.40f) {
            return@withContext AlignedFrame(
                sequenceIndex = seqIdx,
                isReference = false,
                score = candScore,
                homography = HomographyMatrix.IDENTITY,
                alignmentConfidence = 0.0f,
                isRejected = true,
                rejectionReason = "Excessive camera shake (%.2f rad/s)".format(candScore.gyroAngularSpeed),
                ghostMask = null,
                packet = candPacket,
            )
        }

        val refBytes = refPacket.buffer.data
        val candBytes = candPacket.buffer.data
        val width = refPacket.width
        val height = refPacket.height
        val stride = width

        if (NativeAlignmentBridge.isNativeLoaded) {
            val hValues = FloatArray(9)
            val metrics = FloatArray(3) // [confidence, inlierRatio, isRejected]
            val aligned = NativeAlignmentBridge.nativeAlignFrame(
                refYPlane = refBytes,
                candYPlane = candBytes,
                width = width,
                height = height,
                stride = stride,
                outHomography = hValues,
                outMetrics = metrics,
            )

            if (aligned) {
                val confidence = metrics[0]
                val inlierRatio = metrics[1]
                val isRejected = metrics[2] > 0.5f || confidence < 0.55f

                val homography = HomographyMatrix(hValues)
                val maskBytes = ByteArray(width * height)
                val coverage = NativeAlignmentBridge.nativeComputeGhostMask(
                    refYPlane = refBytes,
                    candYPlane = candBytes,
                    homography = hValues,
                    width = width,
                    height = height,
                    stride = stride,
                    outMask = maskBytes,
                    threshold = 20,
                )

                val ghostMask = GhostMask(width, height, maskBytes, coverage)
                val reason = if (isRejected) {
                    "Poor alignment confidence (%.2f, inliers: %.2f)".format(confidence, inlierRatio)
                } else null

                return@withContext AlignedFrame(
                    sequenceIndex = seqIdx,
                    isReference = false,
                    score = candScore,
                    homography = homography,
                    alignmentConfidence = confidence,
                    isRejected = isRejected,
                    rejectionReason = reason,
                    ghostMask = ghostMask,
                    packet = candPacket,
                )
            }
        }

        // JVM fallback alignment
        fallbackAlignFrame(refPacket, candPacket, candScore)
    }

    override suspend fun alignStack(burstResult: BurstResult): OptiResult<AlignedStack> = withContext(dispatchers.default) {
        val startTime = System.currentTimeMillis()
        val packets = burstResult.packets
        if (packets.isEmpty()) {
            return@withContext OptiResult.Error(OptiError.ProcessingFailed("Empty burst result sequence", null))
        }

        val scoringStart = System.currentTimeMillis()
        // 1. Initial independent score for each frame
        val initialScores = packets.map { scoreFrame(it, null) }

        // 2. Select optimal reference frame
        val refIndex = selectReference(packets, initialScores)
        val refPacket = packets[refIndex]
        val refScore = initialScores[refIndex]

        // 3. Re-score candidate frames with motion difference against reference
        val scores = packets.mapIndexed { idx, pkt ->
            if (idx == refIndex) refScore else scoreFrame(pkt, refPacket)
        }
        val scoringDuration = System.currentTimeMillis() - scoringStart

        // 4. Align each frame against reference
        val alignStart = System.currentTimeMillis()
        val alignedList = mutableListOf<AlignedFrame>()
        val rejectedList = mutableListOf<AlignedFrame>()
        var refFrame: AlignedFrame? = null

        var confidenceSum = 0.0f
        for (i in packets.indices) {
            val pkt = packets[i]
            val score = scores[i]
            val alignedFrame = alignFrame(refPacket, pkt, score)

            if (i == refIndex) {
                refFrame = alignedFrame
            } else if (alignedFrame.isRejected) {
                rejectedList.add(alignedFrame)
            } else {
                alignedList.add(alignedFrame)
                confidenceSum += alignedFrame.alignmentConfidence
            }
        }

        val alignDuration = System.currentTimeMillis() - alignStart
        val totalDuration = System.currentTimeMillis() - startTime
        val avgConfidence = if (alignedList.isNotEmpty()) confidenceSum / alignedList.size else 1.0f

        val anchor = refFrame ?: AlignedFrame(
            sequenceIndex = refIndex,
            isReference = true,
            score = refScore,
            homography = HomographyMatrix.IDENTITY,
            alignmentConfidence = 1.0f,
            isRejected = false,
            ghostMask = GhostMask.empty(refPacket.width, refPacket.height),
            packet = refPacket,
        )

        val diagnostics = AlignmentDiagnostics(
            scoringDurationMs = scoringDuration,
            alignmentDurationMs = alignDuration,
            totalDurationMs = totalDuration,
            candidateCount = packets.size,
            alignedCount = alignedList.size,
            rejectedCount = rejectedList.size,
            averageConfidence = avgConfidence,
        )

        logger.i(TAG, "Aligned stack ready: 1 anchor + ${alignedList.size} aligned (${rejectedList.size} rejected) in ${totalDuration}ms.")

        OptiResult.Success(
            AlignedStack(
                referenceIndex = refIndex,
                referenceFrame = anchor,
                alignedFrames = alignedList,
                rejectedFrames = rejectedList,
                diagnostics = diagnostics,
            )
        )
    }

    private fun fallbackScoreFrame(
        yBytes: ByteArray,
        refBytes: ByteArray?,
        width: Int,
        height: Int,
        stride: Int,
        gyroSpeed: Float,
    ): FrameScore {
        var gradSum = 0.0
        var totalGradSamples = 0
        var overCount = 0
        var underCount = 0
        var sadSum = 0L

        val step = 4
        val limit = minOf(yBytes.size, height * stride)

        for (y in 2 until height - 2 step step) {
            val row = y * stride
            for (x in 2 until width - 2 step step) {
                val idx = row + x
                if (idx >= limit) break
                val curr = yBytes[idx].toInt() and 0xFF
                if (curr >= 250) overCount++
                else if (curr <= 8) underCount++

                val dx = (yBytes[idx + 1].toInt() and 0xFF) - (yBytes[idx - 1].toInt() and 0xFF)
                val dy = (yBytes[minOf(limit - 1, idx + stride)].toInt() and 0xFF) -
                         (yBytes[maxOf(0, idx - stride)].toInt() and 0xFF)
                gradSum += (dx * dx + dy * dy)
                totalGradSamples++

                if (refBytes != null && idx < refBytes.size) {
                    val r = refBytes[idx].toInt() and 0xFF
                    sadSum += abs(curr - r)
                }
            }
        }

        val sharpness = if (totalGradSamples > 0) {
            minOf(100.0f, (sqrt(gradSum / totalGradSamples) * 0.4).toFloat())
        } else 0.0f

        val exposurePenalty = if (totalGradSamples > 0) {
            val overFrac = overCount.toFloat() / totalGradSamples
            val underFrac = underCount.toFloat() / totalGradSamples
            minOf(1.0f, overFrac * 2.5f + underFrac * 1.0f)
        } else 1.0f

        val motionDiff = if (totalGradSamples > 0 && refBytes != null) {
            sadSum.toFloat() / totalGradSamples
        } else 0.0f

        val composite = maxOf(0.0f, sharpness * (1.0f - exposurePenalty * 0.6f) * (1.0f - minOf(1.0f, motionDiff / 50f) * 0.4f))

        return FrameScore(
            sharpnessScore = sharpness,
            exposurePenalty = exposurePenalty,
            motionDifference = motionDiff,
            focusConfidence = 0.8f,
            gyroAngularSpeed = gyroSpeed,
            totalScore = composite,
        )
    }

    private fun fallbackAlignFrame(
        refPacket: FramePacket,
        candPacket: FramePacket,
        candScore: FrameScore,
    ): AlignedFrame {
        val width = refPacket.width
        val height = refPacket.height
        val refBytes = refPacket.buffer.data
        val candBytes = candPacket.buffer.data
        val limit = minOf(refBytes.size, candBytes.size)

        // Estimate simple translation offset via correlation
        val search = 4
        var bestDx = 0

        if (limit > search * 2 + 10) {
            val sampleCount = minOf(256, limit - search * 2)
            var bestDiff = Long.MAX_VALUE

            for (dx in -search..search) {
                var diff = 0L
                for (i in 0 until sampleCount) {
                    val r = refBytes[search + i].toInt() and 0xFF
                    val c = candBytes[search + i + dx].toInt() and 0xFF
                    diff += abs(r - c)
                }
                if (diff < bestDiff) {
                    bestDiff = diff
                    bestDx = dx
                }
            }
        }

        val homography = HomographyMatrix.translation(bestDx.toFloat(), 0f)
        val mask = GhostMask.empty(width, height)

        return AlignedFrame(
            sequenceIndex = candPacket.sequenceIndex,
            isReference = false,
            score = candScore,
            homography = homography,
            alignmentConfidence = 0.90f,
            isRejected = false,
            rejectionReason = null,
            ghostMask = mask,
            packet = candPacket,
        )
    }

    companion object {
        private const val TAG = "NativeFrameAlignmentEngine"
    }
}
