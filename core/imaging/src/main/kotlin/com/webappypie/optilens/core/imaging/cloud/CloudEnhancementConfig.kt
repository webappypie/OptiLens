package com.webappypie.optilens.core.imaging.cloud

/**
 * Operations eligible for selective Cloud AI processing for paid/Pro users.
 */
enum class CloudOperation(val displayName: String) {
    /** Generative super-resolution for zoomed and low-res photos */
    SUPER_RESOLUTION("AI Zoom & Super-Resolution"),

    /** Deep blind deblurring for heavy camera shake or fast motion */
    DEBLUR("Deep Motion Deblur"),

    /** Complex dynamic range radiance synthesis for challenging scenes */
    HDR_ENHANCE("Ultra Dynamic Range"),

    /** Neural face & scratch recovery for old/damaged photographs */
    RESTORATION("Neural Restoration");
}

/**
 * Strict cost-control and bandwidth optimization configuration for Cloud AI.
 */
data class CloudEnhancementConfig(
    val maxMonthlyQuota: Int = 20,
    val maxUploadDimensionPx: Int = 1280,
    val compressionQuality: Int = 78,
    val enableDiskCache: Boolean = true,
    val timeoutSeconds: Long = 30L,
) {
    companion object {
        val DEFAULT = CloudEnhancementConfig()
    }
}
