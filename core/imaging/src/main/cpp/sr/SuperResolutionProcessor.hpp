#ifndef OPTILENS_SUPER_RESOLUTION_PROCESSOR_HPP
#define OPTILENS_SUPER_RESOLUTION_PROCESSOR_HPP

#include <cstdint>
#include <vector>
#include <cmath>
#include <string>

namespace optilens {

/**
 * Method of super resolution used to synthesize higher-frequency spatial detail.
 */
enum class SrMethod {
    OPTICAL_NATIVE = 0,
    MULTI_FRAME_SR = 1,
    SINGLE_FRAME_EDGE_SR = 2,
    BICUBIC_BASELINE = 3,
    LANCZOS_BASELINE = 4,
    SHARPENED_UPSCALE_BASELINE = 5
};

/**
 * Configuration parameters for Super Resolution processing.
 */
struct SuperResolutionParams {
    float scaleFactor = 2.0f;             // 2.0f (production baseline) or 4.0f (Pro gate)
    float confidenceThreshold = 0.65f;     // Minimum alignment confidence to contribute to HR grid
    float coringThreshold = 6.0f;          // Luminance noise coring gate to prevent noise amplification
    bool enableHaloSuppression = true;     // Suppress unsharp masking ringing along high-contrast boundaries
    float residualRejectionThreshold = 28.0f; // Max photometric difference before rejecting as motion artifact
    float sharpnessBoost = 0.25f;          // High-frequency detail restoration weight
    int tileSize = 256;                    // Tiled memory window width/height
    int tileOverlap = 32;                  // Raised-cosine blend margin across tiles
};

/**
 * Metric result from comparing super-resolution upscaling methods.
 */
struct SrBenchmarkEntry {
    int methodId = 0;                      // SrMethod enum value
    float durationMs = 0.0f;               // Processing latency in milliseconds
    float psnrDb = 0.0f;                   // Peak Signal-to-Noise Ratio (higher is better)
    float ssim = 0.0f;                     // Structural Similarity Index (0.0 to 1.0)
    float acutanceScore = 0.0f;            // Tenengrad gradient energy (edge sharpness)
    uint64_t memoryBytes = 0;              // Peak temporary memory consumption
};

/**
 * Production Super Resolution & AI Zoom Engine.
 *
 * Implements:
 * 1. Multi-Frame Super Resolution (MFSR):
 *    - Sub-pixel shift estimation using luminance spatial Taylor series expansion.
 *    - Normalized kernel splatting onto 2x or 4x high-resolution discrete grid.
 *    - Motion-aware confidence weighting and ghost artifact suppression.
 *    - Sensor point spread function (PSF) deconvolution.
 * 2. Single-Frame Fallback (SFSR):
 *    - Directional edge-directed interpolation (EDI) along local edge contours.
 *    - Anisotropic Lanczos-3 filtering to avoid staircasing/aliasing and blur.
 *    - Halo-suppressed micro-contrast enhancement.
 * 3. Tiled execution:
 *    - Bounded memory footprint (< 25MB) with Hann window edge-blending.
 * 4. Comparative benchmarking against standard bicubic, Lanczos, and sharpened upscale.
 */
class SuperResolutionProcessor {
public:
    /**
     * Process multi-frame stack into high-resolution output buffers.
     */
    static bool processMultiFrameSr(
        const uint8_t* refY,
        const uint8_t* refU,
        const uint8_t* refV,
        const std::vector<const uint8_t*>& candYList,
        const std::vector<const uint8_t*>& candUList,
        const std::vector<const uint8_t*>& candVList,
        const std::vector<const uint8_t*>& ghostMaskList,
        const std::vector<std::pair<float, float>>& subPixelShifts,
        int inWidth,
        int inHeight,
        int inYStride,
        int inUvStride,
        const SuperResolutionParams& params,
        uint8_t* outY,
        uint8_t* outU,
        uint8_t* outV,
        int outWidth,
        int outHeight
    );

    /**
     * Process single image using edge-directed directional interpolation fallback.
     */
    static bool processSingleFrameSr(
        const uint8_t* inY,
        const uint8_t* inU,
        const uint8_t* inV,
        int inWidth,
        int inHeight,
        int inYStride,
        int inUvStride,
        const SuperResolutionParams& params,
        uint8_t* outY,
        uint8_t* outU,
        uint8_t* outV,
        int outWidth,
        int outHeight
    );

    /**
     * Estimates sub-pixel translation (dx, dy) in pixels between candidate and reference frame.
     */
    static std::pair<float, float> estimateSubPixelShift(
        const uint8_t* refY,
        const uint8_t* candY,
        int width,
        int height,
        int stride
    );

    /**
     * Runs standardized comparative benchmark on a representative patch.
     * Returns 5 entries: Bicubic, Lanczos, Sharpened Upscale, Single-Frame SR, Multi-Frame SR.
     */
    static std::vector<SrBenchmarkEntry> runBenchmark(
        const uint8_t* testY,
        int width,
        int height,
        int stride,
        float scaleFactor
    );

    /**
     * Computes Tenengrad sharpness metric (sum of squared Sobel gradients normalized by pixel count).
     */
    static float computeTenengradAcutance(
        const uint8_t* yPlane,
        int width,
        int height,
        int stride
    );

    /**
     * Computes PSNR and SSIM against ground truth image.
     */
    static std::pair<float, float> computePsnrAndSsim(
        const uint8_t* testY,
        const uint8_t* groundTruthY,
        int width,
        int height
    );
};

} // namespace optilens

#endif // OPTILENS_SUPER_RESOLUTION_PROCESSOR_HPP
