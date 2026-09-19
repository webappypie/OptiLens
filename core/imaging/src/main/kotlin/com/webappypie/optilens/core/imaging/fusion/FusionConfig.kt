package com.webappypie.optilens.core.imaging.fusion

/**
 * Tuning parameters governing multi-frame fusion, dynamic range reconstruction,
 * tone mapping, and color grading.
 */
data class FusionConfig(
    val colorProfile: ColorProfile = ColorProfile.DEFAULT,
    val enableDenoise: Boolean = true,
    val enableHdr: Boolean = true,
    val enableHighlightRollOff: Boolean = true,
    val enableShadowRecovery: Boolean = true,
    val shadowLiftAmount: Float = 0.35f,
    val highlightKnee: Float = 0.72f,
    val exposureCompensation: Float = 1.0f,
    val enableAwb: Boolean = true,
    val awbGain: Float = 0.40f,
    val protectSkinTones: Boolean = true,
    val sharpnessBoost: Float = 0.25f,
    val enableNightHighlightProtection: Boolean = false,
    val enableChromaCleanup: Boolean = false,
    val conservativeSharpening: Boolean = false,
)
