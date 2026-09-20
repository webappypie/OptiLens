package com.webappypie.optilens.core.camera

import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import android.graphics.Rect
import com.webappypie.optilens.core.camera.analysis.YuvPreprocessor
import com.webappypie.optilens.core.camera.model.HistogramMode
import com.webappypie.optilens.core.camera.model.LensMetadata
import com.webappypie.optilens.core.camera.model.RawCaptureFormat
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class ProCameraModeTest {

    private val controller = FakeCameraController()

    @Test
    fun `default pro state is completely automated 3A`() = runTest {
        val state = controller.proState.first()
        assertNull(state.iso)
        assertNull(state.shutterSpeedNanos)
        assertNull(state.focusDistanceDiopters)
        assertEquals(WhiteBalanceMode.AUTO, state.whiteBalanceMode)
        assertEquals(0, state.evIndex)
        assertFalse(state.isAnyManualActive)
        assertFalse(state.isRawEnabled)
        assertTrue(state.saveCompanionJpeg)
    }

    @Test
    fun `manual parameter adjustments update pro state and activate manual flag`() = runTest {
        controller.setIso(800)
        controller.setShutterSpeed(16_666_666L) // 1/60s
        controller.setFocusDistance(2.5f)
        controller.setWhiteBalance(WhiteBalanceMode.DAYLIGHT)

        val state = controller.proState.first()
        assertEquals(800, state.iso)
        assertEquals(16_666_666L, state.shutterSpeedNanos)
        assertEquals(2.5f, state.focusDistanceDiopters ?: 0f, 0.01f)
        assertEquals(WhiteBalanceMode.DAYLIGHT, state.whiteBalanceMode)
        assertTrue(state.isAnyManualActive)
    }

    @Test
    fun `resetProToAuto restores full automation immediately`() = runTest {
        controller.setIso(1600)
        controller.setShutterSpeed(1_000_000L)
        controller.setFocusDistance(5.0f)
        controller.setWhiteBalance(WhiteBalanceMode.INCANDESCENT)
        controller.setExposureCompensation(3)

        assertTrue(controller.proState.first().isAnyManualActive)

        controller.resetProToAuto()

        val resetState = controller.proState.first()
        assertNull(resetState.iso)
        assertNull(resetState.shutterSpeedNanos)
        assertNull(resetState.focusDistanceDiopters)
        assertEquals(WhiteBalanceMode.AUTO, resetState.whiteBalanceMode)
        assertEquals(0, resetState.evIndex)
        assertFalse(resetState.isAnyManualActive)
    }

    @Test
    fun `raw capture format and companion jpeg toggles work properly`() = runTest {
        controller.setRawCaptureEnabled(true)
        controller.setRawCaptureFormat(RawCaptureFormat.RAW_SENSOR)
        controller.setSaveCompanionJpeg(false)

        val state = controller.proState.first()
        assertTrue(state.isRawEnabled)
        assertEquals(RawCaptureFormat.RAW_SENSOR, state.rawFormat)
        assertFalse(state.saveCompanionJpeg)

        controller.setSaveCompanionJpeg(true)
        assertTrue(controller.proState.first().saveCompanionJpeg)

        controller.setRawCaptureEnabled(false)
        assertFalse(controller.proState.first().isRawEnabled)
    }

    @Test
    fun `visual aids peaking and zebra toggles update states`() = runTest {
        assertFalse(controller.focusPeakingData.first().isEnabled)
        assertFalse(controller.exposureZebraData.first().isEnabled)

        controller.setFocusPeakingEnabled(true)
        assertTrue(controller.focusPeakingData.first().isEnabled)
        assertTrue(controller.proState.first().focusPeakingEnabled)

        controller.setExposureZebraEnabled(true)
        assertTrue(controller.exposureZebraData.first().isEnabled)
        assertTrue(controller.proState.first().exposureZebraEnabled)

        controller.setFocusPeakingEnabled(false)
        controller.setExposureZebraEnabled(false)
        assertFalse(controller.focusPeakingData.first().isEnabled)
        assertFalse(controller.exposureZebraData.first().isEnabled)
    }

    @Test
    fun `histogram mode cycle updates pro state and histogram flow`() = runTest {
        assertEquals(HistogramMode.LUMINANCE, controller.proState.first().histogramMode)

        controller.setHistogramMode(HistogramMode.RGB)
        assertEquals(HistogramMode.RGB, controller.proState.first().histogramMode)

        controller.setHistogramMode(HistogramMode.BOTH)
        assertEquals(HistogramMode.BOTH, controller.proState.first().histogramMode)

        controller.setHistogramMode(HistogramMode.LUMINANCE)
        assertEquals(HistogramMode.LUMINANCE, controller.proState.first().histogramMode)
    }

    @Test
    fun `lens metadata readout formats accurately`() {
        val meta = LensMetadata(
            focalLengthMm = 24f,
            focalLength35mmEquivalent = 24,
            apertureFNumber = 1.8f,
            minFocusDistanceDiopters = 10f,
            currentFocusDistanceDiopters = 1f / 1.5f,
            currentIso = 400,
            currentShutterSpeedNanos = 8_000_000L, // 1/125s
            isFixedFocus = false,
        )

        val summary = meta.readoutSummary
        assertTrue(summary.contains("24mm eq"))
        assertTrue(summary.contains("f/1.8"))
        assertTrue(summary.contains("1.5m"))
        assertTrue(summary.contains("ISO 400"))
        assertTrue(summary.contains("1/125"))
    }

    @Test
    fun `yuv preprocessor computes 64-bin luminance and rgb histograms accurately`() {
        val width = 16
        val height = 16
        val yPlane = ByteBuffer.allocateDirect(width * height)
        val uPlane = ByteBuffer.allocateDirect((width / 2) * (height / 2))
        val vPlane = ByteBuffer.allocateDirect((width / 2) * (height / 2))

        // Populate mock bright frame (y = 200)
        for (i in 0 until (width * height)) {
            yPlane.put(200.toByte())
        }
        for (i in 0 until ((width / 2) * (height / 2))) {
            uPlane.put(128.toByte())
            vPlane.put(128.toByte())
        }
        yPlane.rewind()
        uPlane.rewind()
        vPlane.rewind()

        val mockImage = createFakeImageProxy(
            width = width,
            height = height,
            yBuffer = yPlane,
            uBuffer = uPlane,
            vBuffer = vPlane,
        )

        val preprocessor = YuvPreprocessor(gridWidth = width, gridHeight = height)
        val frameData = preprocessor.process(mockImage)

        assertEquals(64, frameData.histogramBins.size)
        assertEquals(64, frameData.redHistogramBins.size)
        assertEquals(64, frameData.greenHistogramBins.size)
        assertEquals(64, frameData.blueHistogramBins.size)

        // The peak should be in the upper bins (~ bin 50)
        val lumaPeakBin = frameData.histogramBins.indices.maxByOrNull { frameData.histogramBins[it] } ?: 0
        assertTrue("Luma peak bin should be in upper quarter", lumaPeakBin >= 40)
    }

    @Test
    fun `yuv preprocessor detects focus peaking edges and zebra clipped highlights`() {
        val width = 16
        val height = 16
        val yPlane = ByteBuffer.allocateDirect(width * height)
        val uPlane = ByteBuffer.allocateDirect((width / 2) * (height / 2))
        val vPlane = ByteBuffer.allocateDirect((width / 2) * (height / 2))

        // Create high-contrast alternating pattern (triggers spatial gradient)
        // with upper-left overexposed (255)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (x < 4 && y < 4) {
                    yPlane.put(255.toByte()) // Clipped highlight
                } else if (x % 2 == 0) {
                    yPlane.put(20.toByte())
                } else {
                    yPlane.put(220.toByte()) // Sharp edge
                }
            }
        }
        yPlane.rewind()
        for (i in 0 until ((width / 2) * (height / 2))) {
            uPlane.put(128.toByte())
            vPlane.put(128.toByte())
        }
        uPlane.rewind()
        vPlane.rewind()

        val mockImage = createFakeImageProxy(
            width = width,
            height = height,
            yBuffer = yPlane,
            uBuffer = uPlane,
            vBuffer = vPlane,
        )

        val preprocessor = YuvPreprocessor(gridWidth = width, gridHeight = height)
        val frameData = preprocessor.process(mockImage)

        assertTrue("Focus peaking should detect sharp alternating edges", frameData.focusPeakingPoints.isNotEmpty())
        assertTrue("Exposure zebra should detect overexposed clipped highlights", frameData.exposureZebraRegions.isNotEmpty())
    }

    @Test
    fun `concurrent stress testing of Pro reconfiguration maintains consistency`() = runTest {
        // Concurrently adjust ISO, Shutter, Focus, WB, RAW, and visual aids from 50 coroutines
        val jobs = (0 until 50).map { i ->
            async(Dispatchers.Default) {
                controller.setIso(100 + (i % 8) * 100)
                controller.setShutterSpeed(1_000_000L * ((i % 10) + 1))
                controller.setFocusDistance((i % 10).toFloat())
                controller.setWhiteBalance(WhiteBalanceMode.entries[i % WhiteBalanceMode.entries.size])
                if (i % 2 == 0) {
                    controller.setRawCaptureEnabled(true)
                    controller.setRawCaptureFormat(RawCaptureFormat.RAW_SENSOR)
                    controller.setFocusPeakingEnabled(true)
                } else {
                    controller.setRawCaptureEnabled(false)
                    controller.setFocusPeakingEnabled(false)
                }
            }
        }
        jobs.awaitAll()

        val finalState = controller.proState.first()
        assertNotNull(finalState.iso)
        assertNotNull(finalState.shutterSpeedNanos)
        assertNotNull(finalState.focusDistanceDiopters)
        assertNotNull(finalState.whiteBalanceMode)
    }

    private fun createFakeImageProxy(
        width: Int,
        height: Int,
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
    ): ImageProxy {
        val yPlane = object : ImageProxy.PlaneProxy {
            override fun getRowStride(): Int = width
            override fun getPixelStride(): Int = 1
            override fun getBuffer(): ByteBuffer = yBuffer
        }
        val uPlane = object : ImageProxy.PlaneProxy {
            override fun getRowStride(): Int = width / 2
            override fun getPixelStride(): Int = 1
            override fun getBuffer(): ByteBuffer = uBuffer
        }
        val vPlane = object : ImageProxy.PlaneProxy {
            override fun getRowStride(): Int = width / 2
            override fun getPixelStride(): Int = 1
            override fun getBuffer(): ByteBuffer = vBuffer
        }

        return object : ImageProxy {
            override fun getWidth(): Int = width
            override fun getHeight(): Int = height
            override fun getFormat(): Int = android.graphics.ImageFormat.YUV_420_888
            override fun getPlanes(): Array<ImageProxy.PlaneProxy> = arrayOf(yPlane, uPlane, vPlane)
            override fun getCropRect(): Rect = Rect(0, 0, width, height)
            override fun setCropRect(rect: Rect?) {}
            override fun getImageInfo(): ImageInfo = throw UnsupportedOperationException()
            override fun getImage(): android.media.Image? = null
            override fun close() {}
        }
    }
}
