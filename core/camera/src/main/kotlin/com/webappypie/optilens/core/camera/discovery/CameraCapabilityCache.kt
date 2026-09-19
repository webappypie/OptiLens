package com.webappypie.optilens.core.camera.discovery

import android.content.Context
import android.os.Build
import com.webappypie.optilens.core.camera.model.CameraCapabilityProfile
import com.webappypie.optilens.core.common.build.BuildInfo
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class CachedProfileEnvelope(
    val fingerprint: String,
    val appVersionCode: Int,
    val cachedAtMs: Long,
    val profile: CameraCapabilityProfile,
)

/**
 * Caches non-sensitive camera capability discovery profiles on disk.
 *
 * Automatically invalidates when the device firmware (Build.FINGERPRINT)
 * or application version changes.
 */
@Singleton
class CameraCapabilityCache internal constructor(
    private val context: Context?,
    private val buildInfo: BuildInfo,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
    private val testDir: File? = null,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        buildInfo: BuildInfo,
        dispatchers: AppDispatchers,
        logger: AppLogger,
    ) : this(context, buildInfo, dispatchers, logger, null)

    constructor(
        testDir: File,
        buildInfo: BuildInfo,
        dispatchers: AppDispatchers,
        logger: AppLogger,
    ) : this(null, buildInfo, dispatchers, logger, testDir)

    /** Optional test-only directory override */
    var customCacheDir: File? = testDir

    /** Optional test-only fingerprint override */
    var customFingerprint: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val cacheFile: File
        get() {
            val dir = customCacheDir ?: context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "optilens_cache")
            if (!dir.exists()) dir.mkdirs()
            return File(dir, CACHE_FILE_NAME)
        }

    suspend fun getCachedProfile(): CameraCapabilityProfile? = withContext(dispatchers.io) {
        try {
            val file = cacheFile
            if (!file.exists()) {
                logger.d(TAG, "No cached capability profile found.")
                return@withContext null
            }

            val text = file.readText()
            val envelope = json.decodeFromString<CachedProfileEnvelope>(text)

            // Validate firmware and app version invalidation keys
            val currentFingerprint = customFingerprint ?: Build.FINGERPRINT ?: "unknown"
            val currentVersion = buildInfo.versionCode

            if (envelope.fingerprint != currentFingerprint || envelope.appVersionCode != currentVersion) {
                logger.i(TAG, "Capability cache invalidated: fingerprint or app version changed.")
                file.delete()
                return@withContext null
            }

            logger.d(TAG, "Capability cache hit. Loaded profile discovered at ${envelope.cachedAtMs}")
            return@withContext envelope.profile
        } catch (e: Exception) {
            logger.w(TAG, "Failed to load cached camera capabilities: ${e.message}")
            try { cacheFile.delete() } catch (_: Exception) {}
            return@withContext null
        }
    }

    suspend fun saveProfile(profile: CameraCapabilityProfile) = withContext(dispatchers.io) {
        try {
            val envelope = CachedProfileEnvelope(
                fingerprint = customFingerprint ?: Build.FINGERPRINT ?: "unknown",
                appVersionCode = buildInfo.versionCode,
                cachedAtMs = System.currentTimeMillis(),
                profile = profile,
            )
            val text = json.encodeToString(envelope)
            cacheFile.writeText(text)
            logger.d(TAG, "Camera capability profile successfully cached (${text.length} bytes).")
        } catch (e: Exception) {
            logger.w(TAG, "Failed saving capability profile to cache: ${e.message}")
        }
    }

    suspend fun clearCache() = withContext(dispatchers.io) {
        try {
            if (cacheFile.exists()) {
                cacheFile.delete()
                logger.d(TAG, "Camera capability cache cleared.")
            }
        } catch (e: Exception) {
            logger.w(TAG, "Failed to clear capability cache: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "CameraCapabilityCache"
        private const val CACHE_FILE_NAME = "optilens_camera_capabilities.json"
    }
}
