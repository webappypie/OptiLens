package com.webappypie.optilens.core.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gatekeeper controlling in-app review prompt eligibility.
 *
 * Compliance & Policy Rules:
 * - Strictly enforces prompting ONLY after demonstrably positive user experiences.
 * - Zero fake reviews, fake incentives, or deceptive countdowns.
 * - Minimum 5 successful captures or 3 AI Enhance keeps before first prompt.
 * - Minimum 2 days of usage since initial install.
 * - 30-day cooldown between review prompt presentations.
 * - Suppressed immediately if a pipeline failure occurred within the last hour.
 */
@Singleton
class ReviewEligibilityGate @Inject constructor(
    private val appSettings: AppSettings,
) {

    companion object {
        const val MIN_CAPTURES_REQUIRED = 5
        const val MIN_AI_KEEPS_REQUIRED = 3
        const val MIN_INSTALL_AGE_MS = 2L * 24 * 60 * 60 * 1000 // 2 days
        const val PROMPT_COOLDOWN_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        const val FAILURE_COOLDOWN_MS = 60L * 60 * 1000 // 1 hour
    }

    /**
     * Observes whether the user currently qualifies for an in-app review prompt.
     */
    fun isEligible(nowMs: Long = System.currentTimeMillis()): Flow<Boolean> {
        return combine(
            appSettings.hasUserReviewed,
            appSettings.successfulCapturesCount,
            appSettings.aiEnhanceKeptCount,
            combine(
                appSettings.firstInstallTimestamp,
                appSettings.lastReviewPromptTimestamp,
                appSettings.recentFailureTimestamp,
            ) { installTime, lastPrompt, lastFailure ->
                Triple(installTime, lastPrompt, lastFailure)
            }
        ) { hasReviewed, captures, aiKeeps, timestamps ->
            val (installTime, lastPrompt, lastFailure) = timestamps
            if (hasReviewed) return@combine false

            val hasPositiveExperience = (captures >= MIN_CAPTURES_REQUIRED) || (aiKeeps >= MIN_AI_KEEPS_REQUIRED)
            if (!hasPositiveExperience) return@combine false

            // Ensure app has been installed for at least 2 days
            val installAge = if (installTime > 0L) nowMs - installTime else 0L
            if (installAge < MIN_INSTALL_AGE_MS) return@combine false

            // Ensure 30-day cooldown since previous prompt
            val promptAge = if (lastPrompt > 0L) nowMs - lastPrompt else Long.MAX_VALUE
            if (promptAge < PROMPT_COOLDOWN_MS) return@combine false

            // Ensure no recent pipeline crash / failure in last hour
            val failureAge = if (lastFailure > 0L) nowMs - lastFailure else Long.MAX_VALUE
            if (failureAge < FAILURE_COOLDOWN_MS) return@combine false

            true
        }
    }

    /**
     * One-shot snapshot check of review eligibility.
     */
    suspend fun checkEligibilityNow(nowMs: Long = System.currentTimeMillis()): Boolean {
        // Initialize install timestamp if first check
        val installTime = appSettings.firstInstallTimestamp.first()
        if (installTime == 0L) {
            appSettings.setFirstInstallTimestamp(nowMs)
            return false
        }
        return isEligible(nowMs).first()
    }

    /**
     * Records a successful capture experience.
     */
    suspend fun recordSuccessfulCapture() {
        appSettings.incrementSuccessfulCaptures()
    }

    /**
     * Records an AI enhance keep decision.
     */
    suspend fun recordAiEnhanceKept() {
        appSettings.recordAiEnhanceOutcome(kept = true)
    }

    /**
     * Records a pipeline failure, triggering a 1-hour review suppression window.
     */
    suspend fun recordProcessingFailure(nowMs: Long = System.currentTimeMillis()) {
        appSettings.setRecentFailureTimestamp(nowMs)
    }

    /**
     * Marks that the review dialog was presented, resetting the 30-day cooldown.
     */
    suspend fun markReviewPromptShown(nowMs: Long = System.currentTimeMillis()) {
        appSettings.setLastReviewPromptTimestamp(nowMs)
    }

    /**
     * Marks that the user has reviewed, permanently disabling future prompts.
     */
    suspend fun markUserReviewed() {
        appSettings.setHasUserReviewed(true)
    }
}
