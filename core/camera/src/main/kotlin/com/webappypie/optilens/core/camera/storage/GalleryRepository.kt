package com.webappypie.optilens.core.camera.storage

import android.app.PendingIntent
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository providing privacy-minimal access to OptiLens captures in MediaStore.
 *
 * Adheres strictly to scoped storage without requiring broad READ_EXTERNAL_STORAGE
 * or READ_MEDIA_IMAGES permissions.
 */
interface GalleryRepository {

    /**
     * Continuously observes photos captured or enhanced by OptiLens.
     * Updates automatically on MediaStore changes and user favorite toggles.
     */
    fun observeMedia(): Flow<List<MediaItem>>

    /**
     * Loads the current snapshot list of media items.
     */
    suspend fun loadMedia(): List<MediaItem>

    /**
     * Deletes a media item by URI.
     *
     * @return [OptiResult.Success] with `null` if deleted directly, or with a [PendingIntent]
     * if system confirmation is required on Android 11+ (API 30+).
     */
    suspend fun deleteMedia(uriString: String): OptiResult<PendingIntent?>

    /**
     * Toggles the favorite status of the given [uriString].
     *
     * @return [OptiResult.Success] with the new favorite state (true/false).
     */
    suspend fun toggleFavorite(uriString: String): OptiResult<Boolean>

    /**
     * Extracts optical and exposure EXIF metadata from the given media URI.
     */
    suspend fun extractExifMetadata(uriString: String): MediaExifSummary?
}
