package com.webappypie.optilens.core.imaging.fusion

/**
 * Creative color grading profiles applied downstream of multi-frame fusion.
 */
enum class ColorProfile(val id: Int) {
    /** Balanced, natural rendering with authentic tonal fidelity and slight vibrancy pop (+5%). */
    DEFAULT(0),

    /** Strictly calibrated, filmic, true-to-life muted saturation (-8%) with gentle contrast. */
    NATURAL(1),

    /** Punchy, saturated rendering (+25%) with intelligent skin protection and gamut limiting. */
    VIVID(2);
}
