package com.webappypie.optilens.core.camera.analysis.face

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.face.FaceContour as MlFaceContour
import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.FaceContour
import com.webappypie.optilens.core.camera.model.FaceLandmarkPoint
import com.webappypie.optilens.core.camera.model.LandmarkType
import com.webappypie.optilens.core.camera.model.NormalizedPoint
import com.webappypie.optilens.core.camera.model.NormalizedRect
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device implementation of [FaceDetector] powered by Google ML Kit Face Detection.
 *
 * Configured in fast performance mode with landmark detection and contour curves enabled.
 * Safely handles missing models or runtime exceptions without throwing.
 */
class MlKitFaceDetector : FaceDetector {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.12f)
        .build()

    private val client by lazy { FaceDetection.getClient(detectorOptions) }

    override suspend fun detectFaces(imageProxy: ImageProxy): List<DetectedFace> {
        val mediaImage = imageProxy.image ?: return emptyList()

        return suspendCancellableCoroutine { continuation ->
            try {
                val inputImage = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees,
                )

                client.process(inputImage)
                    .addOnSuccessListener { faces ->
                        val result = mapFaces(faces, inputImage.width, inputImage.height)
                        continuation.resume(result)
                    }
                    .addOnFailureListener {
                        // Fail safely and gracefully without crashing
                        continuation.resume(emptyList())
                    }
            } catch (_: Exception) {
                continuation.resume(emptyList())
            }
        }
    }

    override fun release() {
        try {
            client.close()
        } catch (_: Exception) {
            // Drop gracefully on cleanup
        }
    }

    private fun mapFaces(faces: List<Face>, imageWidth: Int, imageHeight: Int): List<DetectedFace> {
        if (imageWidth <= 0 || imageHeight <= 0) return emptyList()

        val widthF = imageWidth.toFloat()
        val heightF = imageHeight.toFloat()

        return faces.map { face ->
            val box = face.boundingBox
            val normalizedBounds = NormalizedRect(
                left = (box.left / widthF).coerceIn(0f, 1f),
                top = (box.top / heightF).coerceIn(0f, 1f),
                right = (box.right / widthF).coerceIn(0f, 1f),
                bottom = (box.bottom / heightF).coerceIn(0f, 1f),
            )

            val landmarkPoints = mutableListOf<FaceLandmarkPoint>()

            face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let {
                landmarkPoints.add(FaceLandmarkPoint(LandmarkType.LEFT_EYE, (it.x / widthF).coerceIn(0f, 1f), (it.y / heightF).coerceIn(0f, 1f)))
            }
            face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let {
                landmarkPoints.add(FaceLandmarkPoint(LandmarkType.RIGHT_EYE, (it.x / widthF).coerceIn(0f, 1f), (it.y / heightF).coerceIn(0f, 1f)))
            }
            face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let {
                landmarkPoints.add(FaceLandmarkPoint(LandmarkType.NOSE_BASE, (it.x / widthF).coerceIn(0f, 1f), (it.y / heightF).coerceIn(0f, 1f)))
            }
            face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let {
                landmarkPoints.add(FaceLandmarkPoint(LandmarkType.MOUTH_BOTTOM, (it.x / widthF).coerceIn(0f, 1f), (it.y / heightF).coerceIn(0f, 1f)))
            }
            face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let {
                landmarkPoints.add(FaceLandmarkPoint(LandmarkType.MOUTH_LEFT, (it.x / widthF).coerceIn(0f, 1f), (it.y / heightF).coerceIn(0f, 1f)))
            }
            face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let {
                landmarkPoints.add(FaceLandmarkPoint(LandmarkType.MOUTH_RIGHT, (it.x / widthF).coerceIn(0f, 1f), (it.y / heightF).coerceIn(0f, 1f)))
            }

            val contourList = mutableListOf<FaceContour>()
            fun extractContour(contourType: Int, targetType: LandmarkType) {
                face.getContour(contourType)?.points?.let { points ->
                    if (points.isNotEmpty()) {
                        contourList.add(
                            FaceContour(
                                type = targetType,
                                points = points.map { pt ->
                                    NormalizedPoint(
                                        x = (pt.x / widthF).coerceIn(0f, 1f),
                                        y = (pt.y / heightF).coerceIn(0f, 1f),
                                    )
                                }
                            )
                        )
                    }
                }
            }

            extractContour(MlFaceContour.FACE, LandmarkType.FACE_OVAL)
            extractContour(MlFaceContour.LEFT_EYE, LandmarkType.LEFT_EYE)
            extractContour(MlFaceContour.RIGHT_EYE, LandmarkType.RIGHT_EYE)
            extractContour(MlFaceContour.LEFT_EYEBROW_TOP, LandmarkType.LEFT_EYEBROW)
            extractContour(MlFaceContour.RIGHT_EYEBROW_TOP, LandmarkType.RIGHT_EYEBROW)
            extractContour(MlFaceContour.NOSE_BRIDGE, LandmarkType.NOSE_BRIDGE)
            extractContour(MlFaceContour.NOSE_BOTTOM, LandmarkType.NOSE_BASE)
            extractContour(MlFaceContour.UPPER_LIP_TOP, LandmarkType.LIPS_CONTOUR)
            extractContour(MlFaceContour.LOWER_LIP_BOTTOM, LandmarkType.LIPS_CONTOUR)

            DetectedFace(
                id = face.trackingId,
                bounds = normalizedBounds,
                landmarks = landmarkPoints,
                contours = contourList,
                confidence = 0.95f,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
            )
        }
    }
}
