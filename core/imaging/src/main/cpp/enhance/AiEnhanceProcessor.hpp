#pragma once

#include <vector>
#include <cstdint>
#include <functional>

namespace optilens {

enum class NativeEnhanceStage : int {
    ANALYZING_SCENE = 0,
    BALANCING_EXPOSURE = 1,
    REDUCING_NOISE = 2,
    ENHANCING_DETAILS = 3,
    COLOR_HARMONY = 4,
    COMPLETED = 5
};

struct SceneMetrics {
    float meanLuminance;
    float shadowFraction;   // fraction of pixels with Y < 50
    float highlightFraction; // fraction of pixels with Y > 200
    float noiseEstimate;
};

struct EnhanceParams {
    float strength;
    bool preserveSkinTones;
    bool enableExposureBalancing;
    bool enableNoiseReduction;
    bool enableDetailEnhancement;
    bool enableColorHarmony;
};

class AiEnhanceProcessor {
public:
    using ProgressCallback = std::function<void(NativeEnhanceStage, float)>;

    static SceneMetrics analyzeScene(
        const uint8_t* yPlane,
        int width,
        int height,
        int yStride
    );

    static void balanceExposure(
        uint8_t* yPlane,
        int width,
        int height,
        int yStride,
        const SceneMetrics& metrics,
        float strength
    );

    static void reduceNoise(
        uint8_t* yPlane,
        const uint8_t* uPlane,
        const uint8_t* vPlane,
        int width,
        int height,
        int yStride,
        int uvStride,
        float strength
    );

    static void enhanceDetails(
        uint8_t* yPlane,
        int width,
        int height,
        int yStride,
        float strength
    );

    static void enhanceColorHarmony(
        uint8_t* uPlane,
        uint8_t* vPlane,
        int uvWidth,
        int uvHeight,
        int uvStride,
        bool preserveSkin,
        float strength
    );

    static bool process(
        uint8_t* yPlane,
        uint8_t* uPlane,
        uint8_t* vPlane,
        int width,
        int height,
        int yStride,
        int uvStride,
        const EnhanceParams& params,
        ProgressCallback progressCb = nullptr
    );

    static float computeSkinProbability(float u, float v);

private:
    static inline uint8_t clampToUint8(float v) {
        if (v <= 0.0f) return 0;
        if (v >= 255.0f) return 255;
        return static_cast<uint8_t>(v + 0.5f);
    }
};

} // namespace optilens
