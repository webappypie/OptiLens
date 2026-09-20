package com.webappypie.optilens.core.camera.bestshot

import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BestShotScorerTest {

    private lateinit var scorer: BestShotScorer

    @Before
    fun setUp() {
        scorer = BestShotScorer()
    }

    @Test
    fun `sharpness of textured pattern is higher than uniform plane`() {
        val width = 64
        val height = 64

        // Flat uniform buffer
        val flatPlane = ByteArray(width * height) { 128.toByte() }
        val flatSharpness = scorer.computeSharpness(flatPlane, width, height)

        // Checkerboard / high-frequency pattern
        val texturedPlane = ByteArray(width * height) { idx ->
            val x = idx % width
            val y = idx / width
            if ((x / 4 + y / 4) % 2 == 0) 220.toByte() else 30.toByte()
        }
        val texturedSharpness = scorer.computeSharpness(texturedPlane, width, height)

        assertEquals(0.0f, flatSharpness, 0.01f)
        assertTrue("Textured sharpness ($texturedSharpness) should be > 50", texturedSharpness > 50.0f)
    }

    @Test
    fun `motion stability penalizes high gyro angular speed and inter-frame difference`() {
        val width = 32
        val height = 32
        val frameA = ByteArray(width * height) { 100.toByte() }
        val frameBIdentical = ByteArray(width * height) { 100.toByte() }
        val frameCDifferent = ByteArray(width * height) { 200.toByte() }

        // Stationary gyro, identical frame
        val staticStability = scorer.computeMotionStability(
            yPlane = frameA,
            adjacentYPlane = frameBIdentical,
            width = width,
            height = height,
            gyroAngularSpeed = 0.0f
        )
        assertEquals(100.0f, staticStability, 0.5f)

        // High gyro angular velocity
        val gyroMovingStability = scorer.computeMotionStability(
            yPlane = frameA,
            adjacentYPlane = frameBIdentical,
            width = width,
            height = height,
            gyroAngularSpeed = 0.25f // fast rotation
        )
        assertTrue("High gyro angular velocity should penalize stability", gyroMovingStability < staticStability)

        // High inter-frame disparity (subject motion)
        val subjectMovingStability = scorer.computeMotionStability(
            yPlane = frameA,
            adjacentYPlane = frameCDifferent,
            width = width,
            height = height,
            gyroAngularSpeed = 0.0f
        )
        assertTrue("Inter-frame difference should penalize stability", subjectMovingStability < staticStability)
    }

    @Test
    fun `exposure score favors balanced mid-tones and penalizes clipped buffers`() {
        val width = 32
        val height = 32

        // Ideal mid-tone: 120 luma
        val balancedPlane = ByteArray(width * height) { 120.toByte() }
        val balancedScore = scorer.computeExposureScore(balancedPlane, width, height)

        // Over-exposed / clipped highlights: 250 luma
        val clippedWhitePlane = ByteArray(width * height) { 250.toByte() }
        val whiteScore = scorer.computeExposureScore(clippedWhitePlane, width, height)

        // Under-exposed / crushed shadows: 5 luma
        val crushedBlackPlane = ByteArray(width * height) { 5.toByte() }
        val blackScore = scorer.computeExposureScore(crushedBlackPlane, width, height)

        assertTrue("Balanced exposure ($balancedScore) should be > 90", balancedScore > 90.0f)
        assertTrue("Clipped white score ($whiteScore) should be < 50", whiteScore < 50.0f)
        assertTrue("Crushed black score ($blackScore) should be < 50", blackScore < 50.0f)
    }

    @Test
    fun `eye openness evaluates open eyes positively and flags blinks in confident faces`() {
        val faceOpen = DetectedFace(
            id = 1,
            bounds = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
            confidence = 0.95f,
            leftEyeOpenProbability = 0.92f,
            rightEyeOpenProbability = 0.90f
        )
        val faceBlinking = DetectedFace(
            id = 2,
            bounds = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
            confidence = 0.95f,
            leftEyeOpenProbability = 0.05f,
            rightEyeOpenProbability = 0.08f
        )
        val faceLowConfidence = DetectedFace(
            id = 3,
            bounds = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
            confidence = 0.40f, // below 0.70 threshold
            leftEyeOpenProbability = 0.95f,
            rightEyeOpenProbability = 0.95f
        )

        // High confidence open eyes
        val openScore = scorer.evaluateEyeOpenness(listOf(faceOpen))
        assertNotNull(openScore)
        assertTrue("Open eyes score ($openScore) should be >= 0.85", openScore!! >= 0.85f)

        // Blinking face
        val blinkScore = scorer.evaluateEyeOpenness(listOf(faceBlinking))
        assertNotNull(blinkScore)
        assertTrue("Blinking eye score ($blinkScore) should be <= 0.15", blinkScore!! <= 0.15f)

        // Low confidence faces are ignored to prevent false blinks
        val lowConfScore = scorer.evaluateEyeOpenness(listOf(faceLowConfidence))
        assertNull("Low confidence face should return null eye score", lowConfScore)

        // Multi-person group: if one blinks, group score is heavily penalized
        val groupScore = scorer.evaluateEyeOpenness(listOf(faceOpen, faceBlinking))
        assertNotNull(groupScore)
        assertTrue("Group with one blinking person should be penalized ($groupScore)", groupScore!! < 0.5f)
    }

    @Test
    fun `composite score correctly incorporates eye openness when confident`() {
        val width = 32
        val height = 32
        val frame = ByteArray(width * height) { 120.toByte() }

        val faceOpen = DetectedFace(
            id = 1,
            bounds = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f),
            confidence = 0.95f,
            leftEyeOpenProbability = 0.95f,
            rightEyeOpenProbability = 0.95f
        )

        val scoresWithEyes = scorer.scoreFrame(
            yPlane = frame,
            width = width,
            height = height,
            detectedFaces = listOf(faceOpen)
        )
        assertNotNull(scoresWithEyes.eyeOpenScore)
        assertTrue(scoresWithEyes.compositeScore > 0f)

        val scoresWithoutFaces = scorer.scoreFrame(
            yPlane = frame,
            width = width,
            height = height,
            detectedFaces = emptyList()
        )
        assertNull(scoresWithoutFaces.eyeOpenScore)
        assertTrue(scoresWithoutFaces.compositeScore > 0f)
    }

    @Test
    fun `best shot result supports manual override without synthetic face synthesis`() {
        val candidates = listOf(
            BestShotCandidate(index = 0, compositeScore = 75f, ranking = 2, isAlternate = true),
            BestShotCandidate(index = 1, compositeScore = 92f, ranking = 1, isBest = true),
            BestShotCandidate(index = 2, compositeScore = 60f, ranking = 3, isAlternate = true)
        )
        val result = BestShotResult(
            candidates = candidates,
            bestIndex = 1,
            selectedIndex = 1
        )

        assertEquals(1, result.bestIndex)
        assertEquals(1, result.selectedIndex)
        assertEquals(1, result.selectedCandidate?.index)
        assertEquals(2, result.alternates.size)

        // User manually overrides top shot to candidate index 0
        val overridden = result.withManualOverride(0)
        assertEquals(1, overridden.bestIndex) // recommended index remains unchanged
        assertEquals(0, overridden.selectedIndex) // selected keeper overridden
        assertEquals(0, overridden.selectedCandidate?.index)
        assertEquals(1, overridden.recommendedCandidate?.index)
        assertFalse(overridden.selectedCandidate!!.isBest)
        assertTrue(overridden.selectedCandidate!!.isAlternate)
    }
}
