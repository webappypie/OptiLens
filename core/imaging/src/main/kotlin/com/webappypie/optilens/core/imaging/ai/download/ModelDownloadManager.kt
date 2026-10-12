package com.webappypie.optilens.core.imaging.ai.download

import com.webappypie.optilens.core.imaging.ai.registry.AiModelRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status of an on-demand model download.
 */
sealed interface ModelDownloadStatus {
    data object NotDownloaded : ModelDownloadStatus
    data class Downloading(val progressPercent: Int) : ModelDownloadStatus
    data class Downloaded(val localPath: String) : ModelDownloadStatus
    data class Error(val message: String) : ModelDownloadStatus
}

/**
 * Tracks availability of tool engines and verifies model-file checksums.
 *
 * All current engines are bundled algorithms. No remote model source is configured, so
 * non-bundled models are reported as unavailable rather than "downloaded".
 */
@Singleton
class ModelDownloadManager @Inject constructor(
    private val modelRegistry: AiModelRegistry,
) {

    private val downloadStates = mutableMapOf<String, MutableStateFlow<ModelDownloadStatus>>()

    fun getDownloadStatus(modelId: String): StateFlow<ModelDownloadStatus> {
        val entry = modelRegistry.getEntry(modelId)
        val initial = if (entry?.isBundledLocally == true) {
            ModelDownloadStatus.Downloaded(localPath = "bundled://${entry.modelId}")
        } else {
            ModelDownloadStatus.NotDownloaded
        }
        return downloadStates.getOrPut(modelId) { MutableStateFlow(initial) }.asStateFlow()
    }

    fun isModelAvailableLocally(modelId: String): Boolean {
        val entry = modelRegistry.getEntry(modelId) ?: return false
        if (entry.isBundledLocally) return true
        val state = downloadStates[modelId]?.value
        return state is ModelDownloadStatus.Downloaded
    }

    /**
     * Makes a model available locally. Bundled engines are available immediately.
     * There is no download source yet, so any non-bundled model fails with an explicit error
     * (no simulated progress, no placeholder file).
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun downloadModel(
        modelId: String,
        targetDir: File? = null,
        onProgress: ((Int) -> Unit)? = null,
    ): Boolean {
        val entry = modelRegistry.getEntry(modelId) ?: return false
        if (entry.isBundledLocally) {
            onProgress?.invoke(100)
            return true
        }
        downloadStates.getOrPut(modelId) { MutableStateFlow(ModelDownloadStatus.NotDownloaded) }.value =
            ModelDownloadStatus.Error(NO_DOWNLOAD_SOURCE)
        return false
    }

    /**
     * Verifies SHA-256 checksum of raw bytes against the model registry. Always false for
     * engines without a model file (no checksum to verify against).
     */
    fun verifyChecksum(modelId: String, data: ByteArray): Boolean {
        val entry = modelRegistry.getEntry(modelId) ?: return false
        val expected = entry.checksumSha256 ?: return false
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        val calculatedHash = hashBytes.joinToString("") { "%02x".format(it) }
        return calculatedHash.equals(expected, ignoreCase = true)
    }

    companion object {
        const val NO_DOWNLOAD_SOURCE = "No verified download source is configured for this model."
    }
}
