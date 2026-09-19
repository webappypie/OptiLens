package com.webappypie.optilens.core.imaging.alignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GhostMaskTest {

    @Test
    fun `empty mask has zero coverage and no significant motion`() {
        val mask = GhostMask.empty(100, 100)
        assertEquals(100, mask.width)
        assertEquals(100, mask.height)
        assertEquals(0.0f, mask.coverageFraction, 0.001f)
        assertFalse(mask.hasSignificantMotion)
        assertFalse(mask.isPixelMoving(50, 50))
    }

    @Test
    fun `mask correctly classifies moving pixels and evaluates coverage`() {
        val width = 10
        val height = 10
        val bytes = ByteArray(width * height)

        // Mark 10 pixels out of 100 as moving (10%)
        for (i in 0 until 10) {
            bytes[i] = 255.toByte()
        }

        val mask = GhostMask(
            width = width,
            height = height,
            maskBytes = bytes,
            coverageFraction = 0.10f,
        )

        assertTrue(mask.hasSignificantMotion)
        assertTrue(mask.isPixelMoving(0, 0))
        assertFalse(mask.isPixelMoving(5, 5))
    }
}
