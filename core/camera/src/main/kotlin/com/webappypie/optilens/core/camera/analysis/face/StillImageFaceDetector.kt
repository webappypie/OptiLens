package com.webappypie.optilens.core.camera.analysis.face

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.webappypie.optilens.core.camera.model.NormalizedRect
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Face boxes on a decoded photo (bundled ML Kit model, on-device, offline). Used to protect skin
 * texture during enhancement. Never throws: any failure returns an empty list, so enhancement
 * simply runs without face protection.
 */
@Singleton
class StillImageFaceDetector @Inject constructor() {

    private val client by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build(),
        )
    }

    /** Normalised face boxes in [bitmap]'s own orientation. */
    suspend fun detect(bitmap: Bitmap): List<NormalizedRect> {
        if (bitmap.width < 32 || bitmap.height < 32 || bitmap.isRecycled) return emptyList()
        val scale = MAX_EDGE.toFloat() / max(bitmap.width, bitmap.height)
        val input = if (scale < 1f) {
            runCatching {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).roundToInt(), (bitmap.height * scale).roundToInt(), true)
            }.getOrNull() ?: return emptyList()
        } else {
            bitmap
        }
        // The scaled copy is left to the GC: ML Kit may still read it if this call is cancelled.
        val w = input.width.toFloat()
        val h = input.height.toFloat()
        return suspendCancellableCoroutine { cont ->
            try {
                client.process(InputImage.fromBitmap(input, 0))
                    .addOnSuccessListener { faces ->
                        cont.resume(
                            faces.map {
                                val b = it.boundingBox
                                NormalizedRect(
                                    left = (b.left / w).coerceIn(0f, 1f),
                                    top = (b.top / h).coerceIn(0f, 1f),
                                    right = (b.right / w).coerceIn(0f, 1f),
                                    bottom = (b.bottom / h).coerceIn(0f, 1f),
                                )
                            },
                        )
                    }
                    .addOnFailureListener { cont.resume(emptyList()) }
            } catch (_: Exception) {
                cont.resume(emptyList())
            }
        }
    }

    private companion object {
        const val MAX_EDGE = 960
        const val MIN_FACE_SIZE = 0.05f
    }
}
