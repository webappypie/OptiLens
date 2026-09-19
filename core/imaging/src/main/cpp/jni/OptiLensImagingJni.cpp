#include <jni.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include "../scoring/FrameScorer.hpp"
#include "../alignment/PyramidalOpticalFlow.hpp"
#include "../alignment/GhostMaskEstimator.hpp"
#include "../fusion/TemporalFusionEngine.hpp"
#include "../fusion/ToneMapper.hpp"
#include "../fusion/ColorCorrector.hpp"
#include "../portrait/PortraitProcessor.hpp"

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

JNIEXPORT jboolean JNICALL
Java_com_webappypie_optilens_core_imaging_fusion_NativeFusionBridge_nativeFuseStack(
    JNIEnv* env,
    jclass /* clazz */,
    jbyteArray refYPlane,
    jbyteArray refUPlane,
    jbyteArray refVPlane,
    jobjectArray candYPlanes,
    jobjectArray candUPlanes,
    jobjectArray candVPlanes,
    jobjectArray ghostMasks,
    jfloatArray homographies,
    jfloatArray exposureFactors,
    jint width,
    jint height,
    jint stride,
    jint uvPixelStride,
    jint uvRowStride,
    jboolean enableHdr,
    jboolean enableDenoise,
    jfloatArray outFusedY,
    jfloatArray outFusedU,
    jfloatArray outFusedV,
    jfloatArray outMetrics
) {
    if (!refYPlane || !outFusedY || !outFusedU || !outFusedV || !outMetrics || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    const int totalPixels = width * height;

    // 1. Reference frame buffers
    jbyte* refYData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(refYPlane, nullptr));
    if (!refYData) return JNI_FALSE;

    jbyte* refUData = refUPlane ? static_cast<jbyte*>(env->GetPrimitiveArrayCritical(refUPlane, nullptr)) : nullptr;
    jbyte* refVData = refVPlane ? static_cast<jbyte*>(env->GetPrimitiveArrayCritical(refVPlane, nullptr)) : nullptr;

    const int numCands = candYPlanes ? env->GetArrayLength(candYPlanes) : 0;

    std::vector<jbyteArray> candYArrays(numCands);
    std::vector<jbyte*> candYData(numCands, nullptr);
    std::vector<jbyteArray> candUArrays(numCands);
    std::vector<jbyte*> candUData(numCands, nullptr);
    std::vector<jbyteArray> candVArrays(numCands);
    std::vector<jbyte*> candVData(numCands, nullptr);
    std::vector<jbyteArray> maskArrays(numCands);
    std::vector<jbyte*> maskData(numCands, nullptr);

    for (int i = 0; i < numCands; ++i) {
        candYArrays[i] = static_cast<jbyteArray>(env->GetObjectArrayElement(candYPlanes, i));
        if (candYArrays[i]) {
            candYData[i] = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(candYArrays[i], nullptr));
        }

        if (candUPlanes) {
            candUArrays[i] = static_cast<jbyteArray>(env->GetObjectArrayElement(candUPlanes, i));
            if (candUArrays[i]) {
                candUData[i] = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(candUArrays[i], nullptr));
            }
        }
        if (candVPlanes) {
            candVArrays[i] = static_cast<jbyteArray>(env->GetObjectArrayElement(candVPlanes, i));
            if (candVArrays[i]) {
                candVData[i] = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(candVArrays[i], nullptr));
            }
        }
        if (ghostMasks) {
            maskArrays[i] = static_cast<jbyteArray>(env->GetObjectArrayElement(ghostMasks, i));
            if (maskArrays[i]) {
                maskData[i] = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(maskArrays[i], nullptr));
            }
        }
    }

    std::vector<float> flatHomographies(numCands * 9, 0.0f);
    if (homographies && numCands > 0) {
        env->GetFloatArrayRegion(homographies, 0, numCands * 9, flatHomographies.data());
    }

    std::vector<float> flatExposure(1 + numCands, 1.0f);
    if (exposureFactors) {
        env->GetFloatArrayRegion(exposureFactors, 0, 1 + numCands, flatExposure.data());
    }

    // Assemble input frame descriptors
    std::vector<optilens::FusionFrameInput> frameInputs;
    frameInputs.reserve(1 + numCands);

    // Anchor reference frame
    frameInputs.push_back({
        reinterpret_cast<const uint8_t*>(refYData),
        reinterpret_cast<const uint8_t*>(refUData),
        reinterpret_cast<const uint8_t*>(refVData),
        uvPixelStride,
        uvRowStride,
        nullptr, // Reference frame has no ghost mask against itself
        nullptr, // Identity homography
        flatExposure[0],
        true
    });

    // Candidate frames
    for (int i = 0; i < numCands; ++i) {
        if (!candYData[i]) continue;
        frameInputs.push_back({
            reinterpret_cast<const uint8_t*>(candYData[i]),
            reinterpret_cast<const uint8_t*>(candUData[i]),
            reinterpret_cast<const uint8_t*>(candVData[i]),
            uvPixelStride,
            uvRowStride,
            reinterpret_cast<const uint8_t*>(maskData[i]),
            flatHomographies.data() + (i * 9),
            flatExposure[1 + i],
            false
        });
    }

    // Execute multi-frame temporal fusion
    optilens::FusionOutput output = optilens::TemporalFusionEngine::fuse(
        frameInputs,
        width,
        height,
        stride,
        enableHdr == JNI_TRUE,
        enableDenoise == JNI_TRUE
    );

    // Release candidate buffers
    for (int i = 0; i < numCands; ++i) {
        if (maskData[i]) env->ReleasePrimitiveArrayCritical(maskArrays[i], maskData[i], JNI_ABORT);
        if (candVData[i]) env->ReleasePrimitiveArrayCritical(candVArrays[i], candVData[i], JNI_ABORT);
        if (candUData[i]) env->ReleasePrimitiveArrayCritical(candUArrays[i], candUData[i], JNI_ABORT);
        if (candYData[i]) env->ReleasePrimitiveArrayCritical(candYArrays[i], candYData[i], JNI_ABORT);
    }

    // Release reference buffers
    if (refVData) env->ReleasePrimitiveArrayCritical(refVPlane, refVData, JNI_ABORT);
    if (refUData) env->ReleasePrimitiveArrayCritical(refUPlane, refUData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(refYPlane, refYData, JNI_ABORT);

    // Write back fused outputs
    env->SetFloatArrayRegion(outFusedY, 0, totalPixels, output.fusedY.data());
    env->SetFloatArrayRegion(outFusedU, 0, totalPixels, output.fusedU.data());
    env->SetFloatArrayRegion(outFusedV, 0, totalPixels, output.fusedV.data());

    float metrics[4] = {
        output.snrGainDb,
        output.dynamicRangeExtensionEv,
        output.ghostPixelFraction,
        static_cast<float>(output.usedFrameCount)
    };
    env->SetFloatArrayRegion(outMetrics, 0, 4, metrics);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_webappypie_optilens_core_imaging_fusion_NativeFusionBridge_nativeToneMapAndColor(
    JNIEnv* env,
    jclass /* clazz */,
    jfloatArray inY,
    jfloatArray inU,
    jfloatArray inV,
    jint width,
    jint height,
    jboolean enableHighlightRollOff,
    jboolean enableShadowRecovery,
    jfloat shadowLiftAmount,
    jfloat highlightKnee,
    jfloat exposureCompensation,
    jboolean enableNightHighlightProtection,
    jint profile,
    jboolean enableAwb,
    jfloat awbGain,
    jboolean protectSkinTones,
    jfloat sharpnessBoost,
    jboolean enableChromaCleanup,
    jboolean conservativeSharpening,
    jbyteArray outY,
    jbyteArray outU,
    jbyteArray outV
) {
    if (!inY || !inU || !inV || !outY || !outU || !outV || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    const int totalPixels = width * height;

    jfloat* yData = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(inY, nullptr));
    if (!yData) return JNI_FALSE;
    jfloat* uData = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(inU, nullptr));
    if (!uData) {
        env->ReleasePrimitiveArrayCritical(inY, yData, JNI_ABORT);
        return JNI_FALSE;
    }
    jfloat* vData = static_cast<jfloat*>(env->GetPrimitiveArrayCritical(inV, nullptr));
    if (!vData) {
        env->ReleasePrimitiveArrayCritical(inU, uData, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(inY, yData, JNI_ABORT);
        return JNI_FALSE;
    }

    jbyte* outYData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(outY, nullptr));
    jbyte* outUData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(outU, nullptr));
    jbyte* outVData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(outV, nullptr));

    if (!outYData || !outUData || !outVData) {
        if (outVData) env->ReleasePrimitiveArrayCritical(outV, outVData, JNI_ABORT);
        if (outUData) env->ReleasePrimitiveArrayCritical(outU, outUData, JNI_ABORT);
        if (outYData) env->ReleasePrimitiveArrayCritical(outY, outYData, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(inV, vData, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(inU, uData, JNI_ABORT);
        env->ReleasePrimitiveArrayCritical(inY, yData, JNI_ABORT);
        return JNI_FALSE;
    }

    // 1. Tone Mapping (Shadow recovery + Highlight roll-off + Filmic S-curve + Night Highlight Protection)
    std::vector<float> toneMappedY(totalPixels);
    optilens::ToneMapperParams tmParams;
    tmParams.enableHighlightRollOff = (enableHighlightRollOff == JNI_TRUE);
    tmParams.enableShadowRecovery = (enableShadowRecovery == JNI_TRUE);
    tmParams.shadowLiftAmount = shadowLiftAmount;
    tmParams.highlightKnee = highlightKnee;
    tmParams.exposureCompensation = exposureCompensation;
    tmParams.enableNightHighlightProtection = (enableNightHighlightProtection == JNI_TRUE);

    optilens::ToneMapper::mapLuminance(yData, width, height, tmParams, toneMappedY.data());

    // 2. Color Correction (AWB + Profile + Skin Tone Protection + Chroma Cleanup + Conservative Sharpening)
    optilens::ColorCorrectionParams ccParams;
    ccParams.profile = static_cast<optilens::NativeColorProfile>(profile);
    ccParams.enableAwb = (enableAwb == JNI_TRUE);
    ccParams.awbGain = awbGain;
    ccParams.protectSkinTones = (protectSkinTones == JNI_TRUE);
    ccParams.sharpnessBoost = sharpnessBoost;
    ccParams.enableChromaCleanup = (enableChromaCleanup == JNI_TRUE);
    ccParams.conservativeSharpening = (conservativeSharpening == JNI_TRUE);

    optilens::ColorCorrector::correct(
        toneMappedY.data(),
        uData,
        vData,
        width,
        height,
        ccParams,
        reinterpret_cast<uint8_t*>(outYData),
        reinterpret_cast<uint8_t*>(outUData),
        reinterpret_cast<uint8_t*>(outVData)
    );

    env->ReleasePrimitiveArrayCritical(outV, outVData, 0);
    env->ReleasePrimitiveArrayCritical(outU, outUData, 0);
    env->ReleasePrimitiveArrayCritical(outY, outYData, 0);
    env->ReleasePrimitiveArrayCritical(inV, vData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(inU, uData, JNI_ABORT);
    env->ReleasePrimitiveArrayCritical(inY, yData, JNI_ABORT);

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_webappypie_optilens_core_imaging_portrait_NativePortraitBridge_nativeProcessPortrait(
    JNIEnv* env,
    jclass /* clazz */,
    jbyteArray yPlane,
    jbyteArray uPlane,
    jbyteArray vPlane,
    jint width,
    jint height,
    jint yStride,
    jint uvStride,
    jfloatArray faceBoxesArray,
    jfloatArray landmarksArray,
    jfloat apertureFNumber,
    jfloat skinSmoothingStrength,
    jfloat faceEvCompensation,
    jboolean isBacklit,
    jboolean enableDetailProtection,
    jboolean enableEyeSparkle
) {
    if (!yPlane || !uPlane || !vPlane || width <= 0 || height <= 0) {
        return JNI_FALSE;
    }

    jbyte* yData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(yPlane, nullptr));
    jbyte* uData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(uPlane, nullptr));
    jbyte* vData = static_cast<jbyte*>(env->GetPrimitiveArrayCritical(vPlane, nullptr));

    if (!yData || !uData || !vData) {
        if (vData) env->ReleasePrimitiveArrayCritical(vPlane, vData, JNI_ABORT);
        if (uData) env->ReleasePrimitiveArrayCritical(uPlane, uData, JNI_ABORT);
        if (yData) env->ReleasePrimitiveArrayCritical(yPlane, yData, JNI_ABORT);
        return JNI_FALSE;
    }

    // Parse face boxes: each face has 5 floats [left, top, right, bottom, meanLuminance]
    std::vector<optilens::FaceBox> faces;
    if (faceBoxesArray != nullptr) {
        jsize faceCountFloats = env->GetArrayLength(faceBoxesArray);
        if (faceCountFloats >= 5) {
            std::vector<float> faceFloats(faceCountFloats);
            env->GetFloatArrayRegion(faceBoxesArray, 0, faceCountFloats, faceFloats.data());
            for (size_t i = 0; i + 4 < faceFloats.size(); i += 5) {
                optilens::FaceBox fb;
                fb.left = faceFloats[i];
                fb.top = faceFloats[i + 1];
                fb.right = faceFloats[i + 2];
                fb.bottom = faceFloats[i + 3];
                fb.meanLuminance = faceFloats[i + 4];
                faces.push_back(fb);
            }
        }
    }

    // Parse landmarks: each landmark has 4 floats [type, x, y, radius]
    std::vector<optilens::LandmarkCoord> landmarks;
    if (landmarksArray != nullptr) {
        jsize lmCountFloats = env->GetArrayLength(landmarksArray);
        if (lmCountFloats >= 4) {
            std::vector<float> lmFloats(lmCountFloats);
            env->GetFloatArrayRegion(landmarksArray, 0, lmCountFloats, lmFloats.data());
            for (size_t i = 0; i + 3 < lmFloats.size(); i += 4) {
                optilens::LandmarkCoord lm;
                lm.type = static_cast<int>(lmFloats[i]);
                lm.x = lmFloats[i + 1];
                lm.y = lmFloats[i + 2];
                lm.radius = lmFloats[i + 3];
                landmarks.push_back(lm);
            }
        }
    }

    optilens::PortraitParams params;
    params.apertureFNumber = apertureFNumber;
    params.skinSmoothingStrength = skinSmoothingStrength;
    params.faceEvCompensation = faceEvCompensation;
    params.isBacklit = (isBacklit == JNI_TRUE);
    params.enableDetailProtection = (enableDetailProtection == JNI_TRUE);
    params.enableEyeSparkle = (enableEyeSparkle == JNI_TRUE);

    bool success = optilens::PortraitProcessor::processPortrait(
        reinterpret_cast<uint8_t*>(yData),
        reinterpret_cast<uint8_t*>(uData),
        reinterpret_cast<uint8_t*>(vData),
        width,
        height,
        yStride,
        uvStride,
        faces,
        landmarks,
        params
    );

    env->ReleasePrimitiveArrayCritical(vPlane, vData, 0);
    env->ReleasePrimitiveArrayCritical(uPlane, uData, 0);
    env->ReleasePrimitiveArrayCritical(yPlane, yData, 0);

    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
