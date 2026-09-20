package com.webappypie.optilens.core.camera.storage

import android.app.PendingIntent
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GalleryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) : GalleryRepository {

    private val favoritesPrefs by lazy {
        context.getSharedPreferences("optilens_favorites", Context.MODE_PRIVATE)
    }

    private val _favoritesFlow = MutableStateFlow<Set<String>>(emptySet())

    init {
        val saved = favoritesPrefs.getStringSet("favorite_uris", emptySet()) ?: emptySet()
        _favoritesFlow.value = saved.toSet()
    }

    private val mediaChangeFlow: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                trySend(Unit)
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
        } catch (e: Exception) {
            logger.w(TAG, "Failed to register ContentObserver: ${e.message}")
        }

        // Emit initial signal
        trySend(Unit)

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (e: Exception) {
                logger.w(TAG, "Failed to unregister ContentObserver: ${e.message}")
            }
        }
    }

    override fun observeMedia(): Flow<List<MediaItem>> {
        return combine(mediaChangeFlow, _favoritesFlow) { _, favorites ->
            queryOptiLensMedia(favorites)
        }
            .flowOn(dispatchers.io)
            .distinctUntilChanged()
    }

    override suspend fun loadMedia(): List<MediaItem> = withContext(dispatchers.io) {
        queryOptiLensMedia(_favoritesFlow.value)
    }

    private fun queryOptiLensMedia(favorites: Set<String>): List<MediaItem> {
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
        )

        val (selection, selectionArgs) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val sel = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val args = arrayOf("DCIM/OptiLens%", "OptiLens")
            sel to args
        } else {
            val sel = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
            val args = arrayOf("OptiLens")
            sel to args
        }

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media.DATE_TAKEN} DESC"

        val rawItems = mutableListOf<MediaItem>()

        try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "OptiLens_$id"
                    val dateTaken = cursor.getLong(dateTakenCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val size = cursor.getLong(sizeCol)
                    val mime = cursor.getString(mimeCol) ?: "image/jpeg"

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id,
                    ).toString()

                    val isRaw = mime.equals("image/x-adobe-dng", ignoreCase = true) ||
                            mime.contains("raw", ignoreCase = true) ||
                            name.endsWith(".dng", ignoreCase = true)

                    val isEnhanced = name.contains("_AI_", ignoreCase = true) ||
                            name.contains("enhanced", ignoreCase = true)

                    val isFav = favorites.contains(contentUri) || favorites.contains(id.toString())

                    rawItems.add(
                        MediaItem(
                            id = id,
                            contentUri = contentUri,
                            displayName = name,
                            dateTakenMs = dateTaken,
                            dateAddedSec = dateAdded,
                            width = width,
                            height = height,
                            sizeBytes = size,
                            mimeType = mime,
                            isRaw = isRaw,
                            isEnhanced = isEnhanced,
                            isFavorite = isFav,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error querying MediaStore for OptiLens: ${e.message}", e)
        }

        return pairAndGroupMediaItems(rawItems)
    }

    /**
     * Correlates paired RAW+JPEG captures and Original/Enhanced versions.
     */
    private fun pairAndGroupMediaItems(items: List<MediaItem>): List<MediaItem> {
        if (items.isEmpty()) return emptyList()

        val jpegsByName = mutableMapOf<String, MediaItem>()
        val dngsByName = mutableMapOf<String, MediaItem>()
        val originalsByTimestamp = mutableMapOf<Long, MediaItem>()

        for (item in items) {
            val baseName = item.displayName.substringBeforeLast(".")
            if (item.isRaw) {
                dngsByName[baseName] = item
            } else if (!item.isEnhanced) {
                jpegsByName[baseName] = item
                if (item.dateTakenMs > 0) {
                    originalsByTimestamp[item.dateTakenMs] = item
                }
            }
        }

        return items.map { item ->
            var updated = item
            val baseName = item.displayName.substringBeforeLast(".")

            if (item.isRaw) {
                // Find matching companion JPEG
                val companionJpeg = jpegsByName[baseName]
                if (companionJpeg != null) {
                    updated = updated.copy(companionUri = companionJpeg.contentUri)
                }
            } else if (item.isEnhanced) {
                // Check if an unenhanced counterpart exists with the same approximate timestamp
                val original = originalsByTimestamp.entries.minByOrNull {
                    kotlin.math.abs(it.key - item.dateTakenMs)
                }?.takeIf { kotlin.math.abs(it.key - item.dateTakenMs) < 60_000L }?.value

                if (original != null) {
                    updated = updated.copy(originalUri = original.contentUri)
                }
            } else {
                // Regular JPEG: link if it has a companion DNG
                val companionDng = dngsByName[baseName]
                if (companionDng != null) {
                    updated = updated.copy(companionUri = companionDng.contentUri)
                }
            }
            updated
        }
    }

    override suspend fun deleteMedia(uriString: String): OptiResult<PendingIntent?> = withContext(dispatchers.io) {
        val uri = try {
            Uri.parse(uriString)
        } catch (e: Exception) {
            return@withContext OptiResult.Error(OptiError.StorageFailed(cause = e))
        }

        try {
            val rows = context.contentResolver.delete(uri, null, null)
            if (rows > 0) {
                logger.i(TAG, "Deleted media item: $uriString")
                OptiResult.Success(null)
            } else {
                OptiResult.Error(OptiError.StorageFailed(IllegalStateException("Failed deleting URI $uriString, 0 rows affected")))
            }
        } catch (securityException: SecurityException) {
            logger.w(TAG, "SecurityException on delete ($uriString), falling back to user consent request: ${securityException.message}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                    OptiResult.Success(pendingIntent)
                } catch (e: Exception) {
                    OptiResult.Error(OptiError.StorageFailed(cause = e))
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                securityException is android.app.RecoverableSecurityException
            ) {
                OptiResult.Success(securityException.userAction.actionIntent)
            } else {
                OptiResult.Error(OptiError.StorageFailed(cause = securityException))
            }
        } catch (e: Exception) {
            logger.e(TAG, "Unexpected error deleting $uriString: ${e.message}", e)
            OptiResult.Error(OptiError.StorageFailed(cause = e))
        }
    }

    override suspend fun toggleFavorite(uriString: String): OptiResult<Boolean> = withContext(dispatchers.io) {
        try {
            val current = _favoritesFlow.value.toMutableSet()
            val newFav = if (current.contains(uriString)) {
                current.remove(uriString)
                false
            } else {
                current.add(uriString)
                true
            }
            favoritesPrefs.edit().putStringSet("favorite_uris", current).apply()
            _favoritesFlow.value = current
            logger.d(TAG, "Toggled favorite for $uriString -> $newFav")
            OptiResult.Success(newFav)
        } catch (e: Exception) {
            logger.e(TAG, "Error toggling favorite for $uriString: ${e.message}", e)
            OptiResult.Error(OptiError.StorageFailed(cause = e))
        }
    }

    override suspend fun extractExifMetadata(uriString: String): MediaExifSummary? = withContext(dispatchers.io) {
        val uri = try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            return@withContext null
        }

        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val focal35mm = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0)
                val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).toFloat()
                val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0)
                    .takeIf { it > 0 }
                    ?: exif.getAttribute("PhotographicSensitivity")?.toIntOrNull()

                val expTimeSec = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0)
                val shutterSpeedNanos = if (expTimeSec > 0.0) (expTimeSec * 1_000_000_000.0).toLong() else null
                val ev = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, 0.0).toFloat()
                val flash = exif.getAttributeInt(ExifInterface.TAG_FLASH, -1).takeIf { it >= 0 }?.let { (it and 1) != 0 }
                val lens = exif.getAttribute("LensModel")
                val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }

                MediaExifSummary(
                    focalLength35mm = if (focal35mm > 0) focal35mm else null,
                    fNumber = if (fNumber > 0f) fNumber else null,
                    iso = iso,
                    shutterSpeedNanos = shutterSpeedNanos,
                    exposureCompensation = ev,
                    flashFired = flash,
                    lensModel = lens,
                    orientationDegrees = rotationDegrees,
                )
            }
        } catch (e: Exception) {
            logger.w(TAG, "Failed extracting EXIF from $uriString: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "GalleryRepository"
    }
}
