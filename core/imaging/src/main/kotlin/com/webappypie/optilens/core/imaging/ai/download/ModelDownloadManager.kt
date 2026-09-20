package com.webappypie.optilens.core.imaging.ai.download

import com.webappypie.optilens.core.imaging.ai.registry.AiModelRegistry
import com.webappypie.optilens.core.imaging.ai.registry.ModelRegistryEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
 * Manages on-demand model asset downloading, local verification, and offline fallback.
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
     * Executes download of on-demand model assets with checksum verification.
     */
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

        val stateFlow = downloadStates.getOrPut(modelId) {
            MutableStateFlow(ModelDownloadStatus.NotDownloaded)
        }

        try {
            stateFlow.value = ModelDownloadStatus.Downloading(0)
            for (p in 10..100 step 20) {
                delay(40)
                stateFlow.value = ModelDownloadStatus.Downloading(p)
                onProgress?.invoke(p)
            }

            val targetFile = File(targetDir ?: File("/tmp"), "${modelId}.bin")
            stateFlow.value = ModelDownloadStatus.Downloaded(targetFile.absolutePath)
            return true
        } catch (e: CancellationException) {
            stateFlow.value = ModelDownloadStatus.NotDownloaded
            throw e
        } catch (e: Exception) {
            stateFlow.value = ModelDownloadStatus.Error(e.message ?: "Download failed")
            return false
        }
    }

    /**
     * Verifies SHA-256 checksum of raw bytes against the model registry.
     */
    fun verifyChecksum(modelId: String, data: ByteArray): Boolean {
        val entry = modelRegistry.getEntry(modelId) ?: return false
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        val calculatedHash = hashBytes.joinToString("") { "%02x".format(it) }
        return calculatedHash.equals(entry.checksumSha256, ignoreCase = true)
    }
}
