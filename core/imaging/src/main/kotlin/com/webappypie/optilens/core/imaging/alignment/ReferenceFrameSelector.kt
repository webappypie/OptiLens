package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.model.FramePacket

/**
 * Heuristic selection engine that identifies the optimal anchor reference frame
 * from an acquired multi-frame sequence.
 *
 * Prioritizes:
 * 1. High optical sharpness (Tenengrad energy).
 * 2. Low physical gyroscope motion during exposure.
 * 3. Minimal highlight/shadow clipping (preferring 0 EV baseline in bracketed sequences).
 */
class ReferenceFrameSelector {

    /**
     * Selects the index of the best anchor frame in [packets] using pre-computed [scores].
     */
    fun selectReferenceIndex(
        packets: List<FramePacket>,
        scores: List<FrameScore>,
    ): Int {
        if (packets.isEmpty()) return 0
        if (packets.size == 1) return 0
        if (scores.size != packets.size) return packets.size / 2

        var bestIndex = 0
        var bestCompositeScore = -1.0f

        for (i in packets.indices) {
            val packet = packets[i]
            val score = scores[i]

            // 1. Gyro stability penalty
            val gyroSpeed = packet.gyroWindow.meanAngularSpeed
            val gyroPenalty = (gyroSpeed * 3.0f).coerceIn(0.0f, 0.8f)

            // 2. Exposure penalty (strongly penalize severely over/underexposed brackets as anchors)
            val expPenalty = score.exposurePenalty.coerceIn(0.0f, 0.9f)

            // 3. Composite score: high sharpness, low motion, balanced exposure
            val composite = score.sharpnessScore * (1.0f - expPenalty * 0.7f) * (1.0f - gyroPenalty) *
                    (0.8f + 0.2f * score.focusConfidence)

            if (composite > bestCompositeScore) {
                bestCompositeScore = composite
                bestIndex = i
            }
        }

        return bestIndex
    }
}
