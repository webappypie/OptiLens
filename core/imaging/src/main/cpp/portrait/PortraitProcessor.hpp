#pragma once

#include <cstdint>
#include <vector>
#include <cmath>

namespace optilens {

/**
 * Normalized 2D bounding box representing a detected face.
 */
struct FaceBox {
    float left;    // [0, 1]
    float top;     // [0, 1]
    float right;   // [0, 1]
    float bottom;  // [0, 1]
    float meanLuminance;
};

/**
 * Facial landmark anatomical point for detail protection and eye sparkle.
 */
struct LandmarkCoord {
    int type;      // 0=LEFT_EYE, 1=RIGHT_EYE, 2=LEFT_EYEBROW, 3=RIGHT_EYEBROW, 4=NOSE, 5=LIPS
    float x;       // [0, 1]
    float y;       // [0, 1]
    float radius;  // normalized radius of protection
};

/**
 * Execution parameters for portrait and face-aware processing.
 */
struct PortraitParams {
    float apertureFNumber;          // e.g. 1.4, 2.0, 2.8, 4.0, 5.6, 8.0
    float skinSmoothingStrength;    // 0.0 to 1.0 (default 0.25)
    float faceEvCompensation;       // EV compensation for backlit faces (0.0 to +1.5)
    bool isBacklit;
    bool enableDetailProtection;    // protect eyes, lips, brows
    bool enableEyeSparkle;          // +15% micro-contrast in eyes
};

/**
 * High-performance C++ Portrait Processing Engine.
 *
 * Implements:
 * 1. Multi-face depth map generation (foreground subjects at Z=0).
 * 2. Optical disc convolution bokeh with specular highlight blooming.
 * 3. Face-aware exposure balancing with smooth Gaussian falloff.
 * 4. Skin tone probability estimation across Fitzpatrick Types I to VI.
 * 5. Selective bilateral skin cleanup with anatomical detail protection (eyes, brows, lips).
 * 6. Eye sparkle micro-contrast enhancement.
 */
class PortraitProcessor {
public:
    /**
     * Executes complete portrait processing on YUV420 planar image.
     */
    static bool processPortrait(
        uint8_t* yPlane,
        uint8_t* uPlane,
        uint8_t* vPlane,
        int width,
        int height,
        int yStride,
        int uvStride,
        const std::vector<FaceBox>& faces,
        const std::vector<LandmarkCoord>& landmarks,
        const PortraitParams& params
    );

    /**
     * Estimates skin probability P_skin in [0.0, 1.0] across Fitzpatrick Types I-VI
     * using an elliptical Mahalanobis model in the U-V chroma plane.
     */
    static float computeSkinProbability(float u, float v);

    /**
     * Generates normalized depth map Z in [0.0, 1.0] where detected subjects are Z=0.0
     * and background is Z=1.0 with smooth boundary feathering.
     */
    static void generateDepthMap(
        const std::vector<FaceBox>& faces,
        int width,
        int height,
        std::vector<float>& outDepthMap
    );

    /**
     * Synthesizes realistic optical bokeh using a circular aperture disc kernel
     * and blooms specular highlights into circular bokeh orbs.
     */
    static void applyOpticalDiscBokeh(
        uint8_t* yPlane,
        uint8_t* uPlane,
        uint8_t* vPlane,
        const float* depthMap,
        int width,
        int height,
        int yStride,
        int uvStride,
        float maxBlurRadius
    );

    /**
     * Recovers underexposed faces in backlit scenes with smooth Gaussian attenuation.
     */
    static void applyFaceExposureBalancing(
        uint8_t* yPlane,
        int width,
        int height,
        int yStride,
        const std::vector<FaceBox>& faces,
        float evCompensation
    );

    /**
     * Mild skin smoothing using a bilateral filter combined with landmark detail protection.
     */
    static void applySkinSmoothingAndDetailProtection(
        uint8_t* yPlane,
        const uint8_t* uPlane,
        const uint8_t* vPlane,
        int width,
        int height,
        int yStride,
        int uvStride,
        const std::vector<FaceBox>& faces,
        const std::vector<LandmarkCoord>& landmarks,
        float smoothingStrength,
        bool eyeSparkle
    );
};

} // namespace optilens
