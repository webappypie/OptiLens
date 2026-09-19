package com.webappypie.optilens.core.camera.analysis.motion

import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectMotionEstimatorTest {

    private val estimator = SubjectMotionEstimator()

    @Test
    fun estimateMotion_identicalSequentialFrames_reportsStatic() {
        val grid = IntArray(160 * 120) { 128 }

        // Frame 1
        val m1 = estimator.estimateMotion(grid, 0.0f, 1000L)
        assertEquals(SubjectMotionLevel.STATIC, m1.subjectMotionLevel)
        assertEquals(0.0f, m1.subjectMotionScore, 0.01f)

        // Frame 2 (identical)
        val m2 = estimator.estimateMotion(grid, 0.0f, 1066L)
        assertEquals(SubjectMotionLevel.STATIC, m2.subjectMotionLevel)
        assertEquals(0.0f, m2.subjectMotionScore, 0.01f)
    }

    @Test
    fun estimateMotion_significantDifferenceWithoutGyro_reportsMotion() {
        val grid1 = IntArray(160 * 120) { 50 }
        val grid2 = IntArray(160 * 120) { 150 } // Big delta across entire frame

        estimator.estimateMotion(grid1, 0.0f, 1000L)
        val m2 = estimator.estimateMotion(grid2, 0.0f, 1066L)

        assertEquals(SubjectMotionLevel.HIGH_MOTION, m2.subjectMotionLevel)
        assertTrue(m2.subjectMotionScore > 0.5f)
    }

    @Test
    fun estimateMotion_gyroVelocity_updatesShakeLevel() {
        val grid = IntArray(160 * 120) { 100 }

        val m = estimator.estimateMotion(grid, 0.35f, 1000L)
        assertEquals(CameraShakeLevel.HIGH, m.cameraShakeLevel)
        assertTrue(m.isCameraShaking)
    }
}
