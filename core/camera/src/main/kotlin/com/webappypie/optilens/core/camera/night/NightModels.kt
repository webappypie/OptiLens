package com.webappypie.optilens.core.camera.night

import kotlinx.serialization.Serializable

/**
 * Capture pathway for low-light / Night mode execution.
 */
@Serializable
enum class NightModeType {
    /** Hardware-accelerated OEM vendor Night extension (CameraX ExtensionMode.NIGHT). */
    VENDOR_EXTENSION,

    /** Multi-frame alignment, de-ghosting, temporal fusion, and tone mapping pipeline. */
    CUSTOM_COMPUTATIONAL,
}

/**
 * Categorization of physical device stability for exposure integration planning.
 */
@Serializable
enum class StabilityClassification {
    /** Phone is resting on a tripod, table, or rigid mount (angular velocity < 0.025 rad/s). */
    TRIPOD,

    /** Firm, steady handheld posture with minimal tremor (angular velocity < 0.10 rad/s). */
    HANDHELD_STABLE,

    /** Typical handheld posture with moderate natural movement (angular velocity 0.10..0.25 rad/s). */
    HANDHELD_MODERATE,

    /** Active walking, vehicle motion, or unsteady grip (angular velocity > 0.25 rad/s). */
    UNSTEADY,
}

/**
 * User or system preference guiding pathway selection.
 */
@Serializable
enum class NightPolicyPreference {
    /** Intelligently select between vendor and custom based on hardware tier and stability. */
    AUTO,

    /** Prioritize OEM vendor extension whenever reported available by CameraX. */
    PREFER_VENDOR,

    /** Always use custom multi-frame computational fusion pipeline. */
    PREFER_CUSTOM,
}

/**
 * Dynamic burst exposure plan calculated for the active night scene.
 */
@Serializable
data class NightExposurePlan(
    val frameCount: Int,
    val targetShutterNanos: Long,
    val evOffsets: List<Int> = listOf(0),
    val expectedCaptureDurationMs: Long,
    val stabilityRequired: StabilityClassification,
    val isoBoostFactor: Float = 1.0f,
    val enableChromaCleanup: Boolean = true,
    val enableHighlightProtection: Boolean = true,
    val conservativeSharpening: Boolean = true,
) {
    companion object {
        val DEFAULT_HANDHELD = NightExposurePlan(
            frameCount = 6,
            targetShutterNanos = 66_666_666L, // ~1/15s
            evOffsets = listOf(0),
            expectedCaptureDurationMs = 1200L,
            stabilityRequired = StabilityClassification.HANDHELD_STABLE,
            isoBoostFactor = 1.2f,
            enableChromaCleanup = true,
            enableHighlightProtection = true,
            conservativeSharpening = true,
        )

        val TRIPOD_DEEP_STACK = NightExposurePlan(
            frameCount = 10,
            targetShutterNanos = 250_000_000L, // ~1/4s
            evOffsets = listOf(0),
            expectedCaptureDurationMs = 3000L,
            stabilityRequired = StabilityClassification.TRIPOD,
            isoBoostFactor = 1.0f,
            enableChromaCleanup = true,
            enableHighlightProtection = true,
            conservativeSharpening = true,
        )

        val FAST_FALLBACK = NightExposurePlan(
            frameCount = 2,
            targetShutterNanos = 33_333_333L, // ~1/30s
            evOffsets = listOf(0),
            expectedCaptureDurationMs = 350L,
            stabilityRequired = StabilityClassification.UNSTEADY,
            isoBoostFactor = 1.5f,
            enableChromaCleanup = true,
            enableHighlightProtection = false,
            conservativeSharpening = true,
        )
    }
}

/**
 * Complete execution plan produced by [NightModePolicyEngine].
 */
@Serializable
data class NightExecutionPlan(
    val mode: NightModeType,
    val exposurePlan: NightExposurePlan,
    val reason: String,
    val isPreviewBoostRecommended: Boolean = false,
    val movingSubjectFallbackTriggered: Boolean = false,
)
