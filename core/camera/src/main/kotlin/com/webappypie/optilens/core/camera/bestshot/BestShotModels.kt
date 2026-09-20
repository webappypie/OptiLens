package com.webappypie.optilens.core.camera.bestshot

/**
 * An individual burst candidate evaluated by the Best Shot ranking engine.
 *
 * All candidates represent authentic, physical burst frames.
 * Expression synthesis or synthetic face inpainting is strictly prohibited.
 *
 * @param index Frame index in the acquisition sequence.
 * @param uri Local file/MediaStore URI if persisted to disk.
 * @param sharpnessScore Tenengrad gradient energy metric (0.0 to 100.0).
 * @param motionScore Physical and photometric stability metric (0.0 to 100.0).
 * @param exposureScore Radiometric balance and dynamic range metric (0.0 to 100.0).
 * @param eyeOpenScore Facial eye-open confidence (0.0 to 1.0), or null if no faces detected.
 * @param compositeScore Weighted multi-criteria ranking score (0.0 to 100.0).
 * @param ranking Ordinal rank (1 = top recommendation, 2 = runner-up, etc.).
 * @param isBest True if this candidate is the system-recommended top pick.
 * @param isAlternate True if this candidate is an alternate viable option.
 * @param timestampMs Frame capture timestamp.
 */
data class BestShotCandidate(
    val index: Int,
    val uri: String? = null,
    val sharpnessScore: Float = 0.0f,
    val motionScore: Float = 0.0f,
    val exposureScore: Float = 0.0f,
    val eyeOpenScore: Float? = null,
    val compositeScore: Float = 0.0f,
    val ranking: Int = 1,
    val isBest: Boolean = false,
    val isAlternate: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
)

/**
 * Result bundle produced by [BestShotEngine] after scoring and ranking an acquisition burst.
 *
 * @param candidates All scored burst candidates ordered by acquisition index or rank.
 * @param bestIndex Index of the system-recommended top shot.
 * @param selectedIndex Index of the currently selected keeper (starts as [bestIndex], supports manual override).
 * @param hasFaces Whether confident faces were identified during the burst evaluation.
 * @param timestampMs Generation timestamp.
 */
data class BestShotResult(
    val candidates: List<BestShotCandidate>,
    val bestIndex: Int,
    val selectedIndex: Int = bestIndex,
    val hasFaces: Boolean = false,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    val selectedCandidate: BestShotCandidate?
        get() = candidates.firstOrNull { it.index == selectedIndex } ?: candidates.firstOrNull()

    val recommendedCandidate: BestShotCandidate?
        get() = candidates.firstOrNull { it.index == bestIndex } ?: candidates.firstOrNull()

    val alternates: List<BestShotCandidate>
        get() = candidates.filter { it.index != selectedIndex }

    /**
     * Creates a new [BestShotResult] overriding the selected keeper frame to [newSelectedIndex].
     */
    fun withManualOverride(newSelectedIndex: Int): BestShotResult {
        require(candidates.any { it.index == newSelectedIndex }) {
            "Candidate index $newSelectedIndex not found in burst candidates"
        }
        return copy(selectedIndex = newSelectedIndex)
    }
}
