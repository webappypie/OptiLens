package com.webappypie.optilens.core.imaging.portrait

import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.FaceLandmarkPoint
import com.webappypie.optilens.core.camera.model.LandmarkType
import com.webappypie.optilens.core.camera.model.NormalizedRect
import com.webappypie.optilens.core.camera.portrait.PortraitAperture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PortraitProcessorTest {

    private lateinit var engine: NativePortraitEngine

    @Before
    fun setUp() {
        engine = NativePortraitEngine()
    }

    @Test
    fun `face exposure balancing lifts backlit face without clipping`() {
        val width = 100
        val height = 100
        // Background bright (220), face area dark (50)
        val yPlane = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (x in 30..70 && y in 30..70) 50.toByte() else 220.toByte()
        }
        val uPlane = ByteArray(50 * 50) { 112.toByte() }
        val vPlane = ByteArray(50 * 50) { 152.toByte() }

        val face = DetectedFace(
            bounds = NormalizedRect(0.3f, 0.3f, 0.7f, 0.7f),
            meanLuminance = 50.0f,
        )

        val config = PortraitConfig(
            aperture = PortraitAperture.F8_0, // Depth of field disabled for exposure-only test
            skinSmoothingStrength = 0.0f,
            faceEvCompensation = 1.0f,
            isBacklit = true,
            faces = listOf(face),
        )

        val initialFaceCenterY = yPlane[50 * width + 50].toInt() and 0xFF
        assertEquals(50, initialFaceCenterY)

        val success = engine.processPortrait(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
            yStride = width,
            uvStride = 50,
            config = config,
        )

        assertTrue(success)
        val liftedFaceCenterY = yPlane[50 * width + 50].toInt() and 0xFF
        // Face center luminance should be lifted (> 50)
        assertTrue("Face center should be lifted, was $liftedFaceCenterY", liftedFaceCenterY > 65)
        // Background pixels outside face should remain near 220
        val backgroundY = yPlane[5 * width + 5].toInt() and 0xFF
        assertEquals(220, backgroundY)
    }

    @Test
    fun `skin smoothing softens skin while protecting eye landmarks`() {
        val width = 100
        val height = 100
        // Face with alternating noisy skin texture (roughness)
        val yPlane = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (x in 30..70 && y in 30..70) {
                if ((x + y) % 2 == 0) 140.toByte() else 100.toByte()
            } else {
                120.toByte()
            }
        }
        // Human skin tone chroma in face area
        val uPlane = ByteArray(50 * 50) { 112.toByte() }
        val vPlane = ByteArray(50 * 50) { 152.toByte() }

        // Eye landmark at center (x=50, y=50)
        val eyeLandmark = FaceLandmarkPoint(LandmarkType.LEFT_EYE, 0.50f, 0.50f)
        val face = DetectedFace(
            bounds = NormalizedRect(0.3f, 0.3f, 0.7f, 0.7f),
            landmarks = listOf(eyeLandmark),
        )

        val config = PortraitConfig(
            aperture = PortraitAperture.F8_0,
            skinSmoothingStrength = 0.50f,
            enableDetailProtection = true,
            enableEyeSparkle = false,
            faces = listOf(face),
        )

        val success = engine.processPortrait(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
            yStride = width,
            uvStride = 50,
            config = config,
        )

        assertTrue(success)

        // Cheek skin pixel away from eye (x=35, y=35) should be smoothed
        val smoothedCheekY = yPlane[35 * width + 35].toInt() and 0xFF
        // Bilateral filter converges noisy 140/100 texture towards mean ~120
        assertTrue("Cheek should be smoothed towards mean, got $smoothedCheekY", smoothedCheekY in 110..130)

        // Eye pixel at (50, 50) must be protected by landmark protection
        val protectedEyeY = yPlane[50 * width + 50].toInt() and 0xFF
        assertTrue("Protected eye pixel should remain intact, got $protectedEyeY", protectedEyeY in 135..140)
    }

    @Test
    fun `optical disc bokeh convolves background while keeping subject sharp`() {
        val width = 100
        val height = 100
        // Sharp high-contrast pattern across the whole frame
        val yPlane = ByteArray(width * height) { idx ->
            val x = idx % width
            if (x % 4 == 0) 240.toByte() else 20.toByte()
        }
        val uPlane = ByteArray(50 * 50) { 128.toByte() }
        val vPlane = ByteArray(50 * 50) { 128.toByte() }

        // Face in center
        val face = DetectedFace(
            bounds = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f),
        )

        val config = PortraitConfig(
            aperture = PortraitAperture.F1_4, // Wide aperture disc blur
            skinSmoothingStrength = 0.0f,
            faces = listOf(face),
        )

        val faceCenterBefore = yPlane[50 * width + 52].toInt() and 0xFF // x=52 % 4 == 0 -> 240
        assertEquals(240, faceCenterBefore)

        val success = engine.processPortrait(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
            yStride = width,
            uvStride = 50,
            config = config,
        )

        assertTrue(success)

        // Face subject (in focus) retains sharp contrast
        val faceCenterAfter = yPlane[50 * width + 52].toInt() and 0xFF
        assertEquals(240, faceCenterAfter)

        // Background (x=12, y=10) should be convolved by optical disc
        val bgPixelAfter = yPlane[10 * width + 12].toInt() and 0xFF
        assertTrue("Background should be convolved into blur, was $bgPixelAfter", bgPixelAfter < 200)
    }
}
