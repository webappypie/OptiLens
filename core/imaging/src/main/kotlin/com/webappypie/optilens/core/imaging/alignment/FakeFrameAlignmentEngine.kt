package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult

/**
 * Deterministic fake implementation of [FrameAlignmentEngine] for unit testing.
 */
class FakeFrameAlignmentEngine : FrameAlignmentEngine {

    var shouldFail: Boolean = false
    var rejectIndices: Set<Int> = emptySet()
    var defaultConfidence: Float = 0.92f
    var forcedReferenceIndex: Int? = null

    override suspend fun scoreFrame(
        packet: FramePacket,
        referencePacket: FramePacket?,
    ): FrameScore {
        val gyro = packet.gyroWindow.meanAngularSpeed
        val sharpness = if (gyro > 0.2f) 10.0f else 60.0f
        val expPenalty = 0.1f
        val total = sharpness * (1.0f - expPenalty) * (1.0f - minOf(1.0f, gyro * 3.0f))

        return FrameScore(
            sharpnessScore = sharpness,
            exposurePenalty = expPenalty,
            motionDifference = if (referencePacket != null) 5.0f else 0.0f,
            focusConfidence = 0.9f,
            gyroAngularSpeed = gyro,
            totalScore = total,
        )
    }

    override suspend fun selectReference(
        packets: List<FramePacket>,
        scores: List<FrameScore>,
    ): Int {
        return forcedReferenceIndex ?: ReferenceFrameSelector().selectReferenceIndex(packets, scores)
    }

    override suspend fun alignFrame(
        refPacket: FramePacket,
        candPacket: FramePacket,
        candScore: FrameScore,
    ): AlignedFrame {
        val isRef = (refPacket.sequenceIndex == candPacket.sequenceIndex)
        val shouldReject = rejectIndices.contains(candPacket.sequenceIndex)

        return AlignedFrame(
            sequenceIndex = candPacket.sequenceIndex,
            isReference = isRef,
            score = candScore,
            homography = if (isRef) HomographyMatrix.IDENTITY else HomographyMatrix.translation(1.0f, 0.0f),
            alignmentConfidence = if (shouldReject) 0.30f else (if (isRef) 1.0f else defaultConfidence),
            isRejected = shouldReject,
            rejectionReason = if (shouldReject) "Forced rejection in test" else null,
            ghostMask = GhostMask.empty(refPacket.width, refPacket.height),
            packet = candPacket,
        )
    }

    override suspend fun alignStack(burstResult: BurstResult): OptiResult<AlignedStack> {
        if (shouldFail) {
            return OptiResult.Error(OptiError.ProcessingFailed("Simulated alignment failure", null))
        }

        val packets = burstResult.packets
        if (packets.isEmpty()) {
            return OptiResult.Error(OptiError.ProcessingFailed("Empty burst sequence", null))
        }

        val scores = packets.map { scoreFrame(it, null) }
        val refIndex = selectReference(packets, scores)
        val refPacket = packets[refIndex]
        val refScore = scores[refIndex]

        val alignedList = mutableListOf<AlignedFrame>()
        val rejectedList = mutableListOf<AlignedFrame>()
        var refFrame: AlignedFrame? = null

        for (i in packets.indices) {
            val pkt = packets[i]
            val score = scores[i]
            val aligned = alignFrame(refPacket, pkt, score)

            if (i == refIndex) {
                refFrame = aligned
            } else if (aligned.isRejected) {
                rejectedList.add(aligned)
            } else {
                alignedList.add(aligned)
            }
        }

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

        return OptiResult.Success(
            AlignedStack(
                referenceIndex = refIndex,
                referenceFrame = anchor,
                alignedFrames = alignedList,
                rejectedFrames = rejectedList,
                diagnostics = AlignmentDiagnostics(
                    scoringDurationMs = 5L,
                    alignmentDurationMs = 15L,
                    totalDurationMs = 20L,
                    candidateCount = packets.size,
                    alignedCount = alignedList.size,
                    rejectedCount = rejectedList.size,
                    averageConfidence = defaultConfidence,
                ),
            )
        )
    }
}
