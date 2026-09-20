package com.webappypie.optilens.core.camera.raw

import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.DngCreator
import android.media.Image
import android.os.Build
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.PlatformRawCapabilities
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production engine for writing standard Adobe DNG (Digital Negative) raw files
 * compliant with Adobe DNG specification 1.4+.
 *
 * Encapsulates Android's [DngCreator] to embed:
 * - ColorMatrix1, ColorMatrix2
 * - ForwardMatrix1, ForwardMatrix2
 * - CalibrationTransform1, CalibrationTransform2
 * - BlackLevelPattern, WhiteLevel
 * - AsShotNeutral white balance
 * - Orientation and Lens metadata
 * - Optional embedded preview thumbnail
 */
@Singleton
class RawDngEngine @Inject constructor(
    private val logger: AppLogger,
) {

    /**
     * Determines whether the given [RawCaptureFormat] is physically supported
     * by the provided [CameraDeviceProfile].
     */
    fun isFormatSupported(format: RawCaptureFormat, profile: CameraDeviceProfile?): Boolean {
        if (profile == null) return false
        val caps = profile.rawCapabilities
        return when (format) {
            RawCaptureFormat.RAW_SENSOR -> caps.supportsRawSensor
            RawCaptureFormat.RAW10 -> caps.supportsRaw10
            RawCaptureFormat.RAW12 -> caps.supportsRaw12
            RawCaptureFormat.RAW_PRIVATE -> caps.supportsRawPrivate
        }
    }

    /**
     * Returns the list of all RAW formats physically supported by the camera profile.
     */
    fun getSupportedFormats(profile: CameraDeviceProfile?): List<RawCaptureFormat> {
        if (profile == null) return emptyList()
        val caps = profile.rawCapabilities
        val formats = mutableListOf<RawCaptureFormat>()
        if (caps.supportsRawSensor) formats.add(RawCaptureFormat.RAW_SENSOR)
        if (caps.supportsRaw10) formats.add(RawCaptureFormat.RAW10)
        if (caps.supportsRaw12) formats.add(RawCaptureFormat.RAW12)
        if (caps.supportsRawPrivate) formats.add(RawCaptureFormat.RAW_PRIVATE)
        return formats
    }

    /**
     * Writes an uncompressed raw [Image] into standard Adobe DNG stream using [DngCreator].
     *
     * @param characteristics Camera hardware characteristics for color calibration matrices.
     * @param captureResult Capture result containing per-frame exposure and white balance tags.
     * @param rawImage The raw sensor image buffer.
     * @param outputStream Destination output stream.
     * @param orientationDegrees Display rotation (0, 90, 180, 270).
     * @param thumbnail Optional embedded thumbnail bitmap.
     */
    fun writeDng(
        characteristics: CameraCharacteristics,
        captureResult: CaptureResult,
        rawImage: Image,
        outputStream: OutputStream,
        orientationDegrees: Int = 0,
        thumbnail: Bitmap? = null,
        description: String = "OptiLens Pro RAW Engine",
    ): OptiResult<Unit> {
        return try {
            val dngCreator = DngCreator(characteristics, captureResult)
            dngCreator.setDescription(description)
            dngCreator.setOrientation(degreesToDngOrientation(orientationDegrees))

            if (thumbnail != null && !thumbnail.isRecycled) {
                try {
                    dngCreator.setThumbnail(thumbnail)
                } catch (e: Exception) {
                    logger.w(TAG, "Failed embedding thumbnail in DNG: ${e.message}")
                }
            }

            dngCreator.writeImage(outputStream, rawImage)
            dngCreator.close()
            logger.i(TAG, "DNG file written successfully (${rawImage.width}x${rawImage.height})")
            OptiResult.Success(Unit)
        } catch (e: Exception) {
            logger.e(TAG, "Failed writing DNG image: ${e.message}", e)
            OptiResult.Error(OptiError.ProcessingFailed(stage = "DNG writing", cause = e))
        }
    }

    /**
     * Maps degrees (0, 90, 180, 270) to standard EXIF / DNG orientation tags.
     */
    fun degreesToDngOrientation(degrees: Int): Int = when ((degrees % 360 + 360) % 360) {
        90 -> android.media.ExifInterface.ORIENTATION_ROTATE_90
        180 -> android.media.ExifInterface.ORIENTATION_ROTATE_180
        270 -> android.media.ExifInterface.ORIENTATION_ROTATE_270
        else -> android.media.ExifInterface.ORIENTATION_NORMAL
    }

    companion object {
        private const val TAG = "RawDngEngine"
    }
}
