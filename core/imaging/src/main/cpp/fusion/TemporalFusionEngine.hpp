#ifndef OPTILENS_TEMPORAL_FUSION_ENGINE_HPP
#define OPTILENS_TEMPORAL_FUSION_ENGINE_HPP

#include <cstdint>
#include <vector>

namespace optilens {

/**
 * Input descriptor for an individual frame participating in multi-frame fusion.
 */
struct FusionFrameInput {
    const uint8_t* yPlane;       // Luminance buffer
    const uint8_t* uPlane;       // Chrominance U/Cb buffer (or interleaved UV)
    const uint8_t* vPlane;       // Chrominance V/Cr buffer (or null if interleaved)
    int uvPixelStride;           // 1 for planar, 2 for interleaved NV21/NV12
    int uvRowStride;             // Row stride for chroma
    const uint8_t* ghostMask;    // Null for reference, or binary/probabilistic mask (> 128 is motion)
    const float* homography;     // 3x3 matrix mapping reference (x, y) to candidate (cx, cy)
    float exposureFactor;        // (exposureTime * iso) / (refExposureTime * refIso), ref = 1.0
    bool isReference;
};

/**
 * Fused radiance and chrominance output buffers with quality diagnostics.
 */
struct FusionOutput {
    int width;
    int height;
    std::vector<float> fusedY;   // Scene radiance normalized to reference exposure [0..255+]
    std::vector<float> fusedU;   // Chrominance U [0..255]
    std::vector<float> fusedV;   // Chrominance V [0..255]
    float snrGainDb;             // Calculated SNR improvement in decibels (10 * log10(N_eff))
    float dynamicRangeExtensionEv; // Dynamic range extended in EV stops
    float ghostPixelFraction;    // Fraction of pixels where ghost fallback occurred
    int usedFrameCount;          // Total frames contributing to fusion
};

class TemporalFusionEngine {
public:
    /**
     * Executes multi-frame temporal fusion across the supplied aligned frames.
     *
     * Performs:
     * 1. Outlier rejection (trimmed mean / statistical filtering).
     * 2. Ghost-aware reference fallback: moving pixels (ghostMask > 128) take 100% reference frame.
     * 3. Exposure-aware HDR radiance reconstruction via Debevec-style hat weighting.
     * 4. Temporal denoising reducing variance by the effective frame count.
     */
    static FusionOutput fuse(
        const std::vector<FusionFrameInput>& frames,
        int width,
        int height,
        int stride,
        bool enableHdr = true,
        bool enableDenoise = true
    );

    /**
     * Debevec-style hat weighting function:
     * w(z) = exp(-((z - 128)^2) / (2 * sigma^2))
     * Blown highlights (z >= 250) and crushed shadows (z <= 8) are given zero/minimal weight.
     */
    static float computeDebevecWeight(float pixelValue);

private:
    static float sampleBilinear(
        const uint8_t* plane,
        int width,
        int height,
        int stride,
        float x,
        float y
    );
};

} // namespace optilens

#endif // OPTILENS_TEMPORAL_FUSION_ENGINE_HPP
