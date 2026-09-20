package com.webappypie.optilens.core.imaging.ai.tier

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware capability tiers for AI processing workload throttling.
 */
enum class AiDeviceTier {
    LOW_TIER,
    MID_TIER,
    HIGH_TIER;
}

/**
 * Gating governor evaluating device RAM, CPU cores, and hardware limits to optimize AI execution.
 */
@Singleton
class AiDeviceTierGate @Inject constructor() {

    fun resolveTier(
        totalRamMb: Long = Runtime.getRuntime().maxMemory() / (1024 * 1024),
        availableProcessors: Int = Runtime.getRuntime().availableProcessors(),
    ): AiDeviceTier {
        return when {
            totalRamMb >= 512L && availableProcessors >= 8 -> AiDeviceTier.HIGH_TIER
            totalRamMb >= 256L && availableProcessors >= 4 -> AiDeviceTier.MID_TIER
            else -> AiDeviceTier.LOW_TIER
        }
    }

    fun getMaxUpscaleFactor(tier: AiDeviceTier): Int {
        return when (tier) {
            AiDeviceTier.LOW_TIER -> 2
            AiDeviceTier.MID_TIER -> 4
            AiDeviceTier.HIGH_TIER -> 4
        }
    }

    fun getOptimalTileSize(tier: AiDeviceTier): Int {
        return when (tier) {
            AiDeviceTier.LOW_TIER -> 256
            AiDeviceTier.MID_TIER -> 512
            AiDeviceTier.HIGH_TIER -> 1024
        }
    }

    fun getMaxDeblurIterations(tier: AiDeviceTier): Int {
        return when (tier) {
            AiDeviceTier.LOW_TIER -> 6
            AiDeviceTier.MID_TIER -> 12
            AiDeviceTier.HIGH_TIER -> 20
        }
    }
}
