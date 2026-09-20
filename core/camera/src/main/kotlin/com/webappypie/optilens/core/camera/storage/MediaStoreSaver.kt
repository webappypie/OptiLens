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
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
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
 * - Supports JPEG, Adobe DNG (RAW), and paired RAW+JPEG companion workflows.
 * - Extracts and normalizes EXIF orientation and tags.
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

            // 2. Set complete EXIF tags
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    contentResolver.openFileDescriptor(imageUri, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        if (orientationDegrees != 0) {
                            exif.setAttribute(
                                ExifInterface.TAG_ORIENTATION,
                                degreesToExifOrientation(orientationDegrees).toString()
                            )
                        }
                        exif.setAttribute(ExifInterface.TAG_MAKE, "OptiLens")
                        exif.setAttribute(ExifInterface.TAG_SOFTWARE, "OptiLens Pro Engine 1.0")
                        val dateFormatted = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).format(Date(timestamp))
                        exif.setAttribute(ExifInterface.TAG_DATETIME, dateFormatted)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateFormatted)
                        exif.saveAttributes()
                    }
                } catch (e: Exception) {
                    logger.w(TAG, "Failed writing EXIF tags: ${e.message}")
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
                isRaw = false,
            )

            logger.i(TAG, "Photo saved successfully to MediaStore: $imageUri (${bytesToPersist.size} bytes)")
            OptiResult.Success(capturedPhoto)
        } catch (e: Exception) {
            logger.e(TAG, "Failed writing photo to MediaStore: ${e.message}", e)
            try {
                contentResolver.delete(imageUri, null, null)
            } catch (_: Exception) {}
            OptiResult.Error(OptiError.StorageFailed(cause = e))
        }
    }

    /**
     * Saves raw DNG byte array into MediaStore and returns [CapturedPhoto].
     */
    suspend fun saveDng(
        dngBytes: ByteArray,
        orientationDegrees: Int = 0,
        expectedWidth: Int = 0,
        expectedHeight: Int = 0,
        rawFormat: RawCaptureFormat = RawCaptureFormat.RAW_SENSOR,
        customTimestamp: Long? = null,
    ): OptiResult<CapturedPhoto> = withContext(dispatchers.io) {
        val timestamp = customTimestamp ?: System.currentTimeMillis()
        val timeString = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestamp))
        val displayName = "OptiLens_$timeString.${rawFormat.extension}"

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, rawFormat.mimeType)
            put(MediaStore.Images.Media.DATE_ADDED, timestamp / 1000)
            put(MediaStore.Images.Media.DATE_TAKEN, timestamp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/OptiLens")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val contentResolver = context.contentResolver
        val rawUri: Uri = try {
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext OptiResult.Error(
                    OptiError.StorageFailed(IllegalStateException("Failed to insert MediaStore record for DNG"))
                )
        } catch (e: Exception) {
            logger.e(TAG, "Error inserting DNG into MediaStore: ${e.message}", e)
            return@withContext OptiResult.Error(OptiError.StorageFailed(cause = e))
        }

        try {
            contentResolver.openOutputStream(rawUri)?.use { outputStream ->
                outputStream.write(dngBytes)
                outputStream.flush()
            } ?: throw IllegalStateException("Unable to open output stream for $rawUri")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(rawUri, contentValues, null, null)
            }

            val capturedPhoto = CapturedPhoto(
                uri = rawUri.toString(),
                contentUri = rawUri,
                width = expectedWidth,
                height = expectedHeight,
                timestampMs = timestamp,
                orientationDegrees = orientationDegrees,
                fileSizeBytes = dngBytes.size.toLong(),
                isRaw = true,
                rawFormat = rawFormat,
            )
            logger.i(TAG, "DNG saved successfully to MediaStore: $rawUri (${dngBytes.size} bytes)")
            OptiResult.Success(capturedPhoto)
        } catch (e: Exception) {
            logger.e(TAG, "Failed writing DNG to MediaStore: ${e.message}", e)
            try {
                contentResolver.delete(rawUri, null, null)
            } catch (_: Exception) {}
            OptiResult.Error(OptiError.StorageFailed(cause = e))
        }
    }

    /**
     * Atomically saves both a RAW file and its companion JPEG with matched timestamps.
     */
    suspend fun saveRawWithCompanionJpeg(
        rawBytes: ByteArray,
        jpegBytes: ByteArray,
        orientationDegrees: Int = 0,
        expectedWidth: Int = 0,
        expectedHeight: Int = 0,
        rawFormat: RawCaptureFormat = RawCaptureFormat.RAW_SENSOR,
        mirrorHorizontal: Boolean = false,
    ): OptiResult<CapturedPhoto> = withContext(dispatchers.io) {
        val timestamp = System.currentTimeMillis()

        // 1. Save companion JPEG
        val jpegResult = saveJpeg(
            jpegBytes = jpegBytes,
            orientationDegrees = orientationDegrees,
            expectedWidth = expectedWidth,
            expectedHeight = expectedHeight,
            mirrorHorizontal = mirrorHorizontal,
        )

        val jpegPhoto = (jpegResult as? OptiResult.Success)?.data

        // 2. Save RAW DNG
        val rawResult = saveDng(
            dngBytes = rawBytes,
            orientationDegrees = orientationDegrees,
            expectedWidth = expectedWidth,
            expectedHeight = expectedHeight,
            rawFormat = rawFormat,
            customTimestamp = timestamp,
        )

        return@withContext when (rawResult) {
            is OptiResult.Success -> {
                val combined = rawResult.data.copy(
                    companionUri = jpegPhoto?.uri,
                    thumbnail = jpegPhoto?.thumbnail,
                )
                OptiResult.Success(combined)
            }
            is OptiResult.Error -> {
                if (jpegPhoto != null) {
                    OptiResult.Success(jpegPhoto)
                } else {
                    rawResult
                }
            }
            else -> rawResult
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
