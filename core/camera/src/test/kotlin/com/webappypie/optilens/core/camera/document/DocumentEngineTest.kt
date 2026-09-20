package com.webappypie.optilens.core.camera.document

import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DocumentEngineTest {

    private lateinit var documentEngine: DocumentEngine
    private lateinit var ocrEngine: DocumentOcrEngine

    @Before
    fun setUp() {
        documentEngine = DocumentEngine()
        ocrEngine = DocumentOcrEngine()
    }

    @Test
    fun `detectPageQuad returns valid quad on synthetic page buffer`() {
        val width = 64
        val height = 64
        // Background dark (40 luma)
        val yPlane = ByteArray(width * height) { 40.toByte() }

        // White document sheet in center (180 luma)
        for (y in 10 until 54) {
            val row = y * width
            for (x in 10 until 54) {
                yPlane[row + x] = 180.toByte()
            }
        }

        val quad = documentEngine.detectPageQuad(yPlane, width, height)
        assertTrue("Quad must be valid", quad.isValid)
        assertTrue("Top-left X (${quad.topLeft.x}) should be < top-right X (${quad.topRight.x})", quad.topLeft.x < quad.topRight.x)
        assertTrue("Top-left Y (${quad.topLeft.y}) should be < bottom-left Y (${quad.bottomLeft.y})", quad.topLeft.y < quad.bottomLeft.y)
    }

    @Test
    fun `warpPerspective resamples quadrilateral into rectangular grid`() {
        val inW = 32
        val inH = 32
        val outW = 16
        val outH = 16
        val inputY = ByteArray(inW * inH) { 150.toByte() }

        val quad = DocumentQuad(
            topLeft = DocumentPoint(0.1f, 0.1f),
            topRight = DocumentPoint(0.9f, 0.1f),
            bottomRight = DocumentPoint(0.9f, 0.9f),
            bottomLeft = DocumentPoint(0.1f, 0.9f),
        )

        val warped = documentEngine.warpPerspective(inputY, inW, inH, quad, outW, outH)
        assertEquals(outW * outH, warped.size)
        // All pixels sampled from 150 should be 150
        assertEquals(150.toByte(), warped[0])
    }

    @Test
    fun `normalizeIllumination flattens shadow gradient across document`() {
        val width = 32
        val height = 32
        // Create a strong illumination gradient (shadow on left side: 40 luma, bright on right side: 200 luma)
        val shadowY = ByteArray(width * height) { idx ->
            val x = idx % width
            (40 + (x * 5)).coerceIn(0, 255).toByte()
        }

        val normalized = documentEngine.normalizeIllumination(shadowY, width, height)
        assertEquals(shadowY.size, normalized.size)

        // Across the normalized sheet, the relative difference between left and right should be significantly compressed
        val leftPixel = normalized[16 * width + 2].toInt() and 0xFF
        val rightPixel = normalized[16 * width + 30].toInt() and 0xFF
        val shadowDelta = kotlin.math.abs((shadowY[16 * width + 30].toInt() and 0xFF) - (shadowY[16 * width + 2].toInt() and 0xFF))
        val normalizedDelta = kotlin.math.abs(rightPixel - leftPixel)

        assertTrue("Normalized gradient ($normalizedDelta) should be smaller than raw shadow gradient ($shadowDelta)",
            normalizedDelta < shadowDelta)
    }

    @Test
    fun `binarizeBlackAndWhite outputs strictly 0 or 255 pixels`() {
        val width = 16
        val height = 16
        val input = ByteArray(width * height) { idx ->
            if (idx % 2 == 0) 30.toByte() else 210.toByte()
        }

        val binary = documentEngine.binarizeBlackAndWhite(input, width, height)
        for (b in binary) {
            val v = b.toInt() and 0xFF
            assertTrue("Binary pixel ($v) must be either 0 or 255", v == 0 || v == 255)
        }
    }

    @Test
    fun `processDocument handles COLOR, GRAYSCALE, and BLACK_AND_WHITE modes`() {
        val width = 16
        val height = 16
        val y = ByteArray(width * height) { 150.toByte() }
        val u = ByteArray((width / 2) * (height / 2)) { 130.toByte() }
        val v = ByteArray((width / 2) * (height / 2)) { 140.toByte() }

        // COLOR
        val colorResult = documentEngine.processDocument(y, u, v, width, height, colorMode = DocumentColorMode.COLOR)
        assertEquals(DocumentColorMode.COLOR, colorResult.colorMode)
        assertNotNull(colorResult.uPlane)
        assertNotNull(colorResult.vPlane)

        // GRAYSCALE
        val grayResult = documentEngine.processDocument(y, u, v, width, height, colorMode = DocumentColorMode.GRAYSCALE)
        assertEquals(DocumentColorMode.GRAYSCALE, grayResult.colorMode)
        assertNotNull(grayResult.uPlane)
        // Grayscale has neutral UV (128)
        assertEquals(128.toByte(), grayResult.uPlane!![0])

        // BLACK_AND_WHITE
        val bwResult = documentEngine.processDocument(y, u, v, width, height, colorMode = DocumentColorMode.BLACK_AND_WHITE)
        assertEquals(DocumentColorMode.BLACK_AND_WHITE, bwResult.colorMode)
        for (b in bwResult.yPlane) {
            val p = b.toInt() and 0xFF
            assertTrue(p == 0 || p == 255)
        }
    }

    @Test
    fun `decoupled OCR extracts text only when explicitly invoked`() = runTest {
        // Test invalid uri
        val errorResult = ocrEngine.extractTextFromUri("")
        assertTrue(errorResult is OptiResult.Error)

        // Test valid uri on-demand extraction
        val validResult = ocrEngine.extractTextFromUri("content://media/external/images/media/42")
        assertTrue(validResult is OptiResult.Success)
        val text = (validResult as OptiResult.Success).data
        assertTrue(text.contains("OptiLens Document OCR Text"))
        assertTrue(text.contains("Decoupled On-Demand Recognition"))

        // Buffer extraction
        val lumaBuffer = ByteArray(32 * 32) { 128.toByte() }
        val bufferResult = ocrEngine.extractTextFromLuma(lumaBuffer, 32, 32)
        assertTrue(bufferResult is OptiResult.Success)
    }
}
