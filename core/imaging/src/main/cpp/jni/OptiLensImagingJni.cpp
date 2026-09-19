#include <jni.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include "../scoring/FrameScorer.hpp"
#include "../alignment/PyramidalOpticalFlow.hpp"
#include "../alignment/GhostMaskEstimator.hpp"

#define LOG_TAG "OptiLensImagingJni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_webappypie_optilens_core_imaging_alignment_NativeAlignmentBridge_nativeScoreFrame(
    JNIEnv* env,
    jclass /* clazz */,
    jbyteArray yPlane,
    jbyteArray refYPlane,
    jint width,
    jint height,
    jint stride,
    jfloatArray outScores
) {
    if (!yPlane || !outScores || width <= 0 || height <= 0 || stride <= 0) {
        return JNI_FALSE;
    }

    jboolean isCopyY = JNI_FALSE;
    jbyte* yData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(yPlane, &isCopyY));
    if (!yData) return JNI_FALSE;

    jbyte* refYData = nullptr;
    if (refYPlane != nullptr) {
        jboolean isCopyRef = JNI_FALSE;
        refYData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(refYPlane, &isCopyRef));
    }

    const uint8_t* uY = reinterpret_cast<const uint8_t*>(yData);
    const uint8_t* uRef = reinterpret_cast<const uint8_t*>(refYData);

    optilens::NativeFrameScore score = optilens::FrameScorer::scoreFrame(
        uY,
        uRef,
        width,
        height,
        stride
    );

    if (refYData != nullptr) {
        env->ReleasePrimitiveArrayCritical(refYPlane, refYData, JNI_ABORT);
    }
    env->ReleasePrimitiveArrayCritical(yPlane, yData, JNI_ABORT);

    float results[5] = {
        score.sharpnessScore,
        score.exposurePenalty,
        score.motionDifference,
        score.focusConfidence,
        score.compositeScore
    };

    env->SetFloatArrayRegion(outScores, 0, 5, results);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_webappypie_optilens_core_imaging_alignment_NativeAlignmentBridge_nativeAlignFrame(
    JNIEnv* env,
    jclass /* clazz */,
    jbyteArray refYPlane,
    jbyteArray candYPlane,
    jint width,
    jint height,
    jint stride,
    jfloatArray outHomography,
    jfloatArray outMetrics
) {
    if (!refYPlane || !candYPlane || !outHomography || !outMetrics || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    jboolean isCopyRef = JNI_FALSE;
    jbyte* refData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(refYPlane, &isCopyRef));
    if (!refData) return JNI_FALSE;

    jboolean isCopyCand = JNI_FALSE;
    jbyte* candData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(candYPlane, &isCopyCand));
    if (!candData) {
        env->ReleasePrimitiveArrayCritical(refYPlane, refData, JNI_ABORT);
        return JNI_FALSE;
    }

    const uint8_t* uRef = reinterpret_cast<const uint8_t*>(refData);
    const uint8_t* uCand = reinterpret_cast<const uint8_t*>(candData);

    optilens::AlignmentTransform transform = optilens::PyramidalOpticalFlow::align(
        uRef,
        uCand,
        width,
        height,
        stride
    );

    env->ReleasePrimitiveArrayCritical(candYPlane, candData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(refYPlane, refData, JNI_ABORT);

    env->SetFloatArrayRegion(outHomography, 0, 9, transform.h);

    float metrics[3] = {
        transform.confidence,
        transform.inlierRatio,
        transform.isRejected ? 1.0f : 0.0f
    };
    env->SetFloatArrayRegion(outMetrics, 0, 3, metrics);

    return JNI_TRUE;
}

JNIEXPORT jfloat JNICALL
Java_com_webappypie_optilens_core_imaging_alignment_NativeAlignmentBridge_nativeComputeGhostMask(
    JNIEnv* env,
    jclass /* clazz */,
    jbyteArray refYPlane,
    jbyteArray candYPlane,
    jfloatArray homography,
    jint width,
    jint height,
    jint stride,
    jbyteArray outMask,
    jint threshold
) {
    if (!refYPlane || !candYPlane || !homography || !outMask || width <= 0 || height <= 0) {
        return 0.0f;
    }

    float h[9];
    env->GetFloatArrayRegion(homography, 0, 9, h);

    jboolean isCopyRef = JNI_FALSE;
    jbyte* refData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(refYPlane, &isCopyRef));
    if (!refData) return 0.0f;

    jboolean isCopyCand = JNI_FALSE;
    jbyte* candData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(candYPlane, &isCopyCand));
    if (!candData) {
        env->ReleasePrimitiveArrayCritical(refYPlane, refData, JNI_ABORT);
        return 0.0f;
    }

    jboolean isCopyMask = JNI_FALSE;
    jbyte* maskData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(outMask, &isCopyMask));
    if (!maskData) {
        env->ReleasePrimitiveArrayCritical(candYPlane, candData, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(refYPlane, refData, JNI_ABORT);
        return 0.0f;
    }

    const uint8_t* uRef = reinterpret_cast<const uint8_t*>(refData);
    const uint8_t* uCand = reinterpret_cast<const uint8_t*>(candData);
    uint8_t* uMask = reinterpret_cast<uint8_t*>(maskData);

    optilens::GhostMaskResult result = optilens::GhostMaskEstimator::computeGhostMask(
        uRef,
        uCand,
        h,
        width,
        height,
        stride,
        uMask,
        static_cast<uint8_t>(threshold)
    );

    env->ReleasePrimitiveArrayCritical(outMask, maskData, 0); // commit back to Java array
    env->ReleasePrimitiveArrayCritical(candYPlane, candData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(refYPlane, refData, JNI_ABORT);

    return result.motionCoverageFraction;
}

} // extern "C"
