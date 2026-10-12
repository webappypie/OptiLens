package com.webappypie.optilens.core.imaging.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpscaleBudgetTest {

    @Test
    fun `small images allow both factors`() {
        // 1000 x 750 -> 4x = 12 MP output
        assertEquals(listOf(2, 4), UpscaleBudget.allowedFactors(1000, 750))
    }

    @Test
    fun `mid-size images allow only 2x`() {
        // 2000 x 1500 -> 2x = 12 MP, 4x = 48 MP
        assertEquals(listOf(2), UpscaleBudget.allowedFactors(2000, 1500))
    }

    @Test
    fun `a 12 MP camera photo cannot be upscaled within the budget`() {
        // 4000 x 3000 -> 2x = 48 MP, which would need ~192 MB per ARGB buffer
        assertTrue(UpscaleBudget.allowedFactors(4000, 3000).isEmpty())
    }

    @Test
    fun `boundary is inclusive and invalid input is rejected`() {
        assertTrue(UpscaleBudget.isAllowed(3000, 2000, 2)) // exactly 24 MP
        assertFalse(UpscaleBudget.isAllowed(3001, 2000, 2))
        assertFalse(UpscaleBudget.isAllowed(0, 100, 2))
        assertFalse(UpscaleBudget.isAllowed(100, 100, 3))
    }
}
