package com.webappypie.optilens.core.camera.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class MediaItemTest {

    @Test
    fun `date bucket categorization handles today, yesterday, this week, and older`() {
        val now = Calendar.getInstance()

        val todayMs = now.timeInMillis
        assertEquals(DateBucket.TODAY, DateBucket.fromTimestamp(todayMs, now.timeInMillis))

        val yesterdayCal = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        assertEquals(DateBucket.YESTERDAY, DateBucket.fromTimestamp(yesterdayCal.timeInMillis, now.timeInMillis))

        val olderCal = (now.clone() as Calendar).apply { add(Calendar.MONTH, -3) }
        assertEquals(DateBucket.OLDER, DateBucket.fromTimestamp(olderCal.timeInMillis, now.timeInMillis))
    }

    @Test
    fun `media exif summary formats optical string accurately`() {
        val summary = MediaExifSummary(
            focalLength35mm = 24,
            fNumber = 1.8f,
            iso = 200,
            shutterSpeedNanos = 4_000_000L, // 1/250s
        )

        val formatted = summary.opticalSummary
        assertTrue(formatted.contains("24mm"))
        assertTrue(formatted.contains("f/1.8"))
        assertTrue(formatted.contains("ISO 200"))
        assertTrue(formatted.contains("1/250"))
    }

    @Test
    fun `media item computes resolution and megapixels correctly`() {
        val item = MediaItem(
            id = 101L,
            contentUri = "content://media/external/images/media/101",
            displayName = "OptiLens_20260920_120000.jpg",
            dateTakenMs = 1726800000000L,
            dateAddedSec = 1726800000L,
            width = 4000,
            height = 3000,
            sizeBytes = 4_500_000L,
            mimeType = "image/jpeg",
            isRaw = false,
            isEnhanced = false,
            isFavorite = false,
        )

        assertEquals(12.0f, item.megapixels, 0.1f)
        assertTrue(item.resolutionSummary.contains("12.0 MP"))
        assertTrue(item.formattedSize.contains("4.3 MB") || item.formattedSize.contains("4.2 MB") || item.formattedSize.contains("4."))
        assertEquals(4000f / 3000f, item.aspectRatio, 0.01f)
    }

    @Test
    fun `raw media item and enhanced item identification flags work`() {
        val rawItem = MediaItem(
            id = 102L,
            contentUri = "content://media/external/images/media/102",
            displayName = "OptiLens_20260920_120000.dng",
            dateTakenMs = 1726800000000L,
            dateAddedSec = 1726800000L,
            width = 4000,
            height = 3000,
            sizeBytes = 24_000_000L,
            mimeType = "image/x-adobe-dng",
            isRaw = true,
            isEnhanced = false,
        )
        assertTrue(rawItem.isRaw)
        assertFalse(rawItem.isEnhanced)

        val enhancedItem = MediaItem(
            id = 103L,
            contentUri = "content://media/external/images/media/103",
            displayName = "OptiLens_AI_1726800000000.jpg",
            dateTakenMs = 1726800000000L,
            dateAddedSec = 1726800000L,
            width = 4000,
            height = 3000,
            sizeBytes = 5_000_000L,
            mimeType = "image/jpeg",
            isRaw = false,
            isEnhanced = true,
        )
        assertFalse(enhancedItem.isRaw)
        assertTrue(enhancedItem.isEnhanced)
    }
}
