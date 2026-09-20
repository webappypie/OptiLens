package com.webappypie.optilens.core.camera.storage

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Optical and exposure telemetry summary extracted from image EXIF.
 */
@Serializable
data class MediaExifSummary(
    val focalLength35mm: Int? = null,
    val fNumber: Float? = null,
    val iso: Int? = null,
    val shutterSpeedNanos: Long? = null,
    val exposureCompensation: Float? = null,
    val flashFired: Boolean? = null,
    val lensModel: String? = null,
    val orientationDegrees: Int = 0,
) {
    val opticalSummary: String
        get() {
            val parts = mutableListOf<String>()
            if (focalLength35mm != null && focalLength35mm > 0) parts.add("${focalLength35mm}mm")
            if (fNumber != null && fNumber > 0f) parts.add(String.format(Locale.US, "f/%.1f", fNumber))
            if (iso != null && iso > 0) parts.add("ISO $iso")
            if (shutterSpeedNanos != null && shutterSpeedNanos > 0L) {
                val sec = shutterSpeedNanos / 1_000_000_000.0
                val formatted = if (sec >= 1.0) {
                    String.format(Locale.US, "%.1fs", sec).replace(".0s", "s")
                } else {
                    val denom = kotlin.math.round(1.0 / sec).toInt()
                    "1/$denom"
                }
                parts.add(formatted)
            }
            return parts.joinToString(" · ")
        }
}

/**
 * Chronological date grouping categories for gallery presentation.
 */
enum class DateBucket(val title: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    OLDER("Older");

    companion object {
        fun fromTimestamp(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): DateBucket {
            val itemCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }

            val itemYear = itemCal.get(Calendar.YEAR)
            val nowYear = nowCal.get(Calendar.YEAR)
            val itemDay = itemCal.get(Calendar.DAY_OF_YEAR)
            val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)

            if (itemYear == nowYear) {
                if (itemDay == nowDay) return TODAY
                if (nowDay - itemDay == 1) return YESTERDAY
                if (nowDay - itemDay < 7 && itemCal.get(Calendar.WEEK_OF_YEAR) == nowCal.get(Calendar.WEEK_OF_YEAR)) {
                    return THIS_WEEK
                }
                if (itemCal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH)) {
                    return THIS_MONTH
                }
            }
            return OLDER
        }
    }
}

/**
 * Representation of a media item captured or enhanced by OptiLens.
 */
@Serializable
data class MediaItem(
    val id: Long,
    val contentUri: String,
    val displayName: String,
    val dateTakenMs: Long,
    val dateAddedSec: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String,
    val isRaw: Boolean,
    val isEnhanced: Boolean,
    val isFavorite: Boolean = false,
    val companionUri: String? = null,
    val originalUri: String? = null,
    val metadata: MediaExifSummary? = null,
) {
    val dateBucket: DateBucket
        get() = DateBucket.fromTimestamp(if (dateTakenMs > 0) dateTakenMs else dateAddedSec * 1000L)

    val formattedDate: String
        get() {
            val ts = if (dateTakenMs > 0) dateTakenMs else dateAddedSec * 1000L
            return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(ts))
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return ""
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                val kb = sizeBytes / 1024.0
                String.format(Locale.US, "%.0f KB", kb)
            }
        }

    val megapixels: Float
        get() = (width.toLong() * height.toLong()) / 1_000_000f

    val resolutionSummary: String
        get() = if (width > 0 && height > 0) {
            "${width}x${height} (${String.format(Locale.US, "%.1f", megapixels)} MP)"
        } else ""

    val aspectRatio: Float
        get() = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1.0f
}
