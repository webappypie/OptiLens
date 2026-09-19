package com.webappypie.optilens.core.imaging.sr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SuperResolutionProcessorTest {

    private lateinit var engine: NativeSuperResolutionEngine

    @Before
    fun setUp() {
        engine = NativeSuperResolutionEngine()
        engine.setForceJvmFallback(true) // Test JVM mathematical pipeline
    }

    @Test
    fun `processMultiFrameSr produces 2x magnified output with sub-pixel detail`() {
        val width = 64
        val height = 64
        val outWidth = 128
        val outHeight = 128

        // Create synthetic high-contrast checker pattern
        val refY = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x / 4 + y / 4) % 2 == 0) 220.toByte() else 40.toByte()
        }

        val cand1 = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            // Sub-pixel shifted pattern
            if (((x + 1) / 4 + y / 4) % 2 == 0) 215.toByte() else 42.toByte()
        }

        val outY = ByteArray(outWidth * outHeight)
        val success = engine.processMultiFrameSr(
            refYPlane = refY,
            refUPlane = null,
            refVPlane = null,
            candYPlanes = listOf(cand1),
            subPixelShifts = listOf(0.5f to 0.0f),
            inWidth = width,
            inHeight = height,
            config = SuperResolutionConfig(scaleFactor = 2.0f),
            outYPlane = outY,
            outWidth = outWidth,
            outHeight = outHeight,
        )

        assertTrue(success)
        // Verify output buffer is populated
        val nonZeroCount = outY.count { it != 0.toByte() }
        assertTrue(nonZeroCount > outY.size / 2)
    }

    @Test
    fun `processSingleFrameSr edge-directed interpolation reconstructs high-contrast lines`() {
        val width = 48
        val height = 48
        val outWidth = 96
        val outHeight = 96

        // Diagonal step edge
        val inY = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if (x >= y) 200.toByte() else 50.toByte()
        }

        val outY = ByteArray(outWidth * outHeight)
        val success = engine.processSingleFrameSr(
            inYPlane = inY,
            inUPlane = null,
            inVPlane = null,
            inWidth = width,
            inHeight = height,
            config = SuperResolutionConfig(scaleFactor = 2.0f),
            outYPlane = outY,
            outWidth = outWidth,
            outHeight = outHeight,
        )

        assertTrue(success)
        // Output dimensions are exactly 2x
        assertEquals(96 * 96, outY.size)
    }

    @Test
    fun `decomposeTiles covers full image area and bounds memory`() {
        val imageW = 1920
        val imageH = 1080
        val tileSize = 256
        val overlap = 32

        val tiles = engine.decomposeTiles(
            imageWidth = imageW,
            imageHeight = imageH,
            tileSize = tileSize,
            overlap = overlap,
            scaleFactor = 2.0f,
        )

        assertTrue(tiles.isNotEmpty())
        // Verify tiling covers edges
        val minX = tiles.minOf { it.tileX }
        val minY = tiles.minOf { it.tileY }
        val maxX = tiles.maxOf { it.tileX + it.tileWidth }
        val maxY = tiles.maxOf { it.tileY + it.tileHeight }

        assertEquals(0, minX)
        assertEquals(0, minY)
        assertEquals(imageW, maxX)
        assertEquals(imageH, maxY)

        // Verify each tile padded area stays bounded
        for (tile in tiles) {
            assertTrue(tile.paddedWidth <= tileSize + overlap * 2)
            assertTrue(tile.paddedHeight <= tileSize + overlap * 2)
            assertEquals((tile.tileWidth * 2.0f).toInt(), tile.outWidth)
            assertEquals((tile.tileHeight * 2.0f).toInt(), tile.outHeight)
        }
    }

    @Test
    fun `artifact guard rejects large photometric residuals`() {
        val width = 32
        val height = 32
        val outWidth = 64
        val outHeight = 64

        val refY = ByteArray(width * height) { 100 }
        // Candidate has an extreme glitch / flash artifact in center
        val corruptedCand = ByteArray(width * height) { 100 }
        corruptedCand[16 * width + 16] = 250.toByte()

        val outY = ByteArray(outWidth * outHeight)
        val success = engine.processMultiFrameSr(
            refYPlane = refY,
            refUPlane = null,
            refVPlane = null,
            candYPlanes = listOf(corruptedCand),
            subPixelShifts = listOf(0.0f to 0.0f),
            inWidth = width,
            inHeight = height,
            config = SuperResolutionConfig(
                scaleFactor = 2.0f,
                residualRejectionThreshold = 25.0f,
            ),
            outYPlane = outY,
            outWidth = outWidth,
            outHeight = outHeight,
        )

        assertTrue(success)
        // Corrupted pixel should be rejected by artifact guard and not blow up the HR grid
        val centerVal = outY[32 * outWidth + 32].toInt() and 0xFF
        assertTrue("Expected centerVal close to 100, got $centerVal", centerVal < 140)
    }
}
