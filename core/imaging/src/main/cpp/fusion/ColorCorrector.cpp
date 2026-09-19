#include "ColorCorrector.hpp"
#include <algorithm>
#include <cmath>

namespace optilens {

float ColorCorrector::computeSkinProbability(float u, float v) {
    // Human skin locus in YCbCr/YUV space: U around 109, V around 152
    const float du = (u - 109.0f) / 18.0f;
    const float dv = (v - 152.0f) / 20.0f;
    const float distSq = du * du + dv * dv;
    return std::exp(-0.5f * distSq);
}

void ColorCorrector::applyDetailEnhancement(
    const float* inY,
    int width,
    int height,
    float sharpnessBoost,
    uint8_t* outY
) {
    if (!inY || !outY || width <= 0 || height <= 0) return;

    const float noiseFloor = 3.5f;
    const float maxBoost = 14.0f; // Prevent ringing / halo artifacts on sharp contrast edges

    for (int y = 0; y < height; ++y) {
        const int yPrev = (y > 0) ? (y - 1) : y;
        const int yNext = (y < height - 1) ? (y + 1) : y;

        const float* rowPrev = inY + yPrev * width;
        const float* rowCurr = inY + y * width;
        const float* rowNext = inY + yNext * width;
        uint8_t* rowOut = outY + y * width;

        for (int x = 0; x < width; ++x) {
            const int xPrev = (x > 0) ? (x - 1) : x;
            const int xNext = (x < width - 1) ? (x + 1) : x;

            // 3x3 box blur for local low-frequency estimate
            const float blur = (
                rowPrev[xPrev] + rowPrev[x] + rowPrev[xNext] +
                rowCurr[xPrev] + rowCurr[x] + rowCurr[xNext] +
                rowNext[xPrev] + rowNext[x] + rowNext[xNext]
            ) / 9.0f;

            const float center = rowCurr[x];
            const float detail = center - blur;
            const float absDetail = std::abs(detail);

            float delta = 0.0f;
            if (sharpnessBoost > 0.0f && absDetail > noiseFloor) {
                // Modulate boost: gentle ramp past noise floor up to full boost
                const float weight = std::min(1.0f, (absDetail - noiseFloor) / 4.0f);
                delta = detail * sharpnessBoost * weight;
                delta = std::max(-maxBoost, std::min(maxBoost, delta));
            }

            const float enhanced = center + delta;
            rowOut[x] = static_cast<uint8_t>(std::max(0.0f, std::min(255.0f, enhanced)));
        }
    }
}

void ColorCorrector::correct(
    const float* inY,
    const float* inU,
    const float* inV,
    int width,
    int height,
    const ColorCorrectionParams& params,
    uint8_t* outY,
    uint8_t* outU,
    uint8_t* outV
) {
    if (!inY || !inU || !inV || !outY || !outU || !outV || width <= 0 || height <= 0) return;

    const int totalPixels = width * height;

    // 1. Auto White Balance (AWB) via Gray World Chromaticity
    float shiftU = 0.0f;
    float shiftV = 0.0f;

    if (params.enableAwb && params.awbGain > 0.0f) {
        double sumU = 0.0;
        double sumV = 0.0;
        int count = 0;

        // Sample midtones to evaluate illuminant cast
        for (int i = 0; i < totalPixels; i += 4) {
            const float y = inY[i];
            if (y > 35.0f && y < 220.0f) {
                sumU += inU[i];
                sumV += inV[i];
                count++;
            }
        }

        if (count > 0) {
            const float meanU = static_cast<float>(sumU / count);
            const float meanV = static_cast<float>(sumV / count);
            // Gray-world offset from neutral 128
            const float rawShiftU = (meanU - 128.0f) * params.awbGain;
            const float rawShiftV = (meanV - 128.0f) * params.awbGain;
            // Clamp shift to prevent color inversion on intentionally colored lighting
            shiftU = std::max(-18.0f, std::min(18.0f, rawShiftU));
            shiftV = std::max(-18.0f, std::min(18.0f, rawShiftV));
        }
    }

    // Profile saturation multipliers
    float targetSat = 1.05f; // DEFAULT
    if (params.profile == NativeColorProfile::NATURAL) {
        targetSat = 0.92f;
    } else if (params.profile == NativeColorProfile::VIVID) {
        targetSat = 1.25f;
    }

    // 2. Chrominance grading: AWB + Saturation Profile + Skin Tone Protection
    for (int i = 0; i < totalPixels; ++i) {
        // Apply AWB shift
        float u = inU[i] - shiftU;
        float v = inV[i] - shiftV;

        float uDiff = u - 128.0f;
        float vDiff = v - 128.0f;

        float satMultiplier = targetSat;

        // Skin Tone Protection
        if (params.protectSkinTones) {
            const float skinProb = computeSkinProbability(u, v);
            if (skinProb > 0.02f) {
                // Dampen saturation change on skin pixels
                const float skinDamping = 1.0f - (0.80f * skinProb);
                satMultiplier = 1.0f + (targetSat - 1.0f) * skinDamping;
            }
        }

        // Vibrance protection in VIVID mode: dampen already oversaturated pixels
        if (params.profile == NativeColorProfile::VIVID) {
            const float currentSat = std::sqrt(uDiff * uDiff + vDiff * vDiff);
            if (currentSat > 60.0f) {
                const float vibranceDamp = std::max(0.2f, 1.0f - (currentSat - 60.0f) / 70.0f);
                satMultiplier = 1.0f + (satMultiplier - 1.0f) * vibranceDamp;
            }
        }

        u = 128.0f + uDiff * satMultiplier;
        v = 128.0f + vDiff * satMultiplier;

        outU[i] = static_cast<uint8_t>(std::max(0.0f, std::min(255.0f, u)));
        outV[i] = static_cast<uint8_t>(std::max(0.0f, std::min(255.0f, v)));
    }

    // 3. Luminance detail enhancement (Noise-aware unsharp masking)
    applyDetailEnhancement(inY, width, height, params.sharpnessBoost, outY);
}

} // namespace optilens
