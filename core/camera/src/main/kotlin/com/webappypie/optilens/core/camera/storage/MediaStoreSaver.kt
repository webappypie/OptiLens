package com.webappypie.optilens.core.camera.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.webappypie.optilens.core.camera.model.CapturedPhoto
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists captured photos directly to Android's scoped MediaStore storage.
 *
 * Compliance:
 * - Writes to DCIM/OptiLens without requiring broad READ_EXTERNAL_STORAGE / READ_MEDIA_IMAGES.
 * - Manages IS_PENDING on Android 10+ (API 29+) to ensure other apps do not read partial captures.
 * - Extracts and normalizes EXIF orientation.
 * - Generates low-memory thumbnails strictly on background I/O threads.
 */
@Singleton
class MediaStoreSaver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
    private val logger: AppLogger,
) {

    /**
     * Saves raw JPEG byte array into MediaStore and returns [CapturedPhoto].
     */
    suspend fun saveJpeg(
        jpegBytes: ByteArray,
        orientationDegrees: Int = 0,
        expectedWidth: Int = 0,
        expectedHeight: Int = 0,
        mirrorHorizontal: Boolean = false,
    ): OptiResult<CapturedPhoto> = withContext(dispatchers.io) {
        val timestamp = System.currentTimeMillis()
        val timeString = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestamp))
        val displayName = "OptiLens_$timeString.jpg"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/OptiLens")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val contentResolver = context.contentResolver
        val imageUri: Uri = try {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext OptiResult.Error(
                    OptiError.StorageFailed(IllegalStateException("Failed to insert MediaStore record"))
                )
        } catch (e: Exception) {
            logger.e(TAG, "Error inserting into MediaStore: ${e.message}", e)
            return@withContext OptiResult.Error(OptiError.StorageFailed(cause = e))
        }

        val bytesToPersist = if (mirrorHorizontal) {
            try {
                val original = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
                if (original != null) {
                    val matrix = Matrix().apply { postScale(-1f, 1f) }
                    val mirrored = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
                    val stream = java.io.ByteArrayOutputStream()
                    mirrored.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                    if (mirrored != original) original.recycle()
                    mirrored.recycle()
                    stream.toByteArray()
                } else {
                    jpegBytes
                }
            } catch (e: Exception) {
                logger.w(TAG, "Failed mirroring JPEG bytes: ${e.message}")
                jpegBytes
            }
        } else {
            jpegBytes
        }

        try {
            // 1. Stream JPEG bytes to storage
            contentResolver.openOutputStream(imageUri)?.use { outputStream ->
                outputStream.write(bytesToPersist)
                outputStream.flush()
            } ?: throw IllegalStateException("Unable to open output stream for $imageUri")

            // 2. Set EXIF orientation if needed
            if (orientationDegrees != 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    contentResolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        exif.setAttribute(
                            ExifInterface.TAG_ORIENTATION,
                            degreesToExifOrientation(orientationDegrees).toString()
                        )
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Failed writing EXIF orientation: ${e.message}")
                }
            }

            // 3. Mark pending as 0 (ready for external viewers) on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(imageUri, contentValues, null, null)
            }

            // 4. Generate thumbnail bitmap strictly on background thread
            val thumbnail = createThumbnail(bytesToPersist, orientationDegrees, mirrorHorizontal = false)

            val capturedPhoto = CapturedPhoto(
                uri = imageUri.toString(),
                contentUri = imageUri,
                width = expectedWidth,
                height = expectedHeight,
                timestampMs = timestamp,
                thumbnail = thumbnail,
                orientationDegrees = orientationDegrees,
                fileSizeBytes = bytesToPersist.size.toLong(),
            )

            logger.i(TAG, "Photo saved successfully to MediaStore: $imageUri (${bytesToPersist.size} bytes)")
            OptiResult.Success(capturedPhoto)
        } catch (e: Exception) {
            logger.e(TAG, "Failed writing photo to MediaStore: ${e.message}", e)
            // Clean up partial record
            try {
                contentResolver.delete(imageUri, null, null)
            } catch (_: Exception) {}
            OptiResult.Error(OptiError.StorageFailed(cause = e))
        }
    }

    private fun createThumbnail(jpegBytes: ByteArray, orientationDegrees: Int, mirrorHorizontal: Boolean = false): Bitmap? {
        return try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, boundsOptions)

            val targetSize = 128
            var sampleSize = 1
            var w = boundsOptions.outWidth
            var h = boundsOptions.outHeight

            while (w / 2 >= targetSize && h / 2 >= targetSize) {
                w /= 2
                h /= 2
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Low-memory footprint
            }

            val rawThumbnail = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, decodeOptions) ?: return null

            val matrix = Matrix()
            if (mirrorHorizontal) {
                matrix.postScale(-1f, 1f)
            }
            if (orientationDegrees != 0) {
                matrix.postRotate(orientationDegrees.toFloat())
            }

            if (!matrix.isIdentity) {
                val rotated = Bitmap.createBitmap(rawThumbnail, 0, 0, rawThumbnail.width, rawThumbnail.height, matrix, true)
                if (rotated != rawThumbnail) {
                    rawThumbnail.recycle()
                }
                rotated
            } else {
                rawThumbnail
            }
        } catch (e: Exception) {
            logger.w(TAG, "Failed creating thumbnail: ${e.message}")
            null
        }
    }

    private fun degreesToExifOrientation(degrees: Int): Int = when ((degrees % 360 + 360) % 360) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    companion object {
        private const val TAG = "MediaStoreSaver"
    }
}
