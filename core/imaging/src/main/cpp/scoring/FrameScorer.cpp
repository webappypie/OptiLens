#include "FrameScorer.hpp"
#include <cmath>
#include <algorithm>

namespace optilens {

float FrameScorer::computeSharpness(
    const uint8_t* yPlane,
    int width,
    int height,
    int stride
) {
    if (!yPlane || width < 8 || height < 8) return 0.0f;

    double weightedGradientSum = 0.0;
    double totalWeight = 0.0;

    const int centerX = width / 2;
    const int centerY = height / 2;
    const double maxRadius = std::sqrt(centerX * centerX + centerY * centerY);

    // Sample pixels on a 2x2 stride for high-speed evaluation
    for (int y = 2; y < height - 2; y += 2) {
        const uint8_t* prevRow = yPlane + (y - 1) * stride;
        const uint8_t* currRow = yPlane + y * stride;
        const uint8_t* nextRow = yPlane + (y + 1) * stride;

        const double dyCenter = std::abs(y - centerY);

        for (int x = 2; x < width - 2; x += 2) {
            // Sobel horizontal gradient Gx
            const int gx = (prevRow[x + 1] + 2 * currRow[x + 1] + nextRow[x + 1]) -
                           (prevRow[x - 1] + 2 * currRow[x - 1] + nextRow[x - 1]);

            // Sobel vertical gradient Gy
            const int gy = (nextRow[x - 1] + 2 * nextRow[x] + nextRow[x + 1]) -
                           (prevRow[x - 1] + 2 * prevRow[x] + prevRow[x + 1]);

            const double gradSq = static_cast<double>(gx * gx + gy * gy);

            // Radial weighting: 1.5x center boost down to 0.7x at corners
            const double dxCenter = std::abs(x - centerX);
            const double distFromCenter = std::sqrt(dxCenter * dxCenter + dyCenter * dyCenter);
            const double weight = 1.5 - 0.8 * (distFromCenter / maxRadius);

            weightedGradientSum += gradSq * weight;
            totalWeight += weight;
        }
    }

    if (totalWeight <= 0.0) return 0.0f;

    const double meanEnergy = weightedGradientSum / totalWeight;
    // Map Tenengrad energy to a normalized 0.0 .. 100.0 score scale
    const float score = static_cast<float>(std::min(100.0, std::sqrt(meanEnergy) * 0.4));
    return std::max(0.0f, score);
}

float FrameScorer::computeExposurePenalty(
    const uint8_t* yPlane,
    int width,
    int height,
    int stride
) {
    if (!yPlane || width <= 0 || height <= 0) return 1.0f;

    int totalSamples = 0;
    int overexposedCount = 0;
    int underexposedCount = 0;

    for (int y = 0; y < height; y += 2) {
        const uint8_t* row = yPlane + y * stride;
        for (int x = 0; x < width; x += 2) {
            const uint8_t lum = row[x];
            if (lum >= 250) overexposedCount++;
            else if (lum <= 8) underexposedCount++;
            totalSamples++;
        }
    }

    if (totalSamples == 0) return 1.0f;

    const float overFrac = static_cast<float>(overexposedCount) / totalSamples;
    const float underFrac = static_cast<float>(underexposedCount) / totalSamples;

    // Highlights have severe penalty (blown details unrecoverable)
    // Shadows have moderate penalty
    const float penalty = (overFrac * 2.5f) + (underFrac * 1.0f);
    return std::min(1.0f, std::max(0.0f, penalty));
}

float FrameScorer::computeMotionDifference(
    const uint8_t* yPlane1,
    const uint8_t* yPlane2,
    int width,
    int height,
    int stride
) {
    if (!yPlane1 || !yPlane2 || width <= 0 || height <= 0) return 0.0f;

    uint64_t totalSad = 0;
    int sampleCount = 0;

    for (int y = 0; y < height; y += 2) {
        const uint8_t* row1 = yPlane1 + y * stride;
        const uint8_t* row2 = yPlane2 + y * stride;
        for (int x = 0; x < width; x += 2) {
            totalSad += std::abs(static_cast<int>(row1[x]) - static_cast<int>(row2[x]));
            sampleCount++;
        }
    }

    if (sampleCount == 0) return 0.0f;
    return static_cast<float>(totalSad) / sampleCount;
}

float FrameScorer::computeFocusConfidence(
    const uint8_t* yPlane,
    int width,
    int height,
    int stride
) {
    if (!yPlane || width < 4 || height < 4) return 0.0f;

    int strongEdges = 0;
    int totalEvaluated = 0;

    for (int y = 1; y < height - 1; y += 2) {
        const uint8_t* prevRow = yPlane + (y - 1) * stride;
        const uint8_t* nextRow = yPlane + (y + 1) * stride;
        const uint8_t* currRow = yPlane + y * stride;

        for (int x = 1; x < width - 1; x += 2) {
            const int laplacian = std::abs(
                4 * currRow[x] - prevRow[x] - nextRow[x] - currRow[x - 1] - currRow[x + 1]
            );
            if (laplacian > 30) strongEdges++;
            totalEvaluated++;
        }
    }

    if (totalEvaluated == 0) return 0.0f;
    const float edgeDensity = static_cast<float>(strongEdges) / totalEvaluated;
    // Map edge density into [0.0, 1.0]
    return std::min(1.0f, edgeDensity * 10.0f);
}

NativeFrameScore FrameScorer::scoreFrame(
    const uint8_t* yPlane,
    const uint8_t* refYPlane,
    int width,
    int height,
    int stride
) {
    const float sharpness = computeSharpness(yPlane, width, height, stride);
    const float exposurePenalty = computeExposurePenalty(yPlane, width, height, stride);
    const float motionDiff = (refYPlane != nullptr) ?
        computeMotionDifference(yPlane, refYPlane, width, height, stride) : 0.0f;
    const float focusConf = computeFocusConfidence(yPlane, width, height, stride);

    // Composite scoring formula:
    // High sharpness (+), low exposure penalty (-), low motion difference (-)
    const float motionPenalty = std::min(1.0f, motionDiff / 50.0f);
    const float baseScore = sharpness * (1.0f - exposurePenalty * 0.6f) * (1.0f - motionPenalty * 0.4f);
    const float composite = std::max(0.0f, baseScore * (0.8f + 0.2f * focusConf));

    return NativeFrameScore{
        sharpness,
        exposurePenalty,
        motionDiff,
        focusConf,
        composite
    };
}

} // namespace optilens
