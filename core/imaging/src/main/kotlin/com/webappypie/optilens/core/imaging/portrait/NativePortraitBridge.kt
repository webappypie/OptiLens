package com.webappypie.optilens.core.imaging.portrait

/**
 * JNI bridge interfacing with `liboptilens_imaging.so` for portrait and face-aware processing.
 */
object NativePortraitBridge {

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("optilens_imaging")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic
    external fun nativeProcessPortrait(
        yPlane: ByteArray,
        uPlane: ByteArray,
        vPlane: ByteArray,
        width: Int,
        height: Int,
        yStride: Int,
        uvStride: Int,
        faceBoxes: FloatArray,
        landmarks: FloatArray,
        apertureFNumber: Float,
        skinSmoothingStrength: Float,
        faceEvCompensation: Float,
        isBacklit: Boolean,
        enableDetailProtection: Boolean,
        enableEyeSparkle: Boolean,
    ): Boolean
}
