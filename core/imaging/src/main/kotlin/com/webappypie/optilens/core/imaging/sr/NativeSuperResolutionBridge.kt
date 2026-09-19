package com.webappypie.optilens.core.imaging.sr

/**
 * JNI Bridge to `liboptilens_imaging.so` Super Resolution algorithms.
 */
object NativeSuperResolutionBridge {

    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("optilens_imaging")
            isNativeLoaded = true
        } catch (t: Throwable) {
            isNativeLoaded = false
        }
    }

    val isAvailable: Boolean get() = isNativeLoaded

    @JvmStatic
    external fun nativeProcessMultiFrameSr(
        refYPlane: ByteArray,
        refUPlane: ByteArray?,
        refVPlane: ByteArray?,
        candYPlanes: Array<ByteArray>?,
        candUPlanes: Array<ByteArray>?,
        candVPlanes: Array<ByteArray>?,
        ghostMasks: Array<ByteArray>?,
        subPixelShifts: FloatArray?,
        inWidth: Int,
        inHeight: Int,
        inYStride: Int,
        inUvStride: Int,
        scaleFactor: Float,
        confidenceThreshold: Float,
        coringThreshold: Float,
        enableHaloSuppression: Boolean,
        residualRejectionThreshold: Float,
        sharpnessBoost: Float,
        outYPlane: ByteArray,
        outUPlane: ByteArray?,
        outVPlane: ByteArray?,
        outWidth: Int,
        outHeight: Int,
    ): Boolean

    @JvmStatic
    external fun nativeProcessSingleFrameSr(
        inYPlane: ByteArray,
        inUPlane: ByteArray?,
        inVPlane: ByteArray?,
        inWidth: Int,
        inHeight: Int,
        inYStride: Int,
        inUvStride: Int,
        scaleFactor: Float,
        confidenceThreshold: Float,
        coringThreshold: Float,
        enableHaloSuppression: Boolean,
        sharpnessBoost: Float,
        outYPlane: ByteArray,
        outUPlane: ByteArray?,
        outVPlane: ByteArray?,
        outWidth: Int,
        outHeight: Int,
    ): Boolean

    @JvmStatic
    external fun nativeRunBenchmark(
        testYPlane: ByteArray,
        width: Int,
        height: Int,
        stride: Int,
        scaleFactor: Float,
        outFlatMetrics: FloatArray,
    ): Boolean
}
