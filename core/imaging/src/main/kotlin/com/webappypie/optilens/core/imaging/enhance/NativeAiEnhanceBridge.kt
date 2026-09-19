package com.webappypie.optilens.core.imaging.enhance

/**
 * JNI declarations for the native C++ AI enhancement pipeline.
 */
object NativeAiEnhanceBridge {

    init {
        try {
            System.loadLibrary("optilens_imaging")
        } catch (_: UnsatisfiedLinkError) {
            // Handled gracefully via pure Kotlin fallback
        }
    }

    @JvmStatic
    external fun nativeProcessAiEnhance(
        yPlane: ByteArray,
        uPlane: ByteArray?,
        vPlane: ByteArray?,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        strength: Float,
        preserveSkinTones: Boolean,
        enableExposureBalancing: Boolean,
        enableNoiseReduction: Boolean,
        enableDetailEnhancement: Boolean,
        enableColorHarmony: Boolean,
    ): Boolean
}
