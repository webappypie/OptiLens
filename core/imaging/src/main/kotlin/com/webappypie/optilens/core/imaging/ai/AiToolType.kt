package com.webappypie.optilens.core.imaging.ai

/**
 * Permitted Advanced AI tool operations.
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
        description = "Analyzes motion and defocus blur to sharpen details using regularized deconvolution.",
        requiresDisclosure = false,
        isGatedByProPack = true,
    ),
    INPAINTING(
        id = "inpainting",
        displayName = "Object Cleanup",
        description = "Removes background distractions and synthesizes realistic surrounding textures.",
        requiresDisclosure = true,
        disclosureText = "AI Cleanup applied: Selected pixels were synthesized from surrounding image textures. Reconstructed detail is not guaranteed historical truth.",
        isGatedByProPack = true,
    ),
    REFLECTION_REDUCTION(
        id = "reflection",
        displayName = "Reflection Cleaner",
        description = "Attenuates glass reflections, surface glares, and flares from transparent barriers.",
        requiresDisclosure = false,
        isGatedByProPack = true,
    ),
    UPSCALE(
        id = "upscale",
        displayName = "AI Upscale",
        description = "Increases image resolution by 2x or 4x with edge-directed high frequency reconstruction.",
        requiresDisclosure = false,
        isGatedByProPack = true,
    ),
    RESTORATION(
        id = "restoration",
        displayName = "Photo Restore",
        description = "Removes scratches and creases, balances faded sepia/yellow color casts, and revives contrast.",
        requiresDisclosure = true,
        disclosureText = "AI Restoration applied: Colors, tones, and crease areas have been reconstructed algorithmically. Reconstructed detail is not guaranteed historical truth.",
        isGatedByProPack = true,
    );

    companion object {
        fun fromId(id: String): AiToolType = entries.firstOrNull { it.id == id } ?: DEBLUR
    }
}
