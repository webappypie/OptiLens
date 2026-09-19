package com.webappypie.optilens.core.imaging.alignment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomographyMatrixTest {

    @Test
    fun `identity matrix leaves coordinates unchanged`() {
        val identity = HomographyMatrix.IDENTITY
        assertTrue(identity.isIdentity)

        val (tx, ty) = identity.transformPoint(150.0f, 220.0f)
        assertEquals(150.0f, tx, 0.001f)
        assertEquals(220.0f, ty, 0.001f)
    }

    @Test
    fun `translation matrix shifts coordinates correctly`() {
        val trans = HomographyMatrix.translation(12.5f, -8.0f)
        assertFalse(trans.isIdentity)
        assertEquals(12.5f, trans.translationX, 0.001f)
        assertEquals(-8.0f, trans.translationY, 0.001f)

        val (tx, ty) = trans.transformPoint(100.0f, 50.0f)
        assertEquals(112.5f, tx, 0.001f)
        assertEquals(42.0f, ty, 0.001f)
    }

    @Test
    fun `invert inverts valid transformation matrix`() {
        val trans = HomographyMatrix.translation(10.0f, 20.0f)
        val inv = trans.invert()
        assertNotNull(inv)

        val (tx, ty) = trans.transformPoint(50.0f, 50.0f)
        val (origX, origY) = inv!!.transformPoint(tx, ty)

        assertEquals(50.0f, origX, 0.001f)
        assertEquals(50.0f, origY, 0.001f)
    }

    @Test
    fun `invert returns null for singular matrix`() {
        val singular = HomographyMatrix(FloatArray(9) { 0f })
        assertNull(singular.invert())
    }
}
