package com.webappypie.optilens.core.camera.discovery

import com.webappypie.optilens.core.camera.CameraCapability
import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.camera.toLegacyCameraCapability
import com.webappypie.optilens.core.logging.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing device camera capabilities.
 *
 * Coordinates instant cached startup with background capability detection.
 */
@Singleton
class CameraCapabilityRepository @Inject constructor(
    private val detector: CameraCapabilityDetector,
    private val cache: CameraCapabilityCache,
    private val logger: AppLogger,
) {
    private val _capabilityProfile = MutableStateFlow<CameraCapabilityProfile?>(null)
    val capabilityProfile: StateFlow<CameraCapabilityProfile?> = _capabilityProfile.asStateFlow()

    /** Legacy capability stream for backward compatibility with [com.webappypie.optilens.core.camera.CameraController]. */
    val legacyCapability: Flow<CameraCapability> = _capabilityProfile
        .filterNotNull()
        .map { it.toLegacyCameraCapability() }

    private val mutex = Mutex()

    /**
     * Retrieves the capability profile, checking memory first, disk cache second,
     * and performing hardware discovery if no cache is available.
     */
    suspend fun getOrDiscoverCapabilities(): CameraCapabilityProfile = mutex.withLock {
        _capabilityProfile.value?.let { return it }

        val cached = cache.getCachedProfile()
        if (cached != null) {
            logger.d(TAG, "Using cached camera capability profile.")
            _capabilityProfile.value = cached
            return cached
        }

        logger.i(TAG, "Discovering camera capabilities from hardware HAL...")
        val fresh = detector.detectCapabilities()
        cache.saveProfile(fresh)
        _capabilityProfile.value = fresh
        return fresh
    }

    /**
     * Forces a fresh hardware query, bypassing disk cache, and updates cache and state flow.
     */
    suspend fun refreshCapabilities(): CameraCapabilityProfile = mutex.withLock {
        logger.i(TAG, "Forced refresh of camera capabilities...")
        val fresh = detector.detectCapabilities()
        cache.saveProfile(fresh)
        _capabilityProfile.value = fresh
        return fresh
    }

    companion object {
        private const val TAG = "CameraCapabilityRepository"
    }
}
