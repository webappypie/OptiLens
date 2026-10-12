package com.webappypie.optilens.core.imaging.ai

/**
 * Photo editing tools. All are first-party classical image-processing algorithms that run on
 * device; none uses a trained ML model (see AiModelRegistry), so names and descriptions must not
 * claim AI inference or super-resolution.
 */
enum class AiToolType(
    val id: String,
    val displayName: String,
    val description: String,
    val requiresDisclosure: Boolean,
    val disclosureText: String? = null,
    val isGatedByProPack: Boolean = true,
) {
    DEBLUR(
        id = "deblur",
        displayName = "Deblur",
        description = "Estimates motion or focus blur and sharpens with regularized deconvolution. Works best on mild blur.",
        requiresDisclosure = false,
        isGatedByProPack = true,
    ),
    INPAINTING(
        id = "inpainting",
        displayName = "Object Cleanup",
        description = "Fills the area you brush using the surrounding pixels. Best for small distractions on simple backgrounds.",
        requiresDisclosure = true,
        disclosureText = "Object Cleanup applied: brushed pixels were filled in from surrounding image content. Reconstructed detail is not guaranteed historical truth.",
        isGatedByProPack = true,
    ),
    REFLECTION_REDUCTION(
        id = "reflection",
        displayName = "Reflection Cleaner",
        description = "Reduces the low-contrast haze that window glass and glare add across a photo.",
        requiresDisclosure = false,
        isGatedByProPack = true,
    ),
    UPSCALE(
        id = "upscale",
        displayName = "Upscale",
        description = "Enlarges the photo 2x or 4x with edge-directed interpolation. It does not add detail that was not captured.",
        requiresDisclosure = false,
        isGatedByProPack = true,
    ),
    RESTORATION(
        id = "restoration",
        displayName = "Photo Restore",
        description = "Repairs thin scratches and creases, corrects faded or yellowed colour casts, and restores contrast.",
        requiresDisclosure = true,
        disclosureText = "Photo Restore applied: scratch areas were filled in and colours were adjusted algorithmically; reconstructed detail is not guaranteed historical truth.",
        isGatedByProPack = true,
    );

    companion object {
        fun fromId(id: String): AiToolType = entries.firstOrNull { it.id == id } ?: DEBLUR
    }
}
