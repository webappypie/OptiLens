package com.webappypie.optilens.core.camera.raw

import android.media.ExifInterface
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.PlatformRawCapabilities
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import com.webappypie.optilens.core.camera.model.SensorArrayInfo
import com.webappypie.optilens.core.logging.NoOpLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawDngEngineTest {

    private val engine = RawDngEngine(
        logger = NoOpLogger(),
    )

    private val profileWithRaw = CameraDeviceProfile(
        id = "0",
        lensFacing = LensFacing.BACK,
        hardwareLevel = CameraHardwareLevel.FULL,
        sensorInfo = SensorArrayInfo(
            physicalWidthMm = 6.4f,
            physicalHeightMm = 4.8f,
            pixelArrayWidth = 4000,
            pixelArrayHeight = 3000,
        ),
        rawCapabilities = PlatformRawCapabilities(
            supportsRawSensor = true,
            supportsRaw10 = true,
            supportsRaw12 = false,
            supportsRawPrivate = false,
        ),
    )

    private val profileWithoutRaw = CameraDeviceProfile(
        id = "1",
        lensFacing = LensFacing.FRONT,
        hardwareLevel = CameraHardwareLevel.LIMITED,
        sensorInfo = SensorArrayInfo(
            physicalWidthMm = 4.0f,
            physicalHeightMm = 3.0f,
            pixelArrayWidth = 1920,
            pixelArrayHeight = 1080,
        ),
        rawCapabilities = PlatformRawCapabilities(
            supportsRawSensor = false,
            supportsRaw10 = false,
            supportsRaw12 = false,
            supportsRawPrivate = false,
        ),
    )

    @Test
    fun `isFormatSupported correctly checks hardware profile capabilities`() {
        assertTrue(engine.isFormatSupported(RawCaptureFormat.RAW_SENSOR, profileWithRaw))
        assertTrue(engine.isFormatSupported(RawCaptureFormat.RAW10, profileWithRaw))
        assertFalse(engine.isFormatSupported(RawCaptureFormat.RAW12, profileWithRaw))
        assertFalse(engine.isFormatSupported(RawCaptureFormat.RAW_PRIVATE, profileWithRaw))

        assertFalse(engine.isFormatSupported(RawCaptureFormat.RAW_SENSOR, profileWithoutRaw))
        assertFalse(engine.isFormatSupported(RawCaptureFormat.RAW10, profileWithoutRaw))
    }

    @Test
    fun `getSupportedFormats returns matching format list`() {
        val supported = engine.getSupportedFormats(profileWithRaw)
        assertEquals(2, supported.size)
        assertTrue(supported.contains(RawCaptureFormat.RAW_SENSOR))
        assertTrue(supported.contains(RawCaptureFormat.RAW10))

        val none = engine.getSupportedFormats(profileWithoutRaw)
        assertTrue(none.isEmpty())
    }

    @Test
    fun `degreesToDngOrientation converts rotation to standard EXIF tags`() {
        assertEquals(ExifInterface.ORIENTATION_NORMAL, engine.degreesToDngOrientation(0))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, engine.degreesToDngOrientation(90))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_180, engine.degreesToDngOrientation(180))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_270, engine.degreesToDngOrientation(270))
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90, engine.degreesToDngOrientation(450))
    }

    @Test
    fun `format extensions and mime types conform to photographic standards`() {
        assertEquals("dng", RawCaptureFormat.RAW_SENSOR.extension)
        assertEquals("raw10", RawCaptureFormat.RAW10.extension)
        assertEquals("raw12", RawCaptureFormat.RAW12.extension)
        assertEquals("raw", RawCaptureFormat.RAW_PRIVATE.extension)

        assertEquals("image/x-adobe-dng", RawCaptureFormat.RAW_SENSOR.mimeType)
        assertEquals("image/x-raw-sensor", RawCaptureFormat.RAW10.mimeType)
    }
}
