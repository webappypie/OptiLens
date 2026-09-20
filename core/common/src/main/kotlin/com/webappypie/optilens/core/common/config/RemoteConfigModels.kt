package com.webappypie.optilens.core.common.config

import kotlinx.serialization.Serializable

/**
 * Processing and computational capture overrides targeting specific device models.
 *
 * Used by Remote Config to gracefully throttle or adapt pipelines for devices
 * with specific thermal, memory, or ISP quirks.
 */
@Serializable
data class DeviceProcessingOverride(
    val deviceModelPattern: String = ".*",
    val maxBurstFrames: Int? = null,
    val maxNightExposureMs: Long? = null,
    val maxSrZoomScale: Float? = null,
    val disableZeroShutterLag: Boolean = false,
    val disabledModes: List<String> = emptyList(),
    val disabledQuirkIds: List<String> = emptyList(),
    val additionalQuirkIds: List<String> = emptyList(),
    val emergencyFallbackReason: String? = null,
)

/**
 * Container for parsed device-specific override rules.
 */
@Serializable
data class DeviceOverridesConfig(
    val rules: List<DeviceProcessingOverride> = emptyList(),
)
