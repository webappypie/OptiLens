package com.webappypie.optilens.core.camera.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance, low-memory thumbnail loading engine.
 *
 * Uses native [android.content.ContentResolver.loadThumbnail] on Android 10+ (API 29+)
 * for hardware-accelerated, subsampled decoding, backed by an in-memory LRU cache.
 */
@Singleton
class ThumbnailLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {

    // 16MB or 1/8th max heap LRU cache
    private val maxCacheBytes = minOf(16 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 8).toInt())
    private val memoryCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int = bitmap.byteCount
    }

    /**
     * Loads a thumbnail bitmap for the given [uriString].
     * Checks in-memory cache first before performing background disk decode.
     */
    suspend fun loadThumbnail(
        uriString: String,
        targetWidth: Int = DEFAULT_THUMBNAIL_SIZE,
        targetHeight: Int = DEFAULT_THUMBNAIL_SIZE,
    ): Bitmap? = withContext(dispatchers.io) {
        if (uriString.isBlank()) return@withContext null

        val cacheKey = "${uriString}_${targetWidth}x${targetHeight}"
        synchronized(memoryCache) {
            val cached = memoryCache.get(cacheKey)
            if (cached != null && !cached.isRecycled) {
                return@withContext cached
            }
        }

        val bitmap = try {
            if (uriString.startsWith("content://")) {
                loadContentThumbnail(Uri.parse(uriString), targetWidth, targetHeight)
            } else {
                loadFileThumbnail(uriString, targetWidth, targetHeight)
            }
        } catch (e: Exception) {
            logger.w(TAG, "Error loading thumbnail for $uriString: ${e.message}")
            null
        }

        if (bitmap != null) {
            synchronized(memoryCache) {
                memoryCache.put(cacheKey, bitmap)
            }
        }

        bitmap
    }

    private fun loadContentThumbnail(uri: Uri, targetW: Int, targetH: Int): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                return resolver.loadThumbnail(uri, Size(targetW, targetH), null)
            } catch (_: Exception) {
                // Fall back to manual downsampled decoding
            }
        }

        return resolver.openInputStream(uri)?.use { stream ->
            decodeDownsampled(stream, targetW, targetH)
        }
    }

    private fun loadFileThumbnail(path: String, targetW: Int, targetH: Int): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return file.inputStream().use { stream ->
            decodeDownsampled(stream, targetW, targetH)
        }
    }

    private fun decodeDownsampled(inputStream: java.io.InputStream, targetW: Int, targetH: Int): Bitmap? {
        val bytes = inputStream.readBytes()
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

        var sampleSize = 1
        var w = boundsOptions.outWidth
        var h = boundsOptions.outHeight

        while (w / 2 >= targetW && h / 2 >= targetH) {
            w /= 2
            h /= 2
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Half the memory footprint of ARGB_8888
        }

        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
    }

    fun clearCache() {
        synchronized(memoryCache) {
            memoryCache.evictAll()
        }
    }

    companion object {
        private const val TAG = "ThumbnailLoader"
        const val DEFAULT_THUMBNAIL_SIZE = 384
    }
}
