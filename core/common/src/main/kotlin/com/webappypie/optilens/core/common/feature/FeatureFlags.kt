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
}

/**
 * Conservative local defaults — always compile-time safe.
 * Used when Remote Config is not yet initialised or is unavailable.
 */
object LocalFeatureFlags : FeatureFlags {
    override val sceneHintsEnabled: Boolean        = true
    override val nightModeBurstEnabled: Boolean    = true
    override val superResolutionEnabled: Boolean   = true
    override val aiEnhanceEnabled: Boolean         = true
    override val proRawEnabled: Boolean            = true
    override val advancedAiToolsEnabled: Boolean   = false  // gated until Phase 18
    override val diagnosticsEnabled: Boolean       = false  // enabled per-build via BuildInfo
}
