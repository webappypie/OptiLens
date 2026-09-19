#include "AiEnhanceProcessor.hpp"
#include <cmath>
#include <algorithm>
#include <numeric>

namespace optilens {

float AiEnhanceProcessor::computeSkinProbability(float u, float v) {
    const float u0 = 112.0f;
    const float v0 = 152.0f;
    const float du = u - u0;
    const float dv = v - v0;

    const float cosT = 0.81915f;
    const float sinT = -0.57357f;

    const float xr = cosT * du - sinT * dv;
    const float yr = sinT * du + cosT * dv;

    const float sigmaX = 22.0f;
    const float sigmaY = 14.0f;

    const float d2 = (xr * xr) / (sigmaX * sigmaX) + (yr * yr) / (sigmaY * sigmaY);
    if (d2 > 8.0f) return 0.0f;

    const float p = std::exp(-0.5f * d2);
    return (p > 0.05f) ? p : 0.0f;
}

SceneMetrics AiEnhanceProcessor::analyzeScene(
    const uint8_t* yPlane,
    int width,
    int height,
    int yStride
) {
    SceneMetrics metrics{0.0f, 0.0f, 0.0f, 0.0f};
    if (!yPlane || width <= 0 || height <= 0) return metrics;

    int64_t sumLuma = 0;
    int shadowPixels = 0;
    int highlightPixels = 0;
    const int totalPixels = width * height;

    // Subsample step for fast analysis on high-res images
    const int step = std::max(1, static_cast<int>(std::sqrt(totalPixels / 10000.0f)));
    int sampledCount = 0;

    for (int y = 0; y < height; y += step) {
        const uint8_t* row = yPlane + y * yStride;
        for (int x = 0; x < width; x += step) {
            const uint8_t yVal = row[x];
            sumLuma += yVal;
            if (yVal < 50) shadowPixels++;
            if (yVal > 200) highlightPixels++;
            sampledCount++;
        }
    }

    if (sampledCount > 0) {
        metrics.meanLuminance = static_cast<float>(sumLuma) / sampledCount;
        metrics.shadowFraction = static_cast<float>(shadowPixels) / sampledCount;
        metrics.highlightFraction = static_cast<float>(highlightPixels) / sampledCount;
    }

    return metrics;
}

void AiEnhanceProcessor::balanceExposure(
    uint8_t* yPlane,
    int width,
    int height,
    int yStride,
    const SceneMetrics& metrics,
    float strength
) {
    if (!yPlane || width <= 0 || height <= 0) return;

    // Determine shadow lift based on under-exposure and shadow density
    const float baseLift = (metrics.shadowFraction > 0.12f || metrics.meanLuminance < 115.0f)
        ? std::min(1.8f, 0.5f + (115.0f - metrics.meanLuminance) / 75.0f)
        : 0.35f;
    const float effectiveLift = baseLift * strength;

    for (int y = 0; y < height; ++y) {
        uint8_t* row = yPlane + y * yStride;
        for (int x = 0; x < width; ++x) {
            const float yVal = static_cast<float>(row[x]);
            const float normY = yVal / 255.0f;

            // Parametric shadow lift curve: peaks in low midtones (~Y=60..90)
            const float shadowWeight = (1.0f - normY) * std::sqrt(normY) * 2.0f;
            float newY = yVal + effectiveLift * 35.0f * shadowWeight;

            // Highlight knee compression to protect bright areas from blowout
            if (newY > 210.0f) {
                const float excess = newY - 210.0f;
                newY = 210.0f + excess * 0.70f;
            }

            row[x] = clampToUint8(newY);
        }
    }
}

void AiEnhanceProcessor::reduceNoise(
    uint8_t* yPlane,
    const uint8_t* uPlane,
    const uint8_t* vPlane,
    int width,
    int height,
    int yStride,
    int uvStride,
    float strength
) {
    if (!yPlane || width < 4 || height < 4) return;

    const float sigmaSpatial = 2.0f;
    const float sigmaRange = 25.0f;
    const float twoS2 = 2.0f * sigmaSpatial * sigmaSpatial;
    const float twoR2 = 2.0f * sigmaRange * sigmaRange;

    // Allocate temporary row buffers for in-place safe filtering
    std::vector<uint8_t> filteredRow(width);

    for (int y = 2; y < height - 2; ++y) {
        const uint8_t* currRow = yPlane + y * yStride;
        const int uvY = y / 2;
        const uint8_t* uRow = (uPlane) ? uPlane + uvY * uvStride : nullptr;
        const uint8_t* vRow = (vPlane) ? vPlane + uvY * uvStride : nullptr;

        for (int x = 2; x < width - 2; ++x) {
            const float centerVal = static_cast<float>(currRow[x]);

            // Selective filtering: only shadows (Y < 75) or skin pixels
            bool shouldFilter = (centerVal < 75.0f);
            if (!shouldFilter && uRow && vRow) {
                const float uVal = uRow[x / 2];
                const float vVal = vRow[x / 2];
                if (computeSkinProbability(uVal, vVal) > 0.20f) {
                    shouldFilter = true;
                }
            }

            if (!shouldFilter) {
                filteredRow[x] = currRow[x];
                continue;
            }

            float sumWeights = 0.0f;
            float sumValues = 0.0f;

            for (int dy = -2; dy <= 2; ++dy) {
                const uint8_t* neighborRow = yPlane + (y + dy) * yStride;
                for (int dx = -2; dx <= 2; ++dx) {
                    const float neighborVal = static_cast<float>(neighborRow[x + dx]);
                    const float sDist2 = static_cast<float>(dx * dx + dy * dy);
                    const float diff = centerVal - neighborVal;
                    const float rDist2 = diff * diff;

                    const float w = std::exp(-sDist2 / twoS2 - rDist2 / twoR2);
                    sumWeights += w;
                    sumValues += w * neighborVal;
                }
            }

            if (sumWeights > 0.001f) {
                const float smoothed = sumValues / sumWeights;
                const float finalVal = (1.0f - strength * 0.60f) * centerVal + (strength * 0.60f) * smoothed;
                filteredRow[x] = clampToUint8(finalVal);
            } else {
                filteredRow[x] = currRow[x];
            }
        }

        // Copy filtered pixels back to row
        uint8_t* targetRow = yPlane + y * yStride;
        for (int x = 2; x < width - 2; ++x) {
            targetRow[x] = filteredRow[x];
        }
    }
}

void AiEnhanceProcessor::enhanceDetails(
    uint8_t* yPlane,
    int width,
    int height,
    int yStride,
    float strength
) {
    if (!yPlane || width < 3 || height < 3) return;

    const float coringThreshold = 3.0f; // Ignore very small noise fluctuations
    const float maxBoost = 20.0f;

    std::vector<uint8_t> sharpenedRow(width);

    for (int y = 1; y < height - 1; ++y) {
        const uint8_t* prevRow = yPlane + (y - 1) * yStride;
        const uint8_t* currRow = yPlane + y * yStride;
        const uint8_t* nextRow = yPlane + (y + 1) * yStride;

        for (int x = 1; x < width - 1; ++x) {
            const float center = static_cast<float>(currRow[x]);

            // 3x3 Gaussian low-pass approximation
            const float blurred = (
                (prevRow[x - 1] + prevRow[x + 1] + nextRow[x - 1] + nextRow[x + 1]) * 1.0f +
                (prevRow[x] + currRow[x - 1] + currRow[x + 1] + nextRow[x]) * 2.0f +
                center * 4.0f
            ) / 16.0f;

            const float highPass = center - blurred;

            // Noise-gating coring and shadow suppression
            float detailDelta = 0.0f;
            if (std::abs(highPass) > coringThreshold) {
                // Attenuate sharpening in deep shadows to prevent grain
                const float shadowGain = (center < 40.0f) ? (center / 40.0f) : 1.0f;
                detailDelta = highPass * strength * 0.45f * shadowGain;
                detailDelta = std::max(-maxBoost, std::min(maxBoost, detailDelta));
            }

            sharpenedRow[x] = clampToUint8(center + detailDelta);
        }

        uint8_t* targetRow = yPlane + y * yStride;
        for (int x = 1; x < width - 1; ++x) {
            targetRow[x] = sharpenedRow[x];
        }
    }
}

void AiEnhanceProcessor::enhanceColorHarmony(
    uint8_t* uPlane,
    uint8_t* vPlane,
    int uvWidth,
    int uvHeight,
    int uvStride,
    bool preserveSkin,
    float strength
) {
    if (!uPlane || !vPlane || uvWidth <= 0 || uvHeight <= 0) return;

    for (int y = 0; y < uvHeight; ++y) {
        uint8_t* uRow = uPlane + y * uvStride;
        uint8_t* vRow = vPlane + y * uvStride;

        for (int x = 0; x < uvWidth; ++x) {
            const float uVal = static_cast<float>(uRow[x]);
            const float vVal = static_cast<float>(vRow[x]);

            const float du = uVal - 128.0f;
            const float dv = vVal - 128.0f;
            const float chroma = std::sqrt(du * du + dv * dv);

            // Calculate skin protection weight
            float pSkin = 0.0f;
            if (preserveSkin) {
                pSkin = computeSkinProbability(uVal, vVal);
            }

            // Vibrance: boost muted colors more than saturated colors
            const float vibranceFactor = std::max(0.0f, 1.0f - (chroma / 90.0f));
            const float effectiveBoost = 1.0f + (strength * 0.22f * vibranceFactor * (1.0f - pSkin));

            uRow[x] = clampToUint8(128.0f + du * effectiveBoost);
            vRow[x] = clampToUint8(128.0f + dv * effectiveBoost);
        }
    }
}

bool AiEnhanceProcessor::process(
    uint8_t* yPlane,
    uint8_t* uPlane,
    uint8_t* vPlane,
    int width,
    int height,
    int yStride,
    int uvStride,
    const EnhanceParams& params,
    ProgressCallback progressCb
) {
    if (!yPlane || width <= 0 || height <= 0) return false;

    // Stage 1: Analyze Scene
    if (progressCb) progressCb(NativeEnhanceStage::ANALYZING_SCENE, 0.15f);
    const SceneMetrics metrics = analyzeScene(yPlane, width, height, yStride);

    // Stage 2: Balance Exposure & Dynamic Range
    if (params.enableExposureBalancing) {
        if (progressCb) progressCb(NativeEnhanceStage::BALANCING_EXPOSURE, 0.35f);
        balanceExposure(yPlane, width, height, yStride, metrics, params.strength);
    }

    // Stage 3: Reduce Noise
    if (params.enableNoiseReduction) {
        if (progressCb) progressCb(NativeEnhanceStage::REDUCING_NOISE, 0.55f);
        reduceNoise(yPlane, uPlane, vPlane, width, height, yStride, uvStride, params.strength);
    }

    // Stage 4: Enhance Details
    if (params.enableDetailEnhancement) {
        if (progressCb) progressCb(NativeEnhanceStage::ENHANCING_DETAILS, 0.75f);
        enhanceDetails(yPlane, width, height, yStride, params.strength);
    }

    // Stage 5: Color Harmony & Vibrance
    if (params.enableColorHarmony && uPlane && vPlane) {
        if (progressCb) progressCb(NativeEnhanceStage::COLOR_HARMONY, 0.90f);
        enhanceColorHarmony(uPlane, vPlane, width / 2, height / 2, uvStride, params.preserveSkinTones, params.strength);
    }

    if (progressCb) progressCb(NativeEnhanceStage::COMPLETED, 1.0f);
    return true;
}

} // namespace optilens
