package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.common.result.OptiResult

/**
 * Interface responsible for multi-frame scoring, reference frame selection,
 * pyramidal optical flow alignment, confidence evaluation, and ghost mask generation.
 */
interface FrameAlignmentEngine {

    /**
     * Scores an individual frame for sharpness, exposure clipping, and motion.
     */
    suspend fun scoreFrame(packet: FramePacket, referencePacket: FramePacket? = null): FrameScore

    /**
     * Selects the anchor reference frame from [packets] using precomputed [scores].
     */
    suspend fun selectReference(packets: List<FramePacket>, scores: List<FrameScore>): Int

    /**
     * Aligns candidate frame [candPacket] to [refPacket].
     */
    suspend fun alignFrame(
        refPacket: FramePacket,
        candPacket: FramePacket,
        candScore: FrameScore,
    ): AlignedFrame

    /**
     * Fully registers, scores, and aligns an acquired [BurstResult] sequence into an [AlignedStack].
     */
    suspend fun alignStack(burstResult: BurstResult): OptiResult<AlignedStack>
}
