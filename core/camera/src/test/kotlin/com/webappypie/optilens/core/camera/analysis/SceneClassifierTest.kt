package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.CameraShakeLevel
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.NormalizedRect
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneType
import com.webappypie.optilens.core.camera.model.SubjectMotionLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneClassifierTest {

    private val classifier = SceneClassifier()

    private fun createBaseFrame(
        meanLum: Float = 120f,
        centerLum: Float = 120f,
        peripheryLum: Float = 120f,
        u: Float = 128f,
        v: Float = 128f,
        skyScore: Float = 0f,
        plantScore: Float = 0f,
        warmScore: Float = 0f,
        bimodalDoc: Boolean = false,
    ): PreprocessedFrameData {
        val bins = FloatArray(64)
        if (bimodalDoc) {
            bins[0] = 0.15f
            bins[60] = 0.20f
        }
        return PreprocessedFrameData(
            gridWidth = 160,
            gridHeight = 120,
            yGrid = IntArray(160 * 120) { meanLum.toInt() },
            histogramBins = bins,
            meanLuminance = meanLum,
            centerLuminance = centerLum,
            peripheryLuminance = peripheryLum,
            highlightClippingPercent = 0f,
            shadowClippingPercent = 0f,
            averageU = u,
            averageV = v,
            skyScore = skyScore,
            plantScore = plantScore,
            warmScore = warmScore,
            timestampMs = 1000L,
        )
    }

    @Test
    fun classify_detectedFace_returnsPortrait() {
        val frame = createBaseFrame()
        val metrics = QualityMetrics(luminance = 120f)
        val motion = MotionState.DEFAULT
        val faces = listOf(
            DetectedFace(
                bounds = NormalizedRect(0.3f, 0.2f, 0.7f, 0.8f),
                confidence = 0.95f,
            )
        )

        val result = classifier.classify(frame, metrics, motion, faces)

        assertEquals(SceneType.PORTRAIT, result.primaryScene)
        assertTrue(result.confidence > 0.8f)
    }

    @Test
    fun classify_darkLuminanceAndHighShadowClipping_returnsLowLight() {
        val frame = createBaseFrame(meanLum = 25f)
        val metrics = QualityMetrics(
            luminance = 25f,
            shadowClippingPercent = 30f,
        )
        val motion = MotionState.DEFAULT

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.LOW_LIGHT, result.primaryScene)
    }

    @Test
    fun classify_bimodalDocumentHistogramAndTextSharpness_returnsDocument() {
        val frame = createBaseFrame(
            meanLum = 150f,
            centerLum = 180f,
            u = 128f,
            v = 128f,
            bimodalDoc = true,
        )
        val metrics = QualityMetrics(
            luminance = 150f,
            sharpnessScore = 65f, // High contrast text edges
        )
        val motion = MotionState.DEFAULT

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.DOCUMENT, result.primaryScene)
    }

    @Test
    fun classify_warmTonesInIndoorLighting_returnsFood() {
        val frame = createBaseFrame(
            meanLum = 110f,
            warmScore = 0.45f,
        )
        val metrics = QualityMetrics(luminance = 110f)
        val motion = MotionState.DEFAULT

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.FOOD, result.primaryScene)
    }

    @Test
    fun classify_dominantBlueUpperHalf_returnsSky() {
        val frame = createBaseFrame(
            meanLum = 160f,
            skyScore = 0.65f,
            plantScore = 0.05f,
        )
        val metrics = QualityMetrics(luminance = 160f)
        val motion = MotionState.DEFAULT

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.SKY, result.primaryScene)
    }

    @Test
    fun classify_dominantGreenChrominance_returnsPlant() {
        val frame = createBaseFrame(
            meanLum = 120f,
            skyScore = 0.05f,
            plantScore = 0.55f,
        )
        val metrics = QualityMetrics(luminance = 120f)
        val motion = MotionState.DEFAULT

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.PLANT, result.primaryScene)
    }

    @Test
    fun classify_combinedSkyAndPlant_returnsNature() {
        val frame = createBaseFrame(
            meanLum = 145f,
            skyScore = 0.45f,
            plantScore = 0.40f,
        )
        val metrics = QualityMetrics(luminance = 145f)
        val motion = MotionState.DEFAULT

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.NATURE, result.primaryScene)
    }

    @Test
    fun classify_natureContextWithSubjectMotionAndStableCamera_returnsWildlife() {
        val frame = createBaseFrame(
            meanLum = 130f,
            plantScore = 0.40f,
        )
        val metrics = QualityMetrics(luminance = 130f)
        val motion = MotionState(
            cameraShakeLevel = CameraShakeLevel.STABLE,
            subjectMotionScore = 0.45f,
            subjectMotionLevel = SubjectMotionLevel.HIGH_MOTION,
        )

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.WILDLIFE, result.primaryScene)
    }

    @Test
    fun classify_warmTextureWithMotionInIndoor_returnsPet() {
        val frame = createBaseFrame(
            meanLum = 95f,
            warmScore = 0.25f,
        )
        val metrics = QualityMetrics(
            luminance = 95f,
            sharpnessScore = 45f,
        )
        val motion = MotionState(
            cameraShakeLevel = CameraShakeLevel.STABLE,
            subjectMotionScore = 0.30f,
            subjectMotionLevel = SubjectMotionLevel.LOW_MOTION,
        )

        val result = classifier.classify(frame, metrics, motion, emptyList())

        assertEquals(SceneType.PET, result.primaryScene)
    }
}
