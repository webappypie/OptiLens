package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.SceneType
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneStabilizerTest {

    @Test
    fun stabilize_singleOutlierFrame_doesNotFlipActiveScene() {
        val stabilizer = SceneStabilizer(windowSize = 7, transitionThreshold = 4)

        // Feed 5 PORTRAIT frames
        for (i in 0 until 5) {
            val result = stabilizer.stabilize(SceneClassification(primaryScene = SceneType.PORTRAIT, confidence = 0.9f))
            assertEquals(SceneType.PORTRAIT, result.primaryScene)
        }

        // Single noisy outlier FOOD frame
        val outlierResult = stabilizer.stabilize(SceneClassification(primaryScene = SceneType.FOOD, confidence = 0.8f))
        // Must remain PORTRAIT due to hysteresis
        assertEquals(SceneType.PORTRAIT, outlierResult.primaryScene)
    }

    @Test
    fun stabilize_sustainedNewScene_transitionsAfterThreshold() {
        val stabilizer = SceneStabilizer(windowSize = 7, transitionThreshold = 4)

        // 4 PORTRAIT frames
        for (i in 0 until 4) {
            stabilizer.stabilize(SceneClassification(primaryScene = SceneType.PORTRAIT, confidence = 0.9f))
        }

        // 1 FOOD frame (remains PORTRAIT)
        val r1 = stabilizer.stabilize(SceneClassification(primaryScene = SceneType.FOOD, confidence = 0.85f))
        assertEquals(SceneType.PORTRAIT, r1.primaryScene)

        // 2nd FOOD frame (remains PORTRAIT)
        val r2 = stabilizer.stabilize(SceneClassification(primaryScene = SceneType.FOOD, confidence = 0.85f))
        assertEquals(SceneType.PORTRAIT, r2.primaryScene)

        // 3rd FOOD frame (remains PORTRAIT)
        val r3 = stabilizer.stabilize(SceneClassification(primaryScene = SceneType.FOOD, confidence = 0.85f))
        assertEquals(SceneType.PORTRAIT, r3.primaryScene)

        // 4th FOOD frame -> Reaches threshold (4/7) -> transitions to FOOD!
        val r4 = stabilizer.stabilize(SceneClassification(primaryScene = SceneType.FOOD, confidence = 0.85f))
        assertEquals(SceneType.FOOD, r4.primaryScene)
    }
}
