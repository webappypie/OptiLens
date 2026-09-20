package com.webappypie.optilens.core.camera.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage space availability tier.
 */
enum class StorageStatus {
    NORMAL,
    LOW,
    CRITICAL;
}

/**
 * Live storage state telemetry.
 */
data class StorageState(
    val freeBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val status: StorageStatus = StorageStatus.NORMAL,
    val cacheSizeBytes: Long = 0L,
) {
    val freeMegabytes: Long get() = freeBytes / (1024L * 1024L)
    val totalGigabytes: Float get() = totalBytes / (1024f * 1024f * 1024f)

    val formattedFree: String
        get() {
            val gb = freeBytes / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.1f GB free", gb)
            } else {
                String.format(Locale.US, "%d MB free", freeMegabytes)
            }
        }

    val formattedCacheSize: String
        get() {
            val mb = cacheSizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                val kb = cacheSizeBytes / 1024.0
                String.format(Locale.US, "%.0f KB", kb)
            }
        }

    val isCaptureAllowed: Boolean get() = status != StorageStatus.CRITICAL
}

/**
 * Proactive monitor interface for storage space, low memory warnings, and cache lifecycle.
 */
interface StorageMonitor {
    val storageState: Flow<StorageState>
    suspend fun refreshStorageState(): StorageState
    suspend fun calculateCacheSizeBytes(): Long
    suspend fun cleanExpiredCache(olderThanMs: Long = DEFAULT_EXPIRATION_MS): Long
    suspend fun clearCache(): Long

    companion object {
        const val LOW_THRESHOLD_MB = 500L
        const val CRITICAL_THRESHOLD_MB = 100L
        const val DEFAULT_EXPIRATION_MS = 24L * 60L * 60L * 1000L // 24 hours
    }
}

/**
 * Production implementation of [StorageMonitor] using [StatFs] and scoped directories.
 */
@Singleton
class StorageMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : StorageMonitor {

    private val _storageState = MutableStateFlow(StorageState())
    override val storageState: Flow<StorageState> = _storageState.asStateFlow()

    init {
        refreshStorageStateSync()
    }

    /**
     * Evaluates free and total space on the public storage partition.
     */
    override suspend fun refreshStorageState(): StorageState = withContext(dispatchers.io) {
        val dcimPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val targetPath = if (dcimPath.exists()) dcimPath else context.filesDir

        val stat = try {
            StatFs(targetPath.absolutePath)
        } catch (e: Exception) {
            logger.w(TAG, "Failed reading StatFs: ${e.message}")
            StatFs(context.filesDir.absolutePath)
        }

        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val freeMb = freeBytes / (1024L * 1024L)

        val status = when {
            freeMb < StorageMonitor.CRITICAL_THRESHOLD_MB -> StorageStatus.CRITICAL
            freeMb < StorageMonitor.LOW_THRESHOLD_MB -> StorageStatus.LOW
            else -> StorageStatus.NORMAL
        }

        val cacheSize = calculateCacheSizeBytes()

        val state = StorageState(
            freeBytes = freeBytes,
            totalBytes = totalBytes,
            status = status,
            cacheSizeBytes = cacheSize,
        )
        _storageState.value = state
        state
    }

    private fun refreshStorageStateSync(): StorageState {
        return try {
            val targetPath = context.filesDir
            val stat = StatFs(targetPath.absolutePath)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val totalBytes = stat.blockCountLong * stat.blockSizeLong
            val freeMb = freeBytes / (1024L * 1024L)

            val status = when {
                freeMb < StorageMonitor.CRITICAL_THRESHOLD_MB -> StorageStatus.CRITICAL
                freeMb < StorageMonitor.LOW_THRESHOLD_MB -> StorageStatus.LOW
                else -> StorageStatus.NORMAL
            }
            val state = StorageState(
                freeBytes = freeBytes,
                totalBytes = totalBytes,
                status = status,
                cacheSizeBytes = 0L,
            )
            _storageState.value = state
            state
        } catch (_: Exception) {
            StorageState()
        }
    }

    /**
     * Computes the total byte size of internal cache and temporary directories.
     */
    override suspend fun calculateCacheSizeBytes(): Long = withContext(dispatchers.io) {
        computeDirSize(context.cacheDir) + computeDirSize(context.externalCacheDir)
    }

    /**
     * Purges temporary and cache files older than [olderThanMs] (default 24 hours).
     */
    override suspend fun cleanExpiredCache(olderThanMs: Long): Long = withContext(dispatchers.io) {
        val cutoff = System.currentTimeMillis() - olderThanMs
        var deletedBytes = 0L

        deletedBytes += deleteExpiredFiles(context.cacheDir, cutoff)
        deletedBytes += deleteExpiredFiles(context.externalCacheDir, cutoff)

        logger.i(TAG, "Expired cache cleanup freed ${deletedBytes / 1024} KB")
        refreshStorageState()
        deletedBytes
    }

    /**
     * Clears all cache files immediately and refreshes storage state.
     */
    override suspend fun clearCache(): Long = withContext(dispatchers.io) {
        var deletedBytes = 0L
        deletedBytes += deleteDirectoryContents(context.cacheDir)
        deletedBytes += deleteDirectoryContents(context.externalCacheDir)
        logger.i(TAG, "Manual cache purge freed ${deletedBytes / 1024} KB")
        refreshStorageState()
        deletedBytes
    }

    private fun computeDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            size += if (file.isDirectory) computeDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteExpiredFiles(dir: File?, cutoffTimeMs: Long): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                freed += deleteExpiredFiles(file, cutoffTimeMs)
            } else if (file.lastModified() < cutoffTimeMs) {
                val len = file.length()
                if (file.delete()) freed += len
            }
        }
        return freed
    }

    private fun deleteDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var freed = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            if (file.isDirectory) {
                freed += deleteDirectoryContents(file)
                file.delete()
            } else {
                val len = file.length()
                if (file.delete()) freed += len
            }
        }
        return freed
    }

    companion object {
        private const val TAG = "StorageMonitor"
    }
}
