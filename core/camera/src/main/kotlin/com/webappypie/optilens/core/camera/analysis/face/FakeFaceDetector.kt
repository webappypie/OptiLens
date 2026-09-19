package com.webappypie.optilens.core.camera.analysis.face

import androidx.camera.core.ImageProxy
import com.webappypie.optilens.core.camera.model.DetectedFace

/**
 * Fake implementation of [FaceDetector] for deterministic unit testing.
 */
class FakeFaceDetector(
    var facesToReturn: List<DetectedFace> = emptyList()
) : FaceDetector {

    override suspend fun detectFaces(imageProxy: ImageProxy): List<DetectedFace> {
        return facesToReturn
    }

    override fun release() = Unit
}
