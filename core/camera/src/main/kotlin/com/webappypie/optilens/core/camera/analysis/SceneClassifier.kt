package com.webappypie.optilens.core.camera.analysis

import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.SceneType
import kotlin.math.abs

/**
 * Real-time vision-based scene classifier evaluating physical frame statistics,
 * chromaticity distributions, face presence, edge profiles, and lighting conditions.
 */
class SceneClassifier {

    /**
     * Classifies the scene into one of the 9 baseline scene types.
     */
    fun classify(
        frameData: PreprocessedFrameData,
        metrics: QualityMetrics,
        motionState: MotionState,
        faces: List<DetectedFace>,
    ): SceneClassification {
        val candidates = mutableListOf<SceneCandidate>()

        // 1. Portrait / People: Face detection is authoritative
        if (faces.isNotEmpty()) {
            val totalFaceArea = faces.sumOf { it.bounds.areaFraction.toDouble() }.toFloat()
            val portraitConfidence = (0.75f + (totalFaceArea * 0.5f)).coerceIn(0.75f, 0.98f)
            candidates.add(SceneCandidate(SceneType.PORTRAIT, portraitConfidence))
        }

        // 2. Low Light / Night
        if (metrics.luminance < 40.0f || (metrics.luminance < 50.0f && metrics.shadowClippingPercent > 18.0f)) {
            val nightConfidence = ((50.0f - metrics.luminance) / 50.0f).coerceIn(0.6f, 0.99f)
            candidates.add(SceneCandidate(SceneType.LOW_LIGHT, nightConfidence))
        }

        // 2b. Moon / Bright Disc in Dark Sky
        val isMoonCandidate = (metrics.luminance < 50.0f || frameData.meanLuminance < 50.0f) &&
                frameData.peripheryLuminance < 35.0f &&
                frameData.centerLuminance > 120.0f &&
                faces.isEmpty()
        if (isMoonCandidate) {
            candidates.add(SceneCandidate(SceneType.MOON, 0.89f))
        }

        // 3. Document: High text/edge contrast on neutral background
        val isChrominanceNeutral = abs(frameData.averageU - 128f) < 8f && abs(frameData.averageV - 128f) < 8f
        val hasPaperLuminance = frameData.centerLuminance > 120f
        val hasTextEdges = metrics.sharpnessScore > 40f
        val bimodalDocumentHistogram = frameData.histogramBins[0] > 0.05f && frameData.histogramBins[60] > 0.05f

        if (isChrominanceNeutral && hasPaperLuminance && hasTextEdges && bimodalDocumentHistogram && faces.isEmpty()) {
            val docConfidence = 0.88f
            candidates.add(SceneCandidate(SceneType.DOCUMENT, docConfidence))
        }

        // 4. Food: Rich warm tones (red/yellow chrominance), central focus, indoor/dining luminance
        if (frameData.warmScore > 0.28f && metrics.luminance in 60.0f..185.0f && faces.isEmpty()) {
            val foodConfidence = (0.60f + (frameData.warmScore * 0.35f)).coerceIn(0.60f, 0.90f)
            candidates.add(SceneCandidate(SceneType.FOOD, foodConfidence))
        }

        // 5. Sky / Plant / Nature: Chromaticity signatures
        val hasSky = frameData.skyScore > 0.30f
        val hasPlant = frameData.plantScore > 0.25f

        if (hasSky && hasPlant) {
            val natureConfidence = ((frameData.skyScore + frameData.plantScore) / 2f).coerceIn(0.65f, 0.92f)
            candidates.add(SceneCandidate(SceneType.NATURE, natureConfidence))
        } else if (hasSky) {
            candidates.add(SceneCandidate(SceneType.SKY, (0.60f + frameData.skyScore * 0.35f).coerceIn(0.60f, 0.95f)))
        } else if (hasPlant) {
            candidates.add(SceneCandidate(SceneType.PLANT, (0.60f + frameData.plantScore * 0.35f).coerceIn(0.60f, 0.95f)))
        }

        // 6. Wildlife vs Pet (Active animal motion in nature or domestic context takes precedence over static background)
        if (hasPlant && motionState.subjectMotionScore > 0.25f && !motionState.isCameraShaking && faces.isEmpty()) {
            val wildlifeConfidence = (0.78f + (motionState.subjectMotionScore * 0.20f)).coerceIn(0.78f, 0.98f)
            candidates.add(SceneCandidate(SceneType.WILDLIFE, wildlifeConfidence))
        } else if (faces.isEmpty() && frameData.warmScore > 0.20f && metrics.sharpnessScore > 35f && motionState.subjectMotionScore > 0.15f) {
            val petConfidence = (0.74f + (motionState.subjectMotionScore * 0.20f)).coerceIn(0.74f, 0.95f)
            candidates.add(SceneCandidate(SceneType.PET, petConfidence))
        }

        // 7. Indoor vs Outdoor fallback
        val isOutdoor = metrics.luminance > 140.0f || frameData.skyScore > 0.15f || metrics.dynamicRangeScore > 65.0f
        if (isOutdoor) {
            candidates.add(SceneCandidate(SceneType.OUTDOOR, 0.55f))
        } else {
            candidates.add(SceneCandidate(SceneType.INDOOR, 0.50f))
        }

        // Always have GENERAL as baseline fallback
        candidates.add(SceneCandidate(SceneType.GENERAL, 0.40f))

        // Sort descending by confidence
        candidates.sortByDescending { it.confidence }

        val primary = candidates.first()
        val secondary = candidates.getOrNull(1)?.takeIf { it.confidence > 0.50f }

        return SceneClassification(
            primaryScene = primary.sceneType,
            secondaryScene = secondary?.sceneType,
            confidence = primary.confidence,
            stabilityScore = 1.0f,
            timestampMs = frameData.timestampMs,
        )
    }

    private data class SceneCandidate(
        val sceneType: SceneType,
        val confidence: Float,
    )
}
