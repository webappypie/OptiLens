#include "TemporalFusionEngine.hpp"
#include <cmath>
#include <algorithm>
#include <numeric>

namespace optilens {

float TemporalFusionEngine::computeDebevecWeight(float pixelValue) {
    if (pixelValue <= 4.0f || pixelValue >= 252.0f) {
        return 0.01f;
    }
    const float diff = pixelValue - 128.0f;
    const float sigma = 55.0f;
    return std::exp(-(diff * diff) / (2.0f * sigma * sigma));
}

float TemporalFusionEngine::sampleBilinear(
    const uint8_t* plane,
    int width,
    int height,
    int stride,
    float x,
    float y
) {
    x = std::max(0.0f, std::min(static_cast<float>(width - 1), x));
    y = std::max(0.0f, std::min(static_cast<float>(height - 1), y));

    const int x0 = static_cast<int>(x);
    const int y0 = static_cast<int>(y);
    const int x1 = std::min(x0 + 1, width - 1);
    const int y1 = std::min(y0 + 1, height - 1);

    const float fx = x - static_cast<float>(x0);
    const float fy = y - static_cast<float>(y0);

    const float p00 = plane[y0 * stride + x0];
    const float p10 = plane[y0 * stride + x1];
    const float p01 = plane[y1 * stride + x0];
    const float p11 = plane[y1 * stride + x1];

    const float top = p00 + fx * (p10 - p00);
    const float bot = p01 + fx * (p11 - p01);
    return top + fy * (bot - top);
}

FusionOutput TemporalFusionEngine::fuse(
    const std::vector<FusionFrameInput>& frames,
    int width,
    int height,
    int stride,
    bool enableHdr,
    bool enableDenoise
) {
    FusionOutput output{};
    output.width = width;
    output.height = height;
    const int totalPixels = width * height;
    output.fusedY.resize(totalPixels, 0.0f);
    output.fusedU.resize(totalPixels, 128.0f);
    output.fusedV.resize(totalPixels, 128.0f);

    if (frames.empty() || width <= 0 || height <= 0) {
        output.snrGainDb = 0.0f;
        output.dynamicRangeExtensionEv = 0.0f;
        output.ghostPixelFraction = 0.0f;
        output.usedFrameCount = 0;
        return output;
    }

    // Identify reference frame
    size_t refIdx = 0;
    for (size_t i = 0; i < frames.size(); ++i) {
        if (frames[i].isReference) {
            refIdx = i;
            break;
        }
    }
    const auto& refFrame = frames[refIdx];

    // Compute dynamic range extension
    float minExp = 1.0f;
    float maxExp = 1.0f;
    for (const auto& f : frames) {
        const float ef = (f.exposureFactor > 0.0f) ? f.exposureFactor : 1.0f;
        minExp = std::min(minExp, ef);
        maxExp = std::max(maxExp, ef);
    }
    output.dynamicRangeExtensionEv = (enableHdr && minExp > 0.0f) ?
        std::max(0.0f, std::log2(maxExp / minExp)) : 0.0f;

    int ghostFallbackPixels = 0;
    double cumulativeNeff = 0.0;

    // Allocate temporary scratch for samples
    const size_t numFrames = frames.size();
    std::vector<float> sampleRadiances(numFrames);
    std::vector<float> sampleWeights(numFrames);
    std::vector<float> sampleU(numFrames);
    std::vector<float> sampleV(numFrames);

    const int chromaW = (width + 1) / 2;
    const int chromaH = (height + 1) / 2;

    for (int y = 0; y < height; ++y) {
        const int yOffset = y * stride;
        const int outYOffset = y * width;
        const int cy = y / 2;

        for (int x = 0; x < width; ++x) {
            const int cx = x / 2;
            size_t validCount = 0;
            bool isGhostPixel = false;

            // 1. Reference frame sample
            const float refY = refFrame.yPlane[yOffset + x];
            const float refExp = 1.0f;
            const float refRadiance = refY / refExp;
            const float refWeight = computeDebevecWeight(refY);

            sampleRadiances[0] = refRadiance;
            sampleWeights[0] = std::max(0.05f, refWeight);

            // Sample reference Chroma
            float refU = 128.0f;
            float refV = 128.0f;
            if (refFrame.uPlane != nullptr) {
                if (refFrame.vPlane != nullptr) {
                    // Planar YUV
                    refU = refFrame.uPlane[cy * refFrame.uvRowStride + cx * refFrame.uvPixelStride];
                    refV = refFrame.vPlane[cy * refFrame.uvRowStride + cx * refFrame.uvPixelStride];
                } else {
                    // Interleaved NV21 (V then U) or NV12
                    const int cIdx = cy * refFrame.uvRowStride + cx * 2;
                    refV = refFrame.uPlane[cIdx];
                    refU = refFrame.uPlane[cIdx + 1];
                }
            }
            sampleU[0] = refU;
            sampleV[0] = refV;
            validCount = 1;

            // 2. Candidate frames samples
            for (size_t i = 0; i < numFrames; ++i) {
                if (i == refIdx) continue;

                const auto& cand = frames[i];

                // Check Ghost Mask
                if (cand.ghostMask != nullptr && cand.ghostMask[y * width + x] > 128) {
                    // Motion detected -> Ghost-aware fallback: do not fuse candidate pixel
                    isGhostPixel = true;
                    continue;
                }

                // Homography transform coordinates
                float px = static_cast<float>(x);
                float py = static_cast<float>(y);
                if (cand.homography != nullptr) {
                    const float* h = cand.homography;
                    px = h[0] * x + h[1] * y + h[2];
                    py = h[3] * x + h[4] * y + h[5];
                }

                if (px < 0.0f || px >= static_cast<float>(width - 1) ||
                    py < 0.0f || py >= static_cast<float>(height - 1)) {
                    // Outside candidate frame boundary
                    continue;
                }

                const float candY = sampleBilinear(cand.yPlane, width, height, stride, px, py);
                const float expFactor = (cand.exposureFactor > 0.0f) ? cand.exposureFactor : 1.0f;
                const float candRadiance = candY / expFactor;
                float candWeight = enableDenoise ? computeDebevecWeight(candY) : 1.0f;

                // For HDR, de-weight clipped highlights and crushed shadows
                if (enableHdr) {
                    if (candY >= 250.0f && expFactor > 1.0f) {
                        candWeight = 0.0f; // Overexposed frame highlight is clipped
                    } else if (candY <= 8.0f && expFactor < 1.0f) {
                        candWeight = 0.0f; // Underexposed frame shadow is crushed
                    }
                }

                if (candWeight <= 0.001f) continue;

                // Sample candidate chroma
                float candU = 128.0f;
                float candV = 128.0f;
                if (cand.uPlane != nullptr) {
                    const int cpx = static_cast<int>(px / 2.0f);
                    const int cpy = static_cast<int>(py / 2.0f);
                    if (cpx >= 0 && cpx < chromaW && cpy >= 0 && cpy < chromaH) {
                        if (cand.vPlane != nullptr) {
                            candU = cand.uPlane[cpy * cand.uvRowStride + cpx * cand.uvPixelStride];
                            candV = cand.vPlane[cpy * cand.uvRowStride + cpx * cand.uvPixelStride];
                        } else {
                            const int cIdx = cpy * cand.uvRowStride + cpx * 2;
                            candV = cand.uPlane[cIdx];
                            candU = cand.uPlane[cIdx + 1];
                        }
                    }
                }

                sampleRadiances[validCount] = candRadiance;
                sampleWeights[validCount] = candWeight;
                sampleU[validCount] = candU;
                sampleV[validCount] = candV;
                validCount++;
            }

            if (isGhostPixel) {
                ghostFallbackPixels++;
            }

            // 3. Outlier rejection across candidate samples
            if (validCount >= 3) {
                // Compute median of valid samples
                std::vector<float> radSorted(validCount);
                for (size_t s = 0; s < validCount; ++s) radSorted[s] = sampleRadiances[s];
                std::sort(radSorted.begin(), radSorted.end());
                const float medianRad = radSorted[validCount / 2];

                // Rejection threshold based on median
                const float threshold = std::max(12.0f, medianRad * 0.30f);
                for (size_t s = 0; s < validCount; ++s) {
                    if (std::abs(sampleRadiances[s] - medianRad) > threshold) {
                        sampleWeights[s] = 0.0f; // Reject outlier
                    }
                }
            }

            // 4. Weighted temporal fusion
            float sumW = 0.0f;
            float sumW2 = 0.0f;
            float sumRad = 0.0f;
            float sumU = 0.0f;
            float sumV = 0.0f;

            for (size_t s = 0; s < validCount; ++s) {
                const float w = sampleWeights[s];
                if (w > 0.0f) {
                    sumW += w;
                    sumW2 += w * w;
                    sumRad += w * sampleRadiances[s];
                    sumU += w * sampleU[s];
                    sumV += w * sampleV[s];
                }
            }

            if (sumW > 0.001f) {
                output.fusedY[outYOffset + x] = sumRad / sumW;
                output.fusedU[outYOffset + x] = sumU / sumW;
                output.fusedV[outYOffset + x] = sumV / sumW;

                const float neff = (sumW2 > 0.0001f) ? (sumW * sumW / sumW2) : 1.0f;
                cumulativeNeff += neff;
            } else {
                // Fallback to reference
                output.fusedY[outYOffset + x] = refRadiance;
                output.fusedU[outYOffset + x] = refU;
                output.fusedV[outYOffset + x] = refV;
                cumulativeNeff += 1.0;
            }
        }
    }

    const double avgNeff = cumulativeNeff / std::max(1, totalPixels);
    output.snrGainDb = static_cast<float>(10.0 * std::log10(std::max(1.0, avgNeff)));
    output.ghostPixelFraction = static_cast<float>(ghostFallbackPixels) / static_cast<float>(std::max(1, totalPixels));
    output.usedFrameCount = static_cast<int>(numFrames);

    return output;
}

} // namespace optilens
