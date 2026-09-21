package com.webappypie.optilens.core.common.experiment

import com.webappypie.optilens.core.common.config.RemoteConfigRepository
import com.webappypie.optilens.core.common.feature.FeatureFlags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class OnboardingVariant {
    CONTROL,
    FEATURE_FOCUS,
    SPEED_FOCUS;

    companion object {
        fun fromKey(key: String): OnboardingVariant = when (key.lowercase()) {
            "feature_focus" -> FEATURE_FOCUS
            "speed_focus" -> SPEED_FOCUS
            else -> CONTROL
        }
    }
}

enum class WatermarkExperiment {
    OFF_BY_DEFAULT,
    ON_BY_DEFAULT;

    companion object {
        fun fromEnabled(enabled: Boolean): WatermarkExperiment =
            if (enabled) ON_BY_DEFAULT else OFF_BY_DEFAULT
    }
}

data class ActiveExperiments(
    val cohort: String,
    val onboardingVariant: OnboardingVariant,
    val watermarkExperiment: WatermarkExperiment,
    val aiEnhanceDefaultSplit: Float,
    val reviewTriggerThreshold: Int,
)

/**
 * Manages safe, non-sensitive A/B experimentation without external user tracking or PII.
 *
 * Supports deterministic client-side bucketing and Remote Config driven overrides.
 */
@Singleton
class ExperimentationManager @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
) {
    /**
     * Resolves active experiments dynamically as feature flags / Remote Config change.
     */
    val activeExperiments: Flow<ActiveExperiments> =
        remoteConfigRepository.featureFlags.map { flags ->
            resolveExperiments(flags)
        }

    /**
     * Current snapshot of active experiments.
     */
    fun currentSnapshot(): ActiveExperiments {
        return resolveExperiments(remoteConfigRepository.featureFlags.value)
    }

    /**
     * Deterministically assigns an anonymous cohort ("control", "variant_a", "variant_b")
     * based on an anonymous seed (e.g. app install hash), ensuring consistent assignment
     * across app sessions without storing or transmitting personal data.
     */
    fun assignCohortDeterministically(anonymousSeed: String): String {
        val hash = abs(anonymousSeed.hashCode())
        return when (hash % 3) {
            0 -> "control"
            1 -> "variant_a"
            else -> "variant_b"
        }
    }

    private fun resolveExperiments(flags: FeatureFlags): ActiveExperiments {
        return ActiveExperiments(
            cohort = flags.experimentCohort,
            onboardingVariant = OnboardingVariant.fromKey(flags.onboardingVariant),
            watermarkExperiment = WatermarkExperiment.fromEnabled(flags.enableWatermarkByDefault),
            aiEnhanceDefaultSplit = flags.aiEnhanceDefaultSplit.coerceIn(0.0f, 1.0f),
            reviewTriggerThreshold = flags.reviewTriggerThreshold.coerceAtLeast(1),
        )
    }
}
