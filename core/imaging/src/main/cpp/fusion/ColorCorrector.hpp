#ifndef OPTILENS_COLOR_CORRECTOR_HPP
#define OPTILENS_COLOR_CORRECTOR_HPP

#include <cstdint>
#include <vector>

namespace optilens {

enum class NativeColorProfile : int {
    DEFAULT = 0,
    NATURAL = 1,
    VIVID = 2
};

struct ColorCorrectionParams {
    NativeColorProfile profile = NativeColorProfile::DEFAULT;
    bool enableAwb = true;
    float awbGain = 0.40f;             // Gray-world AWB correction intensity (0.0 to 1.0)
    bool protectSkinTones = true;      // Prevent oversaturation of human skin
    float sharpnessBoost = 0.25f;      // Noise-aware unsharp detail enhancement (0.0 to 1.0)
};

class ColorCorrector {
public:
    /**
     * Executes color grading, gray-world AWB, color profiling (DEFAULT, NATURAL, VIVID),
     * skin tone protection, and noise-aware detail enhancement.
     *
     * @param inY Tone-mapped luminance [0..255]
     * @param inU Fused chrominance U [0..255]
     * @param inV Fused chrominance V [0..255]
     * @param width Frame width
     * @param height Frame height
     * @param params Color correction parameters
     * @param outY Final enhanced luminance [0..255]
     * @param outU Final graded chrominance U [0..255]
     * @param outV Final graded chrominance V [0..255]
     */
    static void correct(
        const float* inY,
        const float* inU,
        const float* inV,
        int width,
        int height,
        const ColorCorrectionParams& params,
        uint8_t* outY,
        uint8_t* outU,
        uint8_t* outV
    );

    /**
     * Computes human skin probability metric P_skin in [0..1] based on YUV chrominance locus.
     */
    static float computeSkinProbability(float u, float v);

    /**
     * Noise-aware detail enhancement operator applying unsharp masking only above noise threshold.
     */
    static void applyDetailEnhancement(
        const float* inY,
        int width,
        int height,
        float sharpnessBoost,
        uint8_t* outY
    );
};

} // namespace optilens

#endif // OPTILENS_COLOR_CORRECTOR_HPP
