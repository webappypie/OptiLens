package com.webappypie.optilens.core.imaging.alignment

/**
 * JNI bridge interfacing with `liboptilens_imaging.so` for hardware-accelerated
 * frame scoring, pyramidal Lucas-Kanade optical flow alignment, and ghost mask detection.
 */
object NativeAlignmentBridge {

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("optilens_imaging")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeScoreFrame(
        yPlane: ByteArray,
        refYPlane: ByteArray?,
        width: Int,
        height: Int,
        stride: Int,
        outScores: FloatArray,
    ): Boolean

    @JvmStatic
    external fun nativeAlignFrame(
        refYPlane: ByteArray,
        candYPlane: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
        outHomography: FloatArray,
        outMetrics: FloatArray,
    ): Boolean

    @JvmStatic
    external fun nativeComputeGhostMask(
        refYPlane: ByteArray,
        candYPlane: ByteArray,
        homography: FloatArray,
        width: Int,
        height: Int,
        stride: Int,
        outMask: ByteArray,
        threshold: Int,
    ): Float
}
