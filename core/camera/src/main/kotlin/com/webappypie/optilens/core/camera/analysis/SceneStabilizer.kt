package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.SceneType
import java.util.ArrayDeque

/**
 * Temporal smoothing filter with hysteresis to prevent rapid flickering of scene labels.
 *
 * Maintains a rolling window of recent frame classifications and enforces persistence
 * thresholds before transitioning the active scene label.
 */
class SceneStabilizer(
    private val windowSize: Int = 7,
    private val transitionThreshold: Int = 4,
) {
    private val history = ArrayDeque<SceneClassification>(windowSize)
    private var activeScene: SceneType = SceneType.GENERAL

    /**
     * Stabilizes incoming raw classification over time.
     */
    fun stabilize(raw: SceneClassification): SceneClassification {
        if (history.size >= windowSize) {
            history.removeFirst()
        }
        history.addLast(raw)

        // Count votes per scene type in rolling window
        val votes = mutableMapOf<SceneType, Int>()
        val confidenceSums = mutableMapOf<SceneType, Float>()

        for (item in history) {
            votes[item.primaryScene] = (votes[item.primaryScene] ?: 0) + 1
            confidenceSums[item.primaryScene] = (confidenceSums[item.primaryScene] ?: 0f) + item.confidence
        }

        // Find majority winner
        val mostVoted = votes.maxByOrNull { it.value }

        if (mostVoted != null && mostVoted.value >= transitionThreshold) {
            activeScene = mostVoted.key
        } else if (history.size == 1) {
            activeScene = raw.primaryScene
        }

        val activeVotes = votes[activeScene] ?: 0
        val stabilityScore = activeVotes.toFloat() / history.size.toFloat()
        val averageConfidence = (confidenceSums[activeScene] ?: raw.confidence) / activeVotes.coerceAtLeast(1)

        return raw.copy(
            primaryScene = activeScene,
            confidence = averageConfidence,
            stabilityScore = stabilityScore,
        )
    }

    fun reset() {
        history.clear()
        activeScene = SceneType.GENERAL
    }
}
