package com.webappypie.optilens.core.imaging.alignment

import kotlin.math.abs

/**
 * Encapsulates a 3x3 planar projective/affine homography transformation matrix
 * represented in row-major order:
 *
 * [ values[0], values[1], values[2] ]
 * [ values[3], values[4], values[5] ]
 * [ values[6], values[7], values[8] ]
 */
class HomographyMatrix(
    val values: FloatArray = floatArrayOf(
        1f, 0f, 0f,
        0f, 1f, 0f,
        0f, 0f, 1f
    )
) {
    init {
        require(values.size == 9) { "HomographyMatrix requires exactly 9 elements" }
    }

    val isIdentity: Boolean
        get() = abs(values[0] - 1f) < 1e-4f && abs(values[1]) < 1e-4f && abs(values[2]) < 1e-4f &&
                abs(values[3]) < 1e-4f && abs(values[4] - 1f) < 1e-4f && abs(values[5]) < 1e-4f &&
                abs(values[6]) < 1e-4f && abs(values[7]) < 1e-4f && abs(values[8] - 1f) < 1e-4f

    val translationX: Float
        get() = values[2]

    val translationY: Float
        get() = values[5]

    /**
     * Transforms point (x, y) through this homography.
     */
    fun transformPoint(x: Float, y: Float): Pair<Float, Float> {
        val w = values[6] * x + values[7] * y + values[8]
        val invW = if (abs(w) > 1e-6f) 1.0f / w else 1.0f
        val tx = (values[0] * x + values[1] * y + values[2]) * invW
        val ty = (values[3] * x + values[4] * y + values[5]) * invW
        return tx to ty
    }

    /**
     * Inverts the 3x3 homography matrix using adjugate over determinant.
     */
    fun invert(): HomographyMatrix? {
        val a = values[0]; val b = values[1]; val c = values[2]
        val d = values[3]; val e = values[4]; val f = values[5]
        val g = values[6]; val h = values[7]; val i = values[8]

        val det = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
        if (abs(det) < 1e-6f) return null

        val invDet = 1.0f / det
        val out = FloatArray(9)

        out[0] = (e * i - f * h) * invDet
        out[1] = (c * h - b * i) * invDet
        out[2] = (b * f - c * e) * invDet

        out[3] = (f * g - d * i) * invDet
        out[4] = (a * i - c * g) * invDet
        out[5] = (c * d - a * f) * invDet

        out[6] = (d * h - e * g) * invDet
        out[7] = (b * g - a * h) * invDet
        out[8] = (a * e - b * d) * invDet

        return HomographyMatrix(out)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HomographyMatrix) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String {
        return "HomographyMatrix([%.3f, %.3f, %.3f], [%.3f, %.3f, %.3f], [%.3f, %.3f, %.3f])"
            .format(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8])
    }

    companion object {
        val IDENTITY = HomographyMatrix()

        fun translation(dx: Float, dy: Float): HomographyMatrix {
            return HomographyMatrix(
                floatArrayOf(
                    1f, 0f, dx,
                    0f, 1f, dy,
                    0f, 0f, 1f
                )
            )
        }
    }
}
