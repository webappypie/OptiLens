package com.webappypie.optilens.core.camera.portrait

import kotlinx.serialization.Serializable

/**
 * Capture and processing pathway for portrait captures.
 */
@Serializable
enum class PortraitModeType {
    /** Hardware-accelerated OEM vendor Bokeh extension (CameraX ExtensionMode.BOKEH). */
    VENDOR_BOKEH,

    /** Native optical disc convolution bokeh and face-aware enhancement pipeline. */
    CUSTOM_SOFTWARE_BOKEH,
}

/**
 * User or system policy preference for portrait bokeh mode selection.
 */
@Serializable
enum class PortraitPolicyPreference {
    /** Intelligently select between vendor and custom software bokeh. */
    AUTO,

    /** Always prioritize OEM vendor bokeh extension when reported available. */
    PREFER_VENDOR,

    /** Always use custom software optical disc bokeh and face-aware processing. */
    PREFER_CUSTOM,
}

/**
 * Virtual optical aperture stops simulating physical lens depth of field (DoF).
 */
@Serializable
enum class PortraitAperture(
    val label: String,
    val fNumber: Float,
    val blurRadiusFactor: Float,
) {
    F1_4("f/1.4", 1.4f, 1.00f),
    F2_0("f/2.0", 2.0f, 0.75f),
    F2_8("f/2.8", 2.8f, 0.50f),
    F4_0("f/4.0", 4.0f, 0.30f),
    F5_6("f/5.6", 5.6f, 0.15f),
    F8_0("f/8.0", 8.0f, 0.00f);

    companion object {
        val DEFAULT = F2_8

        fun fromBlurFactor(factor: Float): PortraitAperture {
            return entries.minByOrNull { kotlin.math.abs(it.blurRadiusFactor - factor) } ?: DEFAULT
        }
    }
}

/**
 * Execution plan formulated by [PortraitPolicyEngine].
 */
@Serializable
data class PortraitExecutionPlan(
    val mode: PortraitModeType,
    val aperture: PortraitAperture = PortraitAperture.DEFAULT,
    val faceCount: Int = 0,
    val faceExposureCompensationEv: Float = 0.0f,
    val isBacklitScene: Boolean = false,
    val skinSmoothingStrength: Float = 0.25f,
    val protectEyeDetails: Boolean = true,
    val reason: String,
)
