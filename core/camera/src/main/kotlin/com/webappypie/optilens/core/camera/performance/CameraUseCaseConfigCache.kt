package com.webappypie.optilens.core.camera.performance

import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks bound CameraX use case configurations to avoid expensive and unnecessary
 * unbind/rebind cycles when minor camera parameters change.
 */
@Singleton
class CameraUseCaseConfigCache @Inject constructor() {

    data class UseCaseConfig(
        val lensFacing: LensFacing,
        val analysisEnabled: Boolean = true,
        val rawFormat: RawCaptureFormat? = null,
        val surfaceProviderHashCode: Int = 0,
        val targetAspectRatio: Int = 0,
    )

    private var activeConfig: UseCaseConfig? = null
    private var totalRebindRequests = 0
    private var skippedRebindRequests = 0

    val totalRequests: Int get() = totalRebindRequests
    val skippedCount: Int get() = skippedRebindRequests
    val rebindRatio: Float
        get() = if (totalRebindRequests == 0) 0f else (totalRebindRequests - skippedRebindRequests).toFloat() / totalRebindRequests

    /**
     * Determines whether the given new configuration requires a full CameraX unbind & rebind.
     */
    @Synchronized
    fun requiresRebind(newConfig: UseCaseConfig): Boolean {
        totalRebindRequests++
        val current = activeConfig
        if (current == null) {
            return true
        }

        val needsRebind = current.lensFacing != newConfig.lensFacing ||
                current.analysisEnabled != newConfig.analysisEnabled ||
                current.rawFormat != newConfig.rawFormat ||
                current.surfaceProviderHashCode != newConfig.surfaceProviderHashCode ||
                current.targetAspectRatio != newConfig.targetAspectRatio

        if (!needsRebind) {
            skippedRebindRequests++
        }
        return needsRebind
    }

    /**
     * Commits the active use case configuration once successfully bound to lifecycle.
     */
    @Synchronized
    fun commitActiveConfig(config: UseCaseConfig) {
        activeConfig = config
    }

    /**
     * Clears the cached configuration when camera use cases are unbound.
     */
    @Synchronized
    fun invalidate() {
        activeConfig = null
    }

    @Synchronized
    fun getActiveConfig(): UseCaseConfig? = activeConfig
}
