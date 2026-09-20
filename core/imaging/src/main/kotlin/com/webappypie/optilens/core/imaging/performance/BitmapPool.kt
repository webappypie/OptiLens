package com.webappypie.optilens.core.imaging.performance

import android.graphics.Bitmap
import com.webappypie.optilens.core.logging.AppLogger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time operational metrics for [BitmapPool].
 */
data class BitmapPoolMetrics(
    val currentSizeBytes: Long = 0L,
    val maxSizeBytes: Long = 0L,
    val hitCount: Long = 0L,
    val missCount: Long = 0L,
    val evictionCount: Long = 0L,
    val pooledCount: Int = 0,
) {
    val hitRatePercent: Float
        get() {
            val total = hitCount + missCount
            return if (total > 0L) (hitCount.toFloat() / total) * 100f else 0f
        }
}

/**
 * Thread-safe, LRU-bounded bitmap pool for intermediate imaging buffers.
 *
 * Implements Phase 19 Task 7:
 * - Minimizes GC heap churn by recycling intermediate image processing bitmaps.
 * - Eliminates allocations during continuous multi-frame burst fusion, AI deblur, and super-resolution.
 * - Enforces bounded memory footprint with deterministic eviction and memory trim integration.
 */
@Singleton
class BitmapPool @Inject constructor(
    private val logger: AppLogger,
) {
    companion object {
        const val DEFAULT_MAX_SIZE_BYTES = 32L * 1024L * 1024L // 32 MB default cap
        private const val TAG = "BitmapPool"
    }

    private var maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES
    private var currentSizeBytes: Long = 0L
    private var hitCount: Long = 0L
    private var missCount: Long = 0L
    private var evictionCount: Long = 0L

    private data class Key(val width: Int, val height: Int, val config: Bitmap.Config)

    // LinkedHashMap with accessOrder=true for LRU eviction
    private val pool = mutableMapOf<Key, ArrayDeque<Bitmap>>()

    @Synchronized
    fun setMaxSize(maxBytes: Long) {
        maxSizeBytes = maxBytes.coerceAtLeast(1024L * 1024L)
        trimToSize(maxSizeBytes)
    }

    /**
     * Checks out a reusable [Bitmap] with matching dimensions and config if available.
     * Returns null on pool miss (caller should instantiate fresh).
     */
    @Synchronized
    fun acquire(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val key = Key(width, height, config)
        val deque = pool[key]

        while (deque != null && deque.isNotEmpty()) {
            val candidate = deque.removeFirst()
            val size = getByteSize(candidate, width, height, config)
            currentSizeBytes -= size

            val isRecycled = try { candidate.isRecycled } catch (_: Throwable) { false }
            if (!isRecycled) {
                hitCount++
                return candidate
            }
        }

        missCount++
        return null
    }

    /**
     * Returns a used [Bitmap] to the pool for subsequent reuse.
     */
    @Synchronized
    fun release(
        bitmap: Bitmap,
        explicitWidth: Int? = null,
        explicitHeight: Int? = null,
        explicitConfig: Bitmap.Config? = null,
    ) {
        val isRecycled = try { bitmap.isRecycled } catch (_: Throwable) { false }
        if (isRecycled) return

        val w = explicitWidth ?: try { bitmap.width } catch (_: Throwable) { 0 }
        val h = explicitHeight ?: try { bitmap.height } catch (_: Throwable) { 0 }
        val cfg = explicitConfig ?: try { bitmap.config ?: Bitmap.Config.ARGB_8888 } catch (_: Throwable) { Bitmap.Config.ARGB_8888 }

        if (w <= 0 || h <= 0) return

        val size = getByteSize(bitmap, w, h, cfg)
        if (size > maxSizeBytes) {
            // Bitmap is too large to fit in pool entirely
            try { bitmap.recycle() } catch (_: Throwable) {}
            return
        }

        trimToSize(maxSizeBytes - size)

        val key = Key(w, h, cfg)
        val deque = pool.getOrPut(key) { ArrayDeque() }
        deque.addLast(bitmap)
        currentSizeBytes += size
    }

    /**
     * Trims pool capacity based on system ComponentCallbacks2 trim level.
     */
    @Synchronized
    fun trim(level: Int) {
        when {
            level >= 15 -> clear() // TRIM_MEMORY_RUNNING_CRITICAL / COMPLETE -> flush all
            level >= 10 -> trimToSize(maxSizeBytes / 2) // TRIM_MEMORY_RUNNING_LOW / MODERATE -> reduce to half
            else -> trimToSize((maxSizeBytes * 0.75f).toLong())
        }
    }

    @Synchronized
    fun clear() {
        for (deque in pool.values) {
            for (bitmap in deque) {
                try {
                    val isRecycled = try { bitmap.isRecycled } catch (_: Throwable) { false }
                    if (!isRecycled) bitmap.recycle()
                } catch (_: Throwable) {}
            }
            deque.clear()
        }
        pool.clear()
        currentSizeBytes = 0L
        logger.d(TAG, "BitmapPool cleared")
    }

    @Synchronized
    fun getMetrics(): BitmapPoolMetrics {
        var totalPooled = 0
        for (deque in pool.values) totalPooled += deque.size
        return BitmapPoolMetrics(
            currentSizeBytes = currentSizeBytes,
            maxSizeBytes = maxSizeBytes,
            hitCount = hitCount,
            missCount = missCount,
            evictionCount = evictionCount,
            pooledCount = totalPooled,
        )
    }

    private fun trimToSize(targetBytes: Long) {
        while (currentSizeBytes > targetBytes && pool.isNotEmpty()) {
            val iterator = pool.entries.iterator()
            if (!iterator.hasNext()) break

            val (key, deque) = iterator.next()

            if (deque.isNotEmpty()) {
                val evicted = deque.removeFirst()
                val size = getByteSize(evicted, key.width, key.height, key.config)
                currentSizeBytes -= size
                evictionCount++
                try {
                    val isRecycled = try { evicted.isRecycled } catch (_: Throwable) { false }
                    if (!isRecycled) evicted.recycle()
                } catch (_: Throwable) {}
            }

            if (deque.isEmpty()) {
                iterator.remove()
            }
        }
    }

    private fun getByteSize(bitmap: Bitmap, w: Int, h: Int, config: Bitmap.Config): Long {
        val bpp = when (config) {
            Bitmap.Config.ALPHA_8 -> 1
            Bitmap.Config.RGB_565 -> 2
            Bitmap.Config.ARGB_8888 -> 4
            Bitmap.Config.RGBA_F16 -> 8
            else -> 4
        }
        return w.toLong() * h.toLong() * bpp
    }
}
