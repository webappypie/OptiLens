package com.webappypie.optilens.core.imaging.cloud

import android.graphics.Bitmap
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.imaging.enhance.EnhancementConfig
import com.webappypie.optilens.core.imaging.enhance.OnDeviceEnhancementEngine
import com.webappypie.optilens.core.logging.AppLogger
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of the hybrid on-device / cloud enhancement pipeline.
 */
data class HybridEnhancementResult(
    val bitmap: Bitmap,
    val isCloudEnhanced: Boolean,
    val operationUsed: String,
    val remainingMonthlyQuota: Int,
)

/**
 * Orchestrator implementing the 90% On-Device / 10% Cloud AI hybrid architecture.
 *
 * Rules:
 * 1. 90% of all processing executes strictly on-device using [OnDeviceEnhancementEngine].
 * 2. Cloud AI is invoked ONLY for paid/Pro users, on specific high-impact operations
 *    (e.g., zoom super-resolution, deep restoration), when monthly quota permits.
 * 3. Graceful fallback: Any network or server issue immediately falls back to the high-quality
 *    on-device result with zero UI disruption or crash.
 */
@Singleton
class HybridEnhancementCoordinator @Inject constructor(
    private val onDeviceEngine: OnDeviceEnhancementEngine,
    private val cloudRepository: CloudEnhancementRepository,
    private val usageTracker: CloudUsageTracker,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {
    companion object {
        private const val TAG = "HybridCoordinator"
    }

    suspend fun process(
        bitmap: Bitmap,
        isPro: Boolean,
        requestCloud: Boolean = false,
        operation: CloudOperation = CloudOperation.HDR_ENHANCE,
        config: EnhancementConfig = EnhancementConfig.BALANCED,
        onProgress: ((Float, String) -> Unit)? = null,
    ): HybridEnhancementResult = withContext(dispatchers.default) {
        onProgress?.invoke(0.1f, "Optimizing image on device...")

        // Step 1: Always execute premium on-device processing first
        val onDevicePolished = onDeviceEngine.enhance(
            bitmap = bitmap,
            config = config,
            onProgress = { p -> onProgress?.invoke(0.1f + p * 0.5f, "Enhancing details on device...") }
        )

        val remainingQuota = usageTracker.remainingQuota()

        // Step 2: Evaluate whether to invoke Cloud AI (10% path for paid users)
        val shouldInvokeCloud = isPro && requestCloud && usageTracker.canInvokeCloud()

        if (!shouldInvokeCloud) {
            onProgress?.invoke(1.0f, "Completed")
            return@withContext HybridEnhancementResult(
                bitmap = onDevicePolished,
                isCloudEnhanced = false,
                operationUsed = "On-Device Neural Engine",
                remainingMonthlyQuota = remainingQuota,
            )
        }

        // Step 3: Cloud AI enhancement for paid users
        onProgress?.invoke(0.70f, "Applying Premium Cloud AI...")
        logger.i(TAG, "Triggering Cloud AI for operation: $operation (Quota remaining: $remainingQuota)")

        val cloudResult = cloudRepository.enhanceWithCloud(
            bitmap = onDevicePolished,
            operation = operation,
        )

        if (cloudResult != null) {
            val updatedQuota = usageTracker.recordCloudInvocation()
            val newRemaining = usageTracker.remainingQuota()
            onProgress?.invoke(1.0f, "Completed")
            logger.i(TAG, "Cloud AI enhancement succeeded. Remaining quota: $newRemaining")
            HybridEnhancementResult(
                bitmap = cloudResult,
                isCloudEnhanced = true,
                operationUsed = operation.displayName,
                remainingMonthlyQuota = newRemaining,
            )
        } else {
            // Graceful fallback to the on-device result if network/cloud fails
            logger.w(TAG, "Cloud enhancement unavailable; falling back to on-device result")
            onProgress?.invoke(1.0f, "Completed")
            HybridEnhancementResult(
                bitmap = onDevicePolished,
                isCloudEnhanced = false,
                operationUsed = "On-Device Neural Engine (Offline Fallback)",
                remainingMonthlyQuota = remainingQuota,
            )
        }
    }
}
