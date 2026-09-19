#include "PortraitProcessor.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>
#include <vector>

namespace optilens {

static inline uint8_t clampToUint8(float v) {
    if (v < 0.0f) return 0;
    if (v > 255.0f) return 255;
    return static_cast<uint8_t>(v + 0.5f);
}

float PortraitProcessor::computeSkinProbability(float u, float v) {
    // Continuous elliptical Mahalanobis model in U-V chroma plane
    // Calibrated across Fitzpatrick Phototypes I to VI
    const float u0 = 112.0f;
    const float v0 = 152.0f;
    const float du = u - u0;
    const float dv = v - v0;

    // Rotation angle theta = -35 degrees
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

void PortraitProcessor::generateDepthMap(
    const std::vector<FaceBox>& faces,
    int width,
    int height,
    std::vector<float>& outDepthMap
) {
    outDepthMap.assign(width * height, 1.0f); // Default: background at Z=1.0

    if (faces.empty()) return;

    const float featherZone = 0.05f; // Normalized feathering transition width

    for (int y = 0; y < height; ++y) {
        const float yn = static_cast<float>(y) / height;
        const int rowOffset = y * width;

        for (int x = 0; x < width; ++x) {
            const float xn = static_cast<float>(x) / width;
            float minZ = 1.0f;

            for (const auto& face : faces) {
                // Expand face box downwards for neck/torso and laterally for hair
                const float fw = face.right - face.left;
                const float fh = face.bottom - face.top;
                const float left = std::max(0.0f, face.left - fw * 0.15f);
                const float right = std::min(1.0f, face.right + fw * 0.15f);
                const float top = std::max(0.0f, face.top - fh * 0.12f);
                const float bottom = std::min(1.0f, face.bottom + fh * 0.50f);

                // Distance from expanded subject box
                float dx = 0.0f;
                if (xn < left) dx = left - xn;
                else if (xn > right) dx = xn - right;

                float dy = 0.0f;
                if (yn < top) dy = top - yn;
                else if (yn > bottom) dy = yn - bottom;

                const float dist = std::sqrt(dx * dx + dy * dy);
                if (dist <= 0.0f) {
                    minZ = 0.0f; // Inside subject -> in focus
                    break;
                } else if (dist < featherZone) {
                    const float t = dist / featherZone;
                    // Smoothstep feathering
                    const float z = t * t * (3.0f - 2.0f * t);
                    if (z < minZ) minZ = z;
                }
            }

            outDepthMap[rowOffset + x] = minZ;
        }
    }
}

void PortraitProcessor::applyFaceExposureBalancing(
    uint8_t* yPlane,
    int width,
    int height,
    int yStride,
    const std::vector<FaceBox>& faces,
    float evCompensation
) {
    if (faces.empty() || evCompensation <= 0.05f) return;

    // Exposure gain: 2^ev (capped to +1.5 EV / 2.82x gain)
    const float clampedEv = std::min(1.5f, std::max(0.0f, evCompensation));
    const float gain = std::pow(2.0f, clampedEv);
    const float gainExcess = gain - 1.0f;

    for (const auto& face : faces) {
        const float cx = (face.left + face.right) * 0.5f * width;
        const float cy = (face.top + face.bottom) * 0.5f * height;
        const float rx = std::max(2.0f, (face.right - face.left) * 0.70f * width);
        const float ry = std::max(2.0f, (face.bottom - face.top) * 0.75f * height);

        const int minX = std::max(0, static_cast<int>(cx - rx * 2.0f));
        const int maxX = std::min(width - 1, static_cast<int>(cx + rx * 2.0f));
        const int minY = std::max(0, static_cast<int>(cy - ry * 2.0f));
        const int maxY = std::min(height - 1, static_cast<int>(cy + ry * 2.0f));

        for (int y = minY; y <= maxY; ++y) {
            const float dy = (y - cy) / ry;
            const float dy2 = dy * dy;
            if (dy2 >= 4.0f) continue;

            uint8_t* row = yPlane + y * yStride;
            for (int x = minX; x <= maxX; ++x) {
                const float dx = (x - cx) / rx;
                const float r2 = dx * dx + dy2;
                if (r2 >= 4.0f) continue;

                // Smooth Gaussian falloff from face center
                const float weight = std::exp(-0.5f * r2);
                const float curY = row[x];

                // Highlight knee rolloff: lift shadows/midtones, preserve highlights near 255
                const float headroom = (255.0f - curY) / 255.0f;
                const float lifted = curY + weight * gainExcess * curY * headroom;
                row[x] = clampToUint8(lifted);
            }
        }
    }
}

void PortraitProcessor::applySkinSmoothingAndDetailProtection(
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
) {
    if (faces.empty() || smoothingStrength <= 0.01f) return;

    // 1. Build detail protection and eye sparkle maps
    // Detail protection: 0.0 = full smoothing allowed, 1.0 = zero smoothing
    std::vector<float> protectionMap(width * height, 0.0f);
    std::vector<bool> eyeSparkleMap(width * height, false);

    for (const auto& lm : landmarks) {
        const float lmx = lm.x * width;
        const float lmy = lm.y * height;
        // Radius of protection in pixels
        const float radiusPx = std::max(3.0f, lm.radius * width);
        const float r2Max = radiusPx * radiusPx;

        const int minX = std::max(0, static_cast<int>(lmx - radiusPx));
        const int maxX = std::min(width - 1, static_cast<int>(lmx + radiusPx));
        const int minY = std::max(0, static_cast<int>(lmy - radiusPx));
        const int maxY = std::min(height - 1, static_cast<int>(lmy + radiusPx));

        const bool isEye = (lm.type == 0 || lm.type == 1);

        for (int y = minY; y <= maxY; ++y) {
            const float dy = y - lmy;
            const int offset = y * width;
            for (int x = minX; x <= maxX; ++x) {
                const float dx = x - lmx;
                const float d2 = dx * dx + dy * dy;
                if (d2 < r2Max) {
                    const float factor = 1.0f - (d2 / r2Max);
                    if (factor > protectionMap[offset + x]) {
                        protectionMap[offset + x] = factor;
                    }
                    if (isEye && eyeSparkle && d2 < (r2Max * 0.36f)) {
                        eyeSparkleMap[offset + x] = true;
                    }
                }
            }
        }
    }

    // 2. Selective bilateral skin cleanup
    // We only process bounding boxes of faces
    const float sigmaSpatial = 3.0f;
    const float sigmaRange = 40.0f;
    const float twoSigmaS2 = 2.0f * sigmaSpatial * sigmaSpatial;
    const float twoSigmaR2 = 2.0f * sigmaRange * sigmaRange;

    for (const auto& face : faces) {
        const int minX = std::max(2, static_cast<int>(face.left * width));
        const int maxX = std::min(width - 3, static_cast<int>(face.right * width));
        const int minY = std::max(2, static_cast<int>(face.top * height));
        const int maxY = std::min(height - 3, static_cast<int>(face.bottom * height));

        for (int y = minY; y <= maxY; ++y) {
            const int uvY = y / 2;
            const int mapRow = y * width;
            uint8_t* yRow = yPlane + y * yStride;
            const uint8_t* uRow = uPlane + uvY * uvStride;
            const uint8_t* vRow = vPlane + uvY * uvStride;

            for (int x = minX; x <= maxX; ++x) {
                // Eye sparkle micro-contrast (+15% iris sharpness)
                if (eyeSparkleMap[mapRow + x]) {
                    const float center = yRow[x];
                    const float avgSurround = (
                        yPlane[(y - 1) * yStride + x] +
                        yPlane[(y + 1) * yStride + x] +
                        yRow[x - 1] +
                        yRow[x + 1]
                    ) * 0.25f;
                    const float sparkle = center + 0.15f * (center - avgSurround);
                    yRow[x] = clampToUint8(sparkle);
                    continue;
                }

                const float protection = protectionMap[mapRow + x];
                if (protection >= 0.85f) {
                    continue; // Protected landmark (eye/brow/lip detail)
                }

                const int uvX = x / 2;
                const float uVal = uRow[uvX];
                const float vVal = vRow[uvX];
                const float pSkin = computeSkinProbability(uVal, vVal);
                if (pSkin < 0.20f) {
                    continue; // Non-skin pixel
                }

                const float effectiveSmoothing = std::min(1.0f, smoothingStrength * 1.5f) * pSkin * (1.0f - protection);
                if (effectiveSmoothing <= 0.05f) {
                    continue;
                }

                // 5x5 Bilateral Filter
                const float centerVal = yRow[x];
                float sumWeights = 0.0f;
                float sumValues = 0.0f;

                for (int dy = -2; dy <= 2; ++dy) {
                    const uint8_t* neighborRow = yPlane + (y + dy) * yStride;
                    for (int dx = -2; dx <= 2; ++dx) {
                        const float neighborVal = neighborRow[x + dx];
                        const float spatialDist2 = static_cast<float>(dx * dx + dy * dy);
                        const float diffVal = centerVal - neighborVal;
                        const float rangeDist2 = diffVal * diffVal;

                        const float w = std::exp(-spatialDist2 / twoSigmaS2 - rangeDist2 / twoSigmaR2);
                        sumWeights += w;
                        sumValues += w * neighborVal;
                    }
                }

                if (sumWeights > 0.001f) {
                    const float smoothedVal = sumValues / sumWeights;
                    const float finalVal = (1.0f - effectiveSmoothing) * centerVal + effectiveSmoothing * smoothedVal;
                    yRow[x] = clampToUint8(finalVal);
                }
            }
        }
    }
}

void PortraitProcessor::applyOpticalDiscBokeh(
    uint8_t* yPlane,
    uint8_t* uPlane,
    uint8_t* vPlane,
    const float* depthMap,
    int width,
    int height,
    int yStride,
    int uvStride,
    float maxBlurRadius
) {
    if (maxBlurRadius < 1.0f) return;

    std::vector<uint8_t> yBuffer(width * height);
    for (int y = 0; y < height; ++y) {
        std::memcpy(yBuffer.data() + y * width, yPlane + y * yStride, width);
    }

    const int uvWidth = width / 2;
    const int uvHeight = height / 2;
    std::vector<uint8_t> uBuffer(uvWidth * uvHeight);
    std::vector<uint8_t> vBuffer(uvWidth * uvHeight);
    for (int y = 0; y < uvHeight; ++y) {
        std::memcpy(uBuffer.data() + y * uvWidth, uPlane + y * uvStride, uvWidth);
        std::memcpy(vBuffer.data() + y * uvWidth, vPlane + y * uvStride, uvWidth);
    }

    // 1. Process Y (Luminance) with Optical Disc Kernel & Specular Blooming
    for (int y = 0; y < height; ++y) {
        const int mapRow = y * width;
        uint8_t* outRow = yPlane + y * yStride;

        for (int x = 0; x < width; ++x) {
            const float z = depthMap[mapRow + x];
            if (z <= 0.02f) {
                continue; // Foreground face subject -> 100% sharp
            }

            const int radius = static_cast<int>(std::round(maxBlurRadius * z));
            if (radius <= 0) continue;

            const float r2Max = static_cast<float>(radius * radius);
            float sumWeights = 0.0f;
            float sumY = 0.0f;

            for (int dy = -radius; dy <= radius; ++dy) {
                const int ny = std::min(height - 1, std::max(0, y + dy));
                const int nRow = ny * width;

                for (int dx = -radius; dx <= radius; ++dx) {
                    const float d2 = static_cast<float>(dx * dx + dy * dy);
                    if (d2 <= r2Max) { // Circular aperture disc
                        const int nx = std::min(width - 1, std::max(0, x + dx));
                        const float sampleY = yBuffer[nRow + nx];

                        // Specular highlight blooming into bokeh orbs
                        float bloomWeight = 1.0f;
                        if (sampleY > 185.0f && z > 0.40f) {
                            const float excess = (sampleY - 185.0f) / 70.0f;
                            bloomWeight = 1.0f + 2.5f * excess * excess;
                        }

                        sumWeights += bloomWeight;
                        sumY += bloomWeight * sampleY;
                    }
                }
            }

            if (sumWeights > 0.001f) {
                const float blurredY = sumY / sumWeights;
                const float originalY = yBuffer[mapRow + x];
                outRow[x] = clampToUint8((1.0f - z) * originalY + z * blurredY);
            }
        }
    }

    // 2. Process U & V at half resolution for chrominance depth-of-field
    for (int y = 0; y < uvHeight; ++y) {
        const int mapY = y * 2;
        const int mapRow = mapY * width;
        uint8_t* outURow = uPlane + y * uvStride;
        uint8_t* outVRow = vPlane + y * uvStride;

        for (int x = 0; x < uvWidth; ++x) {
            const int mapX = x * 2;
            const float z = depthMap[mapRow + mapX];
            if (z <= 0.02f) continue;

            const int radius = std::max(1, static_cast<int>(std::round((maxBlurRadius * 0.5f) * z)));
            const float r2Max = static_cast<float>(radius * radius);

            float sumWeights = 0.0f;
            float sumU = 0.0f;
            float sumV = 0.0f;

            for (int dy = -radius; dy <= radius; ++dy) {
                const int ny = std::min(uvHeight - 1, std::max(0, y + dy));
                const int nRow = ny * uvWidth;

                for (int dx = -radius; dx <= radius; ++dx) {
                    const float d2 = static_cast<float>(dx * dx + dy * dy);
                    if (d2 <= r2Max) {
                        const int nx = std::min(uvWidth - 1, std::max(0, x + dx));
                        sumWeights += 1.0f;
                        sumU += uBuffer[nRow + nx];
                        sumV += vBuffer[nRow + nx];
                    }
                }
            }

            if (sumWeights > 0.001f) {
                const float blurredU = sumU / sumWeights;
                const float blurredV = sumV / sumWeights;
                const float origU = uBuffer[y * uvWidth + x];
                const float origV = vBuffer[y * uvWidth + x];
                outURow[x] = clampToUint8((1.0f - z) * origU + z * blurredU);
                outVRow[x] = clampToUint8((1.0f - z) * origV + z * blurredV);
            }
        }
    }
}

bool PortraitProcessor::processPortrait(
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
) {
    if (!yPlane || !uPlane || !vPlane || width <= 0 || height <= 0) {
        return false;
    }

    // Step 1: Face Exposure Balancing (Backlit recovery with Gaussian falloff)
    if (params.isBacklit || params.faceEvCompensation > 0.05f) {
        applyFaceExposureBalancing(yPlane, width, height, yStride, faces, params.faceEvCompensation);
    }

    // Step 2: Skin smoothing & landmark detail protection
    if (params.skinSmoothingStrength > 0.01f) {
        applySkinSmoothingAndDetailProtection(
            yPlane,
            uPlane,
            vPlane,
            width,
            height,
            yStride,
            uvStride,
            faces,
            landmarks,
            params.skinSmoothingStrength,
            params.enableEyeSparkle
        );
    }

    // Step 3: Depth map generation and Optical Disc Bokeh convolution
    if (params.apertureFNumber < 8.0f) {
        // Map aperture f-number to circle of confusion radius in pixels
        // f/1.4 -> 12px, f/2.0 -> 8px, f/2.8 -> 6px, f/4.0 -> 4px, f/5.6 -> 2px
        const float maxBlurRadius = std::min(15.0f, std::max(1.0f, 12.0f * (1.4f / params.apertureFNumber)));

        std::vector<float> depthMap;
        generateDepthMap(faces, width, height, depthMap);

        applyOpticalDiscBokeh(
            yPlane,
            uPlane,
            vPlane,
            depthMap.data(),
            width,
            height,
            yStride,
            uvStride,
            maxBlurRadius
        );
    }

    return true;
}

} // namespace optilens
