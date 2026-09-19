package com.webappypie.optilens.core.camera.night

import java.util.ArrayDeque

/**
 * High-precision stability classifier evaluating rotational gyroscope velocity
 * and temporal persistence to detect Tripod, Handheld Stable, Handheld Moderate,
 * and Unsteady postures.
 */
class NightStabilityDetector(
    private val windowSize: Int = 10,
    private val tripodHoldDurationMs: Long = 400L,
) {
    private data class Sample(val velocity: Float, val timestampMs: Long)

    private val samples = ArrayDeque<Sample>(windowSize)
    private var tripodCandidateStartMs: Long? = null

    /**
     * Evaluates latest angular velocity measurement and updates stability classification.
     *
     * @param angularVelocityRadPerSec Current smoothed angular velocity in rad/s.
     * @param timestampMs Measurement timestamp in milliseconds.
     */
    fun evaluateStability(
        angularVelocityRadPerSec: Float,
        timestampMs: Long = System.currentTimeMillis(),
    ): StabilityAssessment {
        if (samples.size >= windowSize) {
            samples.removeFirst()
        }
        samples.addLast(Sample(angularVelocityRadPerSec, timestampMs))

        val avgVelocity = samples.map { it.velocity }.average().toFloat()

        // 1. Tripod detection with temporal hysteresis
        val isTripodVelocity = angularVelocityRadPerSec < 0.025f && avgVelocity < 0.025f
        if (isTripodVelocity) {
            if (tripodCandidateStartMs == null) {
                tripodCandidateStartMs = timestampMs
            }
        } else {
            tripodCandidateStartMs = null
        }

        val tripodDuration = if (tripodCandidateStartMs != null) {
            timestampMs - tripodCandidateStartMs!!
        } else 0L

        val isTripodConfirmed = tripodDuration >= tripodHoldDurationMs

        // 2. Classify posture
        val classification = when {
            isTripodConfirmed -> StabilityClassification.TRIPOD
            avgVelocity < 0.10f -> StabilityClassification.HANDHELD_STABLE
            avgVelocity <= 0.25f -> StabilityClassification.HANDHELD_MODERATE
            else -> StabilityClassification.UNSTEADY
        }

        // 3. Compute continuous stability score 0.0 to 100.0 (100 = perfectly motionless)
        val rawScore = 100.0f * (1.0f - (avgVelocity / 0.35f)).coerceIn(0.0f, 1.0f)
        val stabilityConfidence = when (classification) {
            StabilityClassification.TRIPOD -> (0.85f + (tripodDuration / 1000f) * 0.15f).coerceAtMost(1.0f)
            StabilityClassification.HANDHELD_STABLE -> 0.90f
            StabilityClassification.HANDHELD_MODERATE -> 0.70f
            StabilityClassification.UNSTEADY -> 0.40f
        }

        return StabilityAssessment(
            classification = classification,
            stabilityScore = rawScore,
            stabilityConfidence = stabilityConfidence,
            averageAngularVelocity = avgVelocity,
            isTripod = isTripodConfirmed,
            timestampMs = timestampMs,
        )
    }

    fun reset() {
        samples.clear()
        tripodCandidateStartMs = null
    }
}

/**
 * Result bundle produced by [NightStabilityDetector].
 */
data class StabilityAssessment(
    val classification: StabilityClassification,
    val stabilityScore: Float = 100f,
    val stabilityConfidence: Float = 1.0f,
    val averageAngularVelocity: Float = 0.0f,
    val isTripod: Boolean = (classification == StabilityClassification.TRIPOD),
    val timestampMs: Long = 0L,
)
