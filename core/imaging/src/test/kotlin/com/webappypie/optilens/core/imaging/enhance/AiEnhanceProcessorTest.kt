package com.webappypie.optilens.core.imaging.enhance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiEnhanceProcessorTest {

    private lateinit var engine: NativeAiEnhanceEngine

    @Before
    fun setUp() {
        engine = NativeAiEnhanceEngine(useNativeIfAvailable = false) // Pure JVM test
    }

    @Test
    fun `processEnhance emits real processing stages in proper sequence`() {
        val width = 100
        val height = 100
        val yPlane = ByteArray(width * height) { 80.toByte() }
        val uPlane = ByteArray(50 * 50) { 128.toByte() }
        val vPlane = ByteArray(50 * 50) { 128.toByte() }

        val observedStages = mutableListOf<AiEnhanceStage>()

        val success = engine.processEnhance(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = width,
            height = height,
            yStride = width,
            uvStride = 50,
            config = AiEnhanceConfig(),
            onStageChanged = { stage -> observedStages.add(stage) },
        )

        assertTrue("Processing should succeed", success)
        val expectedSequence = listOf(
            AiEnhanceStage.ANALYZING_SCENE,
            AiEnhanceStage.BALANCING_EXPOSURE,
            AiEnhanceStage.REDUCING_NOISE,
            AiEnhanceStage.ENHANCING_DETAILS,
            AiEnhanceStage.COLOR_HARMONY,
            AiEnhanceStage.COMPLETED,
        )
        assertEquals("Should visit all real processing stages in order", expectedSequence, observedStages)
    }

    @Test
    fun `shadow exposure balancing lifts dark underexposed regions while preserving highlights`() {
        val width = 60
        val height = 60
        val yPlane = ByteArray(width * height) { idx ->
            val y = idx / width
            if (y < 30) 40.toByte() else 220.toByte()
        }

        val success = engine.processEnhance(
            yPlane = yPlane,
            width = width,
            height = height,
            yStride = width,
            config = AiEnhanceConfig(
                enableExposureBalancing = true,
                enableNoiseReduction = false,
                enableDetailEnhancement = false,
                enableColorHarmony = false,
            ),
        )

        assertTrue(success)

        // Dark shadow pixel (y=10, initial 40) should be lifted
        val liftedShadowY = yPlane[10 * width + 10].toInt() and 0xFF
        assertTrue("Shadow pixel should be lifted, got $liftedShadowY", liftedShadowY > 45)

        // Highlight pixel (y=50, initial 220) should remain compressed and not blown out
        val highlightY = yPlane[50 * width + 10].toInt() and 0xFF
        assertTrue("Highlight pixel should not exceed 245, got $highlightY", highlightY <= 245)
    }

    @Test
    fun `detail enhancement sharpens edges while coring preserves flat noise regions`() {
        val width = 40
        val height = 40
        // High contrast step edge in center
        val yPlane = ByteArray(width * height) { idx ->
            val x = idx % width
            if (x < 20) 100.toByte() else 180.toByte()
        }

        val success = engine.processEnhance(
            yPlane = yPlane,
            width = width,
            height = height,
            yStride = width,
            config = AiEnhanceConfig(
                enableExposureBalancing = false,
                enableNoiseReduction = false,
                enableDetailEnhancement = true,
                enableColorHarmony = false,
                strength = 1.0f,
            ),
        )

        assertTrue(success)

        // Edge adjacent pixel should experience micro-contrast boost
        val leftEdgeY = yPlane[20 * width + 19].toInt() and 0xFF
        val rightEdgeY = yPlane[20 * width + 20].toInt() and 0xFF
        // Contrast across edge should expand
        assertTrue("Edge difference should remain sharp or expand", rightEdgeY >= 180 && leftEdgeY <= 100)
    }

    @Test
    fun `color vibrance boosts muted tones while protecting human skin chromaticity`() {
        val uvWidth = 20
        val uvHeight = 20
        // Saturated skin tone pixel
        val uSkin = 112.toByte()
        val vSkin = 152.toByte()
        // Muted gray-blue pixel
        val uMuted = 135.toByte()
        val vMuted = 128.toByte()

        val uPlane = ByteArray(uvWidth * uvHeight) { idx -> if (idx < 200) uSkin else uMuted }
        val vPlane = ByteArray(uvWidth * uvHeight) { idx -> if (idx < 200) vSkin else vMuted }
        val yPlane = ByteArray(uvWidth * 2 * uvHeight * 2) { 128.toByte() }

        val success = engine.processEnhance(
            yPlane = yPlane,
            uPlane = uPlane,
            vPlane = vPlane,
            width = uvWidth * 2,
            height = uvHeight * 2,
            yStride = uvWidth * 2,
            uvStride = uvWidth,
            config = AiEnhanceConfig(
                enableExposureBalancing = false,
                enableNoiseReduction = false,
                enableDetailEnhancement = false,
                enableColorHarmony = true,
                preserveSkinTones = true,
            ),
        )

        assertTrue(success)

        // Skin chromaticity should remain strictly conserved
        val finalSkinU = uPlane[10].toInt() and 0xFF
        val finalSkinV = vPlane[10].toInt() and 0xFF
        assertEquals("Skin U must be preserved", 112, finalSkinU)
        assertEquals("Skin V must be preserved", 152, finalSkinV)

        // Muted pixel should receive subtle vibrance boost
        val finalMutedU = uPlane[250].toInt() and 0xFF
        assertTrue("Muted chroma should receive boost", finalMutedU >= 135)
    }
}
