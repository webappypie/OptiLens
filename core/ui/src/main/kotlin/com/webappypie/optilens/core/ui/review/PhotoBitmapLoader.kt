package com.webappypie.optilens.core.ui.review

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

interface PhotoBitmapLoader {
    /**
     * Decodes the photo upright (EXIF orientation applied) with its long edge capped at
     * [PhotoDecodeMath.MAX_EDIT_LONG_EDGE_PX], so editing never holds an unbounded bitmap.
     */
    suspend fun loadBitmap(uriString: String): Bitmap?

    /** Writes [bitmap] as a new JPEG in DCIM/OptiLens. The original photo is never modified. */
    suspend fun saveEnhancedBitmap(bitmap: Bitmap, originalUri: String, keepOriginal: Boolean): String?
}

/** Pure sizing/orientation maths for [DefaultPhotoBitmapLoader], kept free of Android types. */
object PhotoDecodeMath {

    /** Long-edge cap for editing: ~12.6 MP at 4:3, i.e. full resolution for typical 12 MP photos. */
    const val MAX_EDIT_LONG_EDGE_PX = 4096

    /** Largest power-of-two sample size that keeps the decoded long edge >= [maxLongEdge] where possible. */
    fun inSampleSize(width: Int, height: Int, maxLongEdge: Int = MAX_EDIT_LONG_EDGE_PX): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (maxOf(width, height) / (sample * 2) >= maxLongEdge) sample *= 2
        // A power-of-two step can still leave the edge above the cap; that remainder is
        // removed by an exact scale after decoding.
        return sample
    }

    /** Exact scale (<= 1) applied after sampled decode so the long edge does not exceed [maxLongEdge]. */
    fun residualScale(decodedWidth: Int, decodedHeight: Int, maxLongEdge: Int = MAX_EDIT_LONG_EDGE_PX): Float {
        val longEdge = maxOf(decodedWidth, decodedHeight)
        return if (longEdge <= maxLongEdge || longEdge <= 0) 1f else maxLongEdge.toFloat() / longEdge
    }

    /** Clockwise rotation and horizontal flip that display an image upright for an EXIF orientation. */
    data class Transform(val rotationDegrees: Int, val flipHorizontal: Boolean) {
        val isIdentity: Boolean get() = rotationDegrees == 0 && !flipHorizontal
    }

    fun exifTransform(exifOrientation: Int): Transform = when (exifOrientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Transform(0, true)
        ExifInterface.ORIENTATION_ROTATE_180 -> Transform(180, false)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Transform(180, true)
        ExifInterface.ORIENTATION_TRANSPOSE -> Transform(90, true)
        ExifInterface.ORIENTATION_ROTATE_90 -> Transform(90, false)
        ExifInterface.ORIENTATION_TRANSVERSE -> Transform(270, true)
        ExifInterface.ORIENTATION_ROTATE_270 -> Transform(270, false)
        else -> Transform(0, false)
    }
}

@Singleton
class DefaultPhotoBitmapLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) : PhotoBitmapLoader {

    override suspend fun loadBitmap(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val open: () -> InputStream?
            if (uriString.startsWith("content://")) {
                val uri = Uri.parse(uriString)
                open = { context.contentResolver.openInputStream(uri) }
            } else {
                val file = File(uriString)
                if (!file.isFile) return@withContext null
                open = { file.inputStream() }
            }

            // Bounds-only decode always returns null by design; the result is in bounds.out*.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = open() ?: return@withContext null
            boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val options = BitmapFactory.Options().apply {
                inSampleSize = PhotoDecodeMath.inSampleSize(bounds.outWidth, bounds.outHeight)
            }
            val decoded = open()?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@withContext null

            val exifOrientation = try {
                open()?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            uprightAndBounded(decoded, PhotoDecodeMath.exifTransform(exifOrientation))
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun uprightAndBounded(source: Bitmap, transform: PhotoDecodeMath.Transform): Bitmap {
        val scale = PhotoDecodeMath.residualScale(source.width, source.height)
        if (transform.isIdentity && scale == 1f) return source

        // Rotate first, then mirror: required for TRANSPOSE/TRANSVERSE (same order as the
        // EXIF spec's composite orientations; flipping first would swap the two).
        val matrix = Matrix().apply {
            if (scale != 1f) postScale(scale, scale)
            if (transform.rotationDegrees != 0) postRotate(transform.rotationDegrees.toFloat())
            if (transform.flipHorizontal) postScale(-1f, 1f)
        }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        if (result !== source) source.recycle()
        return result
    }

    override suspend fun saveEnhancedBitmap(
        bitmap: Bitmap,
        originalUri: String,
        keepOriginal: Boolean,
    ): String? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var uri: Uri? = null
        try {
            // "enhanced" in the name is what the gallery uses to label edited copies.
            val filename = "OptiLens_Enhanced_${System.currentTimeMillis()}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/OptiLens")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return@withContext null

            val written = resolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
            } ?: false
            check(written) { "JPEG encode failed" }
            copyCaptureMetadata(originalUri, uri)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            uri.toString()
        } catch (_: OutOfMemoryError) {
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        } catch (_: Exception) {
            // Remove the half-written pending entry so no broken file appears in the gallery.
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            null
        }
    }

    /**
     * Copies capture metadata (time, camera, exposure) from the original into the saved copy.
     * Orientation is set to normal because the pixels were already rotated upright. Location
     * tags are copied only if the original has them. Best effort: a failure keeps the photo.
     */
    private fun copyCaptureMetadata(originalUri: String, targetUri: Uri) {
        try {
            val source = if (originalUri.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(originalUri))?.use { ExifInterface(it) }
            } else {
                File(originalUri).takeIf { it.isFile }?.let { ExifInterface(it.absolutePath) }
            } ?: return
            context.contentResolver.openFileDescriptor(targetUri, "rw")?.use { pfd ->
                val target = ExifInterface(pfd.fileDescriptor)
                for (tag in COPIED_EXIF_TAGS) {
                    source.getAttribute(tag)?.let { target.setAttribute(tag, it) }
                }
                target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                target.setAttribute(ExifInterface.TAG_SOFTWARE, "OptiLens (on-device enhance)")
                target.saveAttributes()
            }
        } catch (_: Exception) {
            // Metadata is optional; the enhanced pixels are already saved.
        }
    }

    private companion object {
        val COPIED_EXIF_TAGS = listOf(
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_ISO_SPEED_RATINGS,
            ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
            ExifInterface.TAG_WHITE_BALANCE,
            ExifInterface.TAG_FLASH,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
        )
    }
}
