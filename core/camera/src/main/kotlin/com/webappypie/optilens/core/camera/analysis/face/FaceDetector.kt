package com.webappypie.optilens.core.camera.analysis.face

import androidx.camera.core.ImageProxy
import com.webappypie.optilens.core.camera.model.DetectedFace

/**
 * Interface for on-device real-time face and landmark detection.
 */
interface FaceDetector {

    /**
     * Identifies faces and anatomical landmarks within the incoming [imageProxy].
     * Coordinates in the returned [DetectedFace] entities are normalized to [0.0, 1.0].
     */
    suspend fun detectFaces(imageProxy: ImageProxy): List<DetectedFace>

    /**
     * Releases detector resources and models.
     */
    fun release()
}
