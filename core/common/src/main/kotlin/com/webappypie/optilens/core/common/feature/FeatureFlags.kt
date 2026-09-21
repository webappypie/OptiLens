package com.webappypie.optilens.core.common.feature

/**
 * Feature flag interface.
 *
 * Local defaults are always safe conservative values — features off by default
 * until explicitly enabled. Firebase Remote Config (Phase 17) will provide
 * remote overrides without changing this interface.
 */
interface FeatureFlags {
    /** AI Auto scene detection hints visible to the user. */
    val sceneHintsEnabled: Boolean

    /** Night Mode custom burst capture pipeline active. */
    val nightModeBurstEnabled: Boolean

    /** Super-resolution zoom processing active. */
    val superResolutionEnabled: Boolean

    /** AI Enhance post-capture flow active. */
    val aiEnhanceEnabled: Boolean

    /** Pro/RAW capture mode accessible. */
    val proRawEnabled: Boolean

    /** Advanced AI tools tab visible. */
    val advancedAiToolsEnabled: Boolean

    /** Internal diagnostics screen shown in debug builds. */
    val diagnosticsEnabled: Boolean

    // ── Phase 17 Business, Remote Config & Safe Ads ───────────────

    /** Master switch for non-viewfinder advertising. */
    val adsEnabled: Boolean

    /** Active ad network provider identifier (e.g. "admob", "noop"). */
    val activeAdProvider: String

    /** Minimum interval between interstitial ad displays in seconds. */
    val adFrequencyIntervalSec: Long

    /** Minimum photo captures before an interstitial ad can display. */
    val adMinCapturesBetweenInterstitials: Int

    /** Hardware capture burst frame count override (null to use device capability default). */
    val maxBurstCountOverride: Int?

    /** Maximum night mode exposure duration override in milliseconds. */
    val nightExposureMaxMsOverride: Long?

    /** Maximum super-resolution zoom factor override. */
    val superResolutionMaxScaleOverride: Float?

    /** Serialized JSON containing per-device processing overrides. */
    val deviceSpecificOverridesJson: String?

    /** Dynamic promotional banner copy for Pro upgrade screen. */
    val promotionalCopy: String?

    // ── Phase 24 Growth & Experimentation Hooks ──────────────────

    /** Onboarding variant experiment ("control", "feature_focus", "speed_focus"). */
    val onboardingVariant: String

    /** Default before/after split slider position in AI Enhance view (0.0 to 1.0). */
    val aiEnhanceDefaultSplit: Float

    /** Capture count required to trigger in-app review eligibility gate. */
    val reviewTriggerThreshold: Int

    /** Whether watermark branding is suggested/enabled by default on export (referral loop). */
    val enableWatermarkByDefault: Boolean

    /** Experiment assignment cohort identifier ("control", "variant_a", "variant_b"). */
    val experimentCohort: String
}

/**
 * Conservative local defaults — always compile-time safe.
 * Used when Remote Config is not yet initialised or is unavailable.
 */
object LocalFeatureFlags : FeatureFlags {
    override val sceneHintsEnabled: Boolean                 = true
    override val nightModeBurstEnabled: Boolean             = true
    override val superResolutionEnabled: Boolean            = true
    override val aiEnhanceEnabled: Boolean                  = true
    override val proRawEnabled: Boolean                     = true
    override val advancedAiToolsEnabled: Boolean            = true   // Phase 18 Advanced AI Tools active
    override val diagnosticsEnabled: Boolean                = false  // enabled per-build via BuildInfo

    override val adsEnabled: Boolean                        = true
    override val activeAdProvider: String                   = "admob"
    override val adFrequencyIntervalSec: Long               = 300L   // 5 minutes
    override val adMinCapturesBetweenInterstitials: Int     = 3
    override val maxBurstCountOverride: Int?                = null
    override val nightExposureMaxMsOverride: Long?          = null
    override val superResolutionMaxScaleOverride: Float?    = null
    override val deviceSpecificOverridesJson: String?       = null
    override val promotionalCopy: String?                   = null

    override val onboardingVariant: String                  = "control"
    override val aiEnhanceDefaultSplit: Float               = 0.5f
    override val reviewTriggerThreshold: Int                = 5
    override val enableWatermarkByDefault: Boolean          = false
    override val experimentCohort: String                   = "control"
}

/**
 * Concrete data class implementation of [FeatureFlags] allowing dynamic overrides and copy.
 */
data class CustomFeatureFlags(
    override val sceneHintsEnabled: Boolean = true,
    override val nightModeBurstEnabled: Boolean = true,
    override val superResolutionEnabled: Boolean = true,
    override val aiEnhanceEnabled: Boolean = true,
    override val proRawEnabled: Boolean = true,
    override val advancedAiToolsEnabled: Boolean = false,
    override val diagnosticsEnabled: Boolean = false,
    override val adsEnabled: Boolean = true,
    override val activeAdProvider: String = "admob",
    override val adFrequencyIntervalSec: Long = 300L,
    override val adMinCapturesBetweenInterstitials: Int = 3,
    override val maxBurstCountOverride: Int? = null,
    override val nightExposureMaxMsOverride: Long? = null,
    override val superResolutionMaxScaleOverride: Float? = null,
    override val deviceSpecificOverridesJson: String? = null,
    override val promotionalCopy: String? = null,
    override val onboardingVariant: String = "control",
    override val aiEnhanceDefaultSplit: Float = 0.5f,
    override val reviewTriggerThreshold: Int = 5,
    override val enableWatermarkByDefault: Boolean = false,
    override val experimentCohort: String = "control",
) : FeatureFlags
