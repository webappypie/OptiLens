package com.webappypie.optilens.core.imaging.portrait

import com.webappypie.optilens.core.camera.model.FaceLandmarkPoint
import com.webappypie.optilens.core.camera.model.LandmarkType
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * High-performance portrait processing engine.
 *
 * Dispatches to native C++ `PortraitProcessor` via [NativePortraitBridge] with
 * deterministic JVM mathematical fallback for headless unit testing.
 */
@Singleton
class NativePortraitEngine @Inject constructor() {

    /**
     * Executes portrait processing on planar YUV420 buffers.
     *
     * @return True if processing succeeded.
     */
    fun processPortrait(
        yPlane: ByteArray,
        uPlane: ByteArray,
        vPlane: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        config: PortraitConfig,
    ): Boolean {
        if (width <= 0 || height <= 0 || yPlane.isEmpty() || uPlane.isEmpty() || vPlane.isEmpty()) {
            return false
        }

        val faceBoxes = serializeFaceBoxes(config)
        val landmarks = serializeLandmarks(config)

        if (NativePortraitBridge.isNativeLoaded) {
            return try {
                NativePortraitBridge.nativeProcessPortrait(
                    yPlane = yPlane,
                    uPlane = uPlane,
                    vPlane = vPlane,
                    width = width,
                    height = height,
                    yStride = yStride,
                    uvStride = uvStride,
                    faceBoxes = faceBoxes,
                    landmarks = landmarks,
                    apertureFNumber = config.aperture.fNumber,
                    skinSmoothingStrength = config.skinSmoothingStrength,
                    faceEvCompensation = config.faceEvCompensation,
                    isBacklit = config.isBacklit,
                    enableDetailProtection = config.enableDetailProtection,
                    enableEyeSparkle = config.enableEyeSparkle,
                )
            } catch (_: UnsatisfiedLinkError) {
                processFallback(yPlane, uPlane, vPlane, width, height, yStride, uvStride, config)
            }
        }

        return processFallback(yPlane, uPlane, vPlane, width, height, yStride, uvStride, config)
    }

    private fun serializeFaceBoxes(config: PortraitConfig): FloatArray {
        val faces = config.faces
        val out = FloatArray(faces.size * 5)
        var idx = 0
        for (f in faces) {
            out[idx++] = f.bounds.left
            out[idx++] = f.bounds.top
            out[idx++] = f.bounds.right
            out[idx++] = f.bounds.bottom
            out[idx++] = f.meanLuminance ?: 100.0f
        }
        return out
    }

    private fun serializeLandmarks(config: PortraitConfig): FloatArray {
        val result = mutableListOf<Float>()
        for (f in config.faces) {
            for (lm in f.landmarks) {
                val (typeInt, radius) = when (lm.type) {
                    LandmarkType.LEFT_EYE -> 0 to 0.035f
                    LandmarkType.RIGHT_EYE -> 1 to 0.035f
                    LandmarkType.LEFT_EYEBROW -> 2 to 0.025f
                    LandmarkType.RIGHT_EYEBROW -> 3 to 0.025f
                    LandmarkType.NOSE_BASE, LandmarkType.NOSE_BRIDGE -> 4 to 0.025f
                    LandmarkType.MOUTH_BOTTOM, LandmarkType.MOUTH_LEFT, LandmarkType.MOUTH_RIGHT, LandmarkType.LIPS_CONTOUR -> 5 to 0.030f
                    else -> continue
                }
                result.add(typeInt.toFloat())
                result.add(lm.x)
                result.add(lm.y)
                result.add(radius)
            }
            for (contour in f.contours) {
                val (typeInt, radius) = when (contour.type) {
                    LandmarkType.LEFT_EYE -> 0 to 0.020f
                    LandmarkType.RIGHT_EYE -> 1 to 0.020f
                    LandmarkType.LEFT_EYEBROW -> 2 to 0.015f
                    LandmarkType.RIGHT_EYEBROW -> 3 to 0.015f
                    LandmarkType.NOSE_BRIDGE, LandmarkType.NOSE_BASE -> 4 to 0.015f
                    LandmarkType.LIPS_CONTOUR -> 5 to 0.020f
                    else -> continue
                }
                for (pt in contour.points) {
                    result.add(typeInt.toFloat())
                    result.add(pt.x)
                    result.add(pt.y)
                    result.add(radius)
                }
            }
        }
        return result.toFloatArray()
    }

    /**
     * Estimates skin tone probability in [0.0, 1.0] across Fitzpatrick Phototypes I to VI.
     */
    fun computeSkinProbability(u: Float, v: Float): Float {
        val u0 = 112.0f
        val v0 = 152.0f
        val du = u - u0
        val dv = v - v0

        val cosT = 0.81915f
        val sinT = -0.57357f

        val xr = cosT * du - sinT * dv
        val yr = sinT * du + cosT * dv

        val sigmaX = 22.0f
        val sigmaY = 14.0f

        val d2 = (xr * xr) / (sigmaX * sigmaX) + (yr * yr) / (sigmaY * sigmaY)
        if (d2 > 8.0f) return 0.0f

        val p = exp(-0.5f * d2)
        return if (p > 0.05f) p else 0.0f
    }

    /**
     * Pure Kotlin fallback implementation matching the native algorithm logic.
     */
    private fun processFallback(
        yPlane: ByteArray,
        uPlane: ByteArray,
        vPlane: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        config: PortraitConfig,
    ): Boolean {
        val faces = config.faces

        // 1. Face Exposure Balancing
        if ((config.isBacklit || config.faceEvCompensation > 0.05f) && faces.isNotEmpty()) {
            val gain = 2.0f.pow(min(1.5f, max(0.0f, config.faceEvCompensation)))
            val gainExcess = gain - 1.0f

            for (face in faces) {
                val cx = (face.bounds.left + face.bounds.right) * 0.5f * width
                val cy = (face.bounds.top + face.bounds.bottom) * 0.5f * height
                val rx = max(2.0f, (face.bounds.right - face.bounds.left) * 0.70f * width)
                val ry = max(2.0f, (face.bounds.bottom - face.bounds.top) * 0.75f * height)

                val minX = max(0, (cx - rx * 2.0f).toInt())
                val maxX = min(width - 1, (cx + rx * 2.0f).toInt())
                val minY = max(0, (cy - ry * 2.0f).toInt())
                val maxY = min(height - 1, (cy + ry * 2.0f).toInt())

                for (y in minY..maxY) {
                    val dy = (y - cy) / ry
                    val dy2 = dy * dy
                    if (dy2 >= 4.0f) continue
                    val row = y * yStride
                    for (x in minX..maxX) {
                        val dx = (x - cx) / rx
                        val r2 = dx * dx + dy2
                        if (r2 >= 4.0f) continue

                        val weight = exp(-0.5f * r2)
                        val curY = yPlane[row + x].toInt() and 0xFF
                        val headroom = (255.0f - curY) / 255.0f
                        val lifted = (curY + weight * gainExcess * curY * headroom).roundToInt().coerceIn(0, 255)
                        yPlane[row + x] = lifted.toByte()
                    }
                }
            }
        }

        // 2. Skin Smoothing & Detail Protection
        if (config.skinSmoothingStrength > 0.01f && faces.isNotEmpty()) {
            val protectionMap = FloatArray(width * height)
            val eyeSparkleMap = BooleanArray(width * height)

            for (face in faces) {
                for (lm in face.landmarks) {
                    val isEye = lm.type == LandmarkType.LEFT_EYE || lm.type == LandmarkType.RIGHT_EYE
                    val lmx = lm.x * width
                    val lmy = lm.y * height
                    val radiusPx = max(3.0f, (if (isEye) 0.035f else 0.025f) * width)
                    val r2Max = radiusPx * radiusPx

                    val minX = max(0, (lmx - radiusPx).toInt())
                    val maxX = min(width - 1, (lmx + radiusPx).toInt())
                    val minY = max(0, (lmy - radiusPx).toInt())
                    val maxY = min(height - 1, (lmy + radiusPx).toInt())

                    for (y in minY..maxY) {
                        val dy = y - lmy
                        val offset = y * width
                        for (x in minX..maxX) {
                            val dx = x - lmx
                            val d2 = dx * dx + dy * dy
                            if (d2 < r2Max) {
                                val factor = 1.0f - (d2 / r2Max)
                                if (factor > protectionMap[offset + x]) {
                                    protectionMap[offset + x] = factor
                                }
                                if (isEye && config.enableEyeSparkle && d2 < (r2Max * 0.36f)) {
                                    eyeSparkleMap[offset + x] = true
                                }
                            }
                        }
                    }
                }
            }

            // Bilateral filter on face areas
            for (face in faces) {
                val minX = max(2, (face.bounds.left * width).toInt())
                val maxX = min(width - 3, (face.bounds.right * width).toInt())
                val minY = max(2, (face.bounds.top * height).toInt())
                val maxY = min(height - 3, (face.bounds.bottom * height).toInt())

                for (y in minY..maxY) {
                    val uvY = y / 2
                    val mapRow = y * width
                    val yRow = y * yStride
                    val uvRow = uvY * uvStride

                    for (x in minX..maxX) {
                        if (eyeSparkleMap[mapRow + x]) {
                            val center = yPlane[yRow + x].toInt() and 0xFF
                            val avgSurround = (
                                (yPlane[(y - 1) * yStride + x].toInt() and 0xFF) +
                                (yPlane[(y + 1) * yStride + x].toInt() and 0xFF) +
                                (yPlane[yRow + x - 1].toInt() and 0xFF) +
                                (yPlane[yRow + x + 1].toInt() and 0xFF)
                            ) * 0.25f
                            val sparkle = (center + 0.15f * (center - avgSurround)).roundToInt().coerceIn(0, 255)
                            yPlane[yRow + x] = sparkle.toByte()
                            continue
                        }

                        val protection = protectionMap[mapRow + x]
                        if (protection >= 0.85f) continue

                        val uvX = x / 2
                        val uVal = (uPlane[uvRow + uvX].toInt() and 0xFF).toFloat()
                        val vVal = (vPlane[uvRow + uvX].toInt() and 0xFF).toFloat()
                        val pSkin = computeSkinProbability(uVal, vVal)
                        if (pSkin < 0.20f) continue

                        val effectiveSmoothing = min(1.0f, config.skinSmoothingStrength * 1.5f) * pSkin * (1.0f - protection)
                        if (effectiveSmoothing <= 0.05f) continue

                        val centerVal = (yPlane[yRow + x].toInt() and 0xFF).toFloat()
                        var sumW = 0.0f
                        var sumV = 0.0f

                        for (dy in -2..2) {
                            val nRow = (y + dy) * yStride
                            for (dx in -2..2) {
                                val neighborVal = (yPlane[nRow + x + dx].toInt() and 0xFF).toFloat()
                                val sDist2 = (dx * dx + dy * dy).toFloat()
                                val rDist2 = (centerVal - neighborVal).let { it * it }
                                val w = exp(-sDist2 / (2f * 3.0f * 3.0f) - rDist2 / (2f * 40f * 40f))
                                sumW += w
                                sumV += w * neighborVal
                            }
                        }

                        if (sumW > 0.001f) {
                            val smoothed = sumV / sumW
                            val finalVal = ((1.0f - effectiveSmoothing) * centerVal + effectiveSmoothing * smoothed).roundToInt().coerceIn(0, 255)
                            yPlane[yRow + x] = finalVal.toByte()
                        }
                    }
                }
            }
        }

        // 3. Optical disc bokeh blur
        if (config.aperture.fNumber < 8.0f) {
            val maxBlurRadius = min(15.0f, max(1.0f, 12.0f * (1.4f / config.aperture.fNumber)))
            val depthMap = FloatArray(width * height) { 1.0f }

            for (y in 0 until height) {
                val yn = y.toFloat() / height
                val row = y * width
                for (x in 0 until width) {
                    val xn = x.toFloat() / width
                    var minZ = 1.0f
                    for (face in faces) {
                        val fw = face.bounds.right - face.bounds.left
                        val fh = face.bounds.bottom - face.bounds.top
                        val left = max(0.0f, face.bounds.left - fw * 0.15f)
                        val right = min(1.0f, face.bounds.right + fw * 0.15f)
                        val top = max(0.0f, face.bounds.top - fh * 0.12f)
                        val bottom = min(1.0f, face.bounds.bottom + fh * 0.50f)

                        var dx = 0.0f
                        if (xn < left) dx = left - xn
                        else if (xn > right) dx = xn - right

                        var dy = 0.0f
                        if (yn < top) dy = top - yn
                        else if (yn > bottom) dy = yn - bottom

                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist <= 0.0f) {
                            minZ = 0.0f
                            break
                        } else if (dist < 0.05f) {
                            val t = dist / 0.05f
                            val z = t * t * (3.0f - 2.0f * t)
                            if (z < minZ) minZ = z
                        }
                    }
                    depthMap[row + x] = minZ
                }
            }

            val yCopy = yPlane.copyOf()
            for (y in 0 until height) {
                val mapRow = y * width
                val outRow = y * yStride
                for (x in 0 until width) {
                    val z = depthMap[mapRow + x]
                    if (z <= 0.02f) continue

                    val radius = (maxBlurRadius * z).roundToInt()
                    if (radius <= 0) continue
                    val r2Max = (radius * radius).toFloat()

                    var sumW = 0.0f
                    var sumVal = 0.0f

                    for (dy in -radius..radius) {
                        val ny = (y + dy).coerceIn(0, height - 1)
                        val nRow = ny * yStride
                        for (dx in -radius..radius) {
                            val d2 = (dx * dx + dy * dy).toFloat()
                            if (d2 <= r2Max) {
                                val nx = (x + dx).coerceIn(0, width - 1)
                                val sampleY = (yCopy[nRow + nx].toInt() and 0xFF).toFloat()
                                var bloom = 1.0f
                                if (sampleY > 185.0f && z > 0.40f) {
                                    val excess = (sampleY - 185.0f) / 70.0f
                                    bloom = 1.0f + 2.5f * excess * excess
                                }
                                sumW += bloom
                                sumVal += bloom * sampleY
                            }
                        }
                    }

                    if (sumW > 0.001f) {
                        val blurred = sumVal / sumW
                        val orig = (yCopy[outRow + x].toInt() and 0xFF).toFloat()
                        val finalY = ((1.0f - z) * orig + z * blurred).roundToInt().coerceIn(0, 255)
                        yPlane[outRow + x] = finalY.toByte()
                    }
                }
            }
        }

        return true
    }
}
