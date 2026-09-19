package com.webappypie.optilens.core.imaging.fusion

/**
 * JNI bridge interfacing with `liboptilens_imaging.so` for multi-frame temporal fusion,
 * dynamic range reconstruction, tone mapping, and color grading.
 */
object NativeFusionBridge {

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("optilens_imaging")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeFuseStack(
        refYPlane: ByteArray,
        refUPlane: ByteArray?,
        refVPlane: ByteArray?,
        candYPlanes: Array<ByteArray>?,
        candUPlanes: Array<ByteArray>?,
        candVPlanes: Array<ByteArray>?,
        ghostMasks: Array<ByteArray>?,
        homographies: FloatArray?,
        exposureFactors: FloatArray?,
        width: Int,
        height: Int,
        stride: Int,
        uvPixelStride: Int,
        uvRowStride: Int,
        enableHdr: Boolean,
        enableDenoise: Boolean,
        outFusedY: FloatArray,
        outFusedU: FloatArray,
        outFusedV: FloatArray,
        outMetrics: FloatArray,
    ): Boolean

    @JvmStatic
    external fun nativeToneMapAndColor(
        inY: FloatArray,
        inU: FloatArray,
        inV: FloatArray,
        width: Int,
        height: Int,
        enableHighlightRollOff: Boolean,
        enableShadowRecovery: Boolean,
        shadowLiftAmount: Float,
        highlightKnee: Float,
        exposureCompensation: Float,
        profile: Int,
        enableAwb: Boolean,
        awbGain: Float,
        protectSkinTones: Boolean,
        sharpnessBoost: Float,
        outY: ByteArray,
        outU: ByteArray,
        outV: ByteArray,
    ): Boolean
}
