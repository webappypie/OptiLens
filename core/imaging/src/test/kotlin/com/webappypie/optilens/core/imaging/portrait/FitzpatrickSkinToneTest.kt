package com.webappypie.optilens.core.imaging.portrait

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression test suite verifying chromatic skin tone model fidelity across
 * all six Fitzpatrick phototypes (Types I to VI) and negative non-skin chromatic controls.
 */
class FitzpatrickSkinToneTest {

    private lateinit var engine: NativePortraitEngine

    @Before
    fun setUp() {
        engine = NativePortraitEngine()
    }

    @Test
    fun `fitzpatrick type I and II fair pale skin produces high skin probability`() {
        // Fitzpatrick I/II: Fair / Nordic / East Asian fair tones
        val u = 106.0f
        val v = 156.0f

        val pSkin = engine.computeSkinProbability(u, v)

        assertTrue(
            "Fitzpatrick I/II probability should be >= 0.70, was $pSkin",
            pSkin >= 0.70f
        )
    }

    @Test
    fun `fitzpatrick type III and IV medium olive skin produces high skin probability`() {
        // Fitzpatrick III/IV: Mediterranean, Hispanic, South Asian medium tones
        val u = 112.0f
        val v = 151.0f

        val pSkin = engine.computeSkinProbability(u, v)

        assertTrue(
            "Fitzpatrick III/IV probability should be >= 0.85, was $pSkin",
            pSkin >= 0.85f
        )
    }

    @Test
    fun `fitzpatrick type V and VI deep melanin brown skin produces high skin probability`() {
        // Fitzpatrick V/VI: South Asian deep, Afro-Caribbean, African deep melanin tones
        val u = 120.0f
        val v = 145.0f

        val pSkin = engine.computeSkinProbability(u, v)

        assertTrue(
            "Fitzpatrick V/VI probability should be >= 0.70, was $pSkin",
            pSkin >= 0.70f
        )
    }

    @Test
    fun `non-skin chroma samples produce near-zero skin probability`() {
        // 1. Blue sky (U=180, V=90)
        val pBlueSky = engine.computeSkinProbability(180.0f, 90.0f)
        assertTrue("Blue sky probability must be < 0.05, was $pBlueSky", pBlueSky < 0.05f)

        // 2. Green vegetation (U=85, V=85)
        val pGreenFoliage = engine.computeSkinProbability(85.0f, 85.0f)
        assertTrue("Green foliage probability must be < 0.05, was $pGreenFoliage", pGreenFoliage < 0.05f)

        // 3. Red stop sign (U=60, V=230)
        val pRedSign = engine.computeSkinProbability(60.0f, 230.0f)
        assertTrue("Saturated red sign probability must be < 0.05, was $pRedSign", pRedSign < 0.05f)
    }
}
