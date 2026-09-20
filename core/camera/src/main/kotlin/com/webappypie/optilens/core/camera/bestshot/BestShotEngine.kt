package com.webappypie.optilens.core.camera.bestshot

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.model.DetectedFace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-performance orchestrator for Best Shot ranking and selection.
 *
 * Evaluates burst acquisitions, sorts candidates descending by composite quality score,
 * flags the system recommended best shot, preserves alternates, and provides manual override.
 *
 * Guarantees that only physical frames are delivered without expression synthesis.
 */
@Singleton
class BestShotEngine @Inject constructor(
    private val scorer: BestShotScorer = BestShotScorer(),
) {

    /**
     * Evaluates a multi-frame [BurstResult] with optional scene detected faces.
     */
    fun evaluateBurst(
        burst: BurstResult,
        detectedFaces: List<DetectedFace> = emptyList(),
    ): BestShotResult {
        val packets = burst.packets
        if (packets.isEmpty()) {
            return BestShotResult(
                candidates = emptyList(),
                bestIndex = 0,
                selectedIndex = 0,
                hasFaces = false,
            )
        }

        return evaluatePackets(packets, detectedFaces)
    }

    /**
     * Evaluates a collection of [FramePacket] entities.
     */
    fun evaluatePackets(
        packets: List<FramePacket>,
        detectedFaces: List<DetectedFace> = emptyList(),
        frameUris: List<String> = emptyList(),
    ): BestShotResult {
        if (packets.isEmpty()) {
            return BestShotResult(
                candidates = emptyList(),
                bestIndex = 0,
                selectedIndex = 0,
                hasFaces = false,
            )
        }

        val candidateScores = mutableListOf<Pair<Int, BestShotCandidateScores>>()

        for (i in packets.indices) {
            val packet = packets[i]
            val prevPacket = if (i > 0) packets[i - 1] else null

            val yData = packet.buffer.data
            val prevYData = prevPacket?.buffer?.data
            val gyroSpeed = packet.gyroWindow.meanAngularSpeed

            val scores = scorer.scoreFrame(
                yPlane = yData,
                width = packet.width,
                height = packet.height,
                stride = packet.width,
                gyroAngularSpeed = gyroSpeed,
                adjacentYPlane = prevYData,
                detectedFaces = detectedFaces,
            )
            candidateScores.add(i to scores)
        }

        // Sort descending by composite score to determine rank
        val sortedByScore = candidateScores.sortedByDescending { it.second.compositeScore }
        val bestIdx = sortedByScore.first().first

        // Map into BestShotCandidate records with rankings
        val rankedMap = mutableMapOf<Int, Int>()
        sortedByScore.forEachIndexed { rankIndex, pair ->
            rankedMap[pair.first] = rankIndex + 1
        }

        val candidates = candidateScores.map { (index, scores) ->
            val rank = rankedMap[index] ?: (index + 1)
            val uri = frameUris.getOrNull(index)
            BestShotCandidate(
                index = index,
                uri = uri,
                sharpnessScore = scores.sharpnessScore,
                motionScore = scores.motionScore,
                exposureScore = scores.exposureScore,
                eyeOpenScore = scores.eyeOpenScore,
                compositeScore = scores.compositeScore,
                ranking = rank,
                isBest = (index == bestIdx),
                isAlternate = (index != bestIdx),
                timestampMs = packets[index].metadata.timestampNs / 1_000_000L,
            )
        }

        val hasFaces = detectedFaces.any { it.confidence >= 0.70f }

        return BestShotResult(
            candidates = candidates,
            bestIndex = bestIdx,
            selectedIndex = bestIdx,
            hasFaces = hasFaces,
            timestampMs = System.currentTimeMillis(),
        )
    }

    /**
     * Evaluates a synthetic or pre-loaded batch of frame buffers.
     */
    fun evaluateRawBuffers(
        buffers: List<ByteArray>,
        width: Int,
        height: Int,
        gyroSpeeds: List<Float> = emptyList(),
        detectedFaces: List<DetectedFace> = emptyList(),
        uris: List<String> = emptyList(),
    ): BestShotResult {
        if (buffers.isEmpty()) {
            return BestShotResult(emptyList(), 0, 0, false)
        }

        val candidateScores = mutableListOf<Pair<Int, BestShotCandidateScores>>()

        for (i in buffers.indices) {
            val yData = buffers[i]
            val prevYData = if (i > 0) buffers[i - 1] else null
            val gyro = gyroSpeeds.getOrNull(i) ?: 0.0f

            val scores = scorer.scoreFrame(
                yPlane = yData,
                width = width,
                height = height,
                stride = width,
                gyroAngularSpeed = gyro,
                adjacentYPlane = prevYData,
                detectedFaces = detectedFaces,
            )
            candidateScores.add(i to scores)
        }

        val sorted = candidateScores.sortedByDescending { it.second.compositeScore }
        val bestIdx = sorted.first().first

        val rankedMap = mutableMapOf<Int, Int>()
        sorted.forEachIndexed { rankIndex, pair ->
            rankedMap[pair.first] = rankIndex + 1
        }

        val candidates = candidateScores.map { (index, scores) ->
            val rank = rankedMap[index] ?: (index + 1)
            BestShotCandidate(
                index = index,
                uri = uris.getOrNull(index),
                sharpnessScore = scores.sharpnessScore,
                motionScore = scores.motionScore,
                exposureScore = scores.exposureScore,
                eyeOpenScore = scores.eyeOpenScore,
                compositeScore = scores.compositeScore,
                ranking = rank,
                isBest = (index == bestIdx),
                isAlternate = (index != bestIdx),
                timestampMs = System.currentTimeMillis(),
            )
        }

        return BestShotResult(
            candidates = candidates,
            bestIndex = bestIdx,
            selectedIndex = bestIdx,
            hasFaces = detectedFaces.any { it.confidence >= 0.70f },
            timestampMs = System.currentTimeMillis(),
        )
    }
}
