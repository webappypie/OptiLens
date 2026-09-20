package com.webappypie.optilens.core.camera.model

/**
 * Recognized baseline scene types for the on-device intelligence loop.
 */
enum class SceneType(val displayName: String) {
    GENERAL("Auto"),
    PORTRAIT("Portrait"),
    PET("Pet"),
    FOOD("Food"),
    DOCUMENT("Document"),
    NATURE("Nature"),
    LOW_LIGHT("Night"),
    INDOOR("Indoor"),
    OUTDOOR("Outdoor"),
    SKY("Sky"),
    PLANT("Plant"),
    WILDLIFE("Wildlife"),
    MOON("Moon");
}

/**
 * Real-time scene classification result emitted by the intelligence loop.
 *
 * @param primaryScene The dominant classified scene.
 * @param secondaryScene A secondary scene candidate when multi-class features exist (e.g. SKY + NATURE).
 * @param confidence Confidence score for the primary classification (0.0 to 1.0).
 * @param stabilityScore Temporal stability score (0.0 to 1.0) derived from hysteresis filter.
 * @param timestampMs Frame analysis timestamp in milliseconds.
 */
data class SceneClassification(
    val primaryScene: SceneType = SceneType.GENERAL,
    val secondaryScene: SceneType? = null,
    val confidence: Float = 1.0f,
    val stabilityScore: Float = 1.0f,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    companion object {
        val DEFAULT = SceneClassification()
    }
}
