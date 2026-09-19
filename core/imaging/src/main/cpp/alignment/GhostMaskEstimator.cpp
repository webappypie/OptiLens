#include "GhostMaskEstimator.hpp"
#include <cmath>
#include <algorithm>
#include <cstring>

namespace optilens {

namespace {

inline uint8_t sampleBilinear(const uint8_t* img, int w, int h, int stride, float x, float y) {
    if (x < 0.0f || x >= w - 1 || y < 0.0f || y >= h - 1) {
        int cx = std::max(0, std::min(w - 1, static_cast<int>(std::round(x))));
        int cy = std::max(0, std::min(h - 1, static_cast<int>(std::round(y))));
        return img[cy * stride + cx];
    }

    int x0 = static_cast<int>(x);
    int y0 = static_cast<int>(y);
    int x1 = x0 + 1;
    int y1 = y0 + 1;

    float fx = x - x0;
    float fy = y - y0;

    const uint8_t* r0 = img + y0 * stride;
    const uint8_t* r1 = img + y1 * stride;

    float p00 = r0[x0];
    float p10 = r0[x1];
    float p01 = r1[x0];
    float p11 = r1[x1];

    float top = p00 + fx * (p10 - p00);
    float bot = p01 + fx * (p11 - p01);
    float val = top + fy * (bot - top);

    return static_cast<uint8_t>(std::max(0.0f, std::min(255.0f, val)));
}

} // namespace

void GhostMaskEstimator::morphologicalOpeningAndDilation(
    const uint8_t* src,
    uint8_t* dst,
    int width,
    int height
) {
    std::vector<uint8_t> eroded(width * height, 0);

    // 1. Erosion (3x3 minimum) to remove speckle noise
    for (int y = 1; y < height - 1; ++y) {
        const uint8_t* prev = src + (y - 1) * width;
        const uint8_t* curr = src + y * width;
        const uint8_t* next = src + (y + 1) * width;
        uint8_t* out = eroded.data() + y * width;

        for (int x = 1; x < width - 1; ++x) {
            uint8_t minVal = curr[x];
            minVal = std::min(minVal, prev[x - 1]);
            minVal = std::min(minVal, prev[x]);
            minVal = std::min(minVal, prev[x + 1]);
            minVal = std::min(minVal, curr[x - 1]);
            minVal = std::min(minVal, curr[x + 1]);
            minVal = std::min(minVal, next[x - 1]);
            minVal = std::min(minVal, next[x]);
            minVal = std::min(minVal, next[x + 1]);
            out[x] = minVal;
        }
    }

    // 2. Dilation (3x3 maximum) twice to expand motion boundaries
    std::vector<uint8_t> dilated1(width * height, 0);
    for (int y = 1; y < height - 1; ++y) {
        const uint8_t* prev = eroded.data() + (y - 1) * width;
        const uint8_t* curr = eroded.data() + y * width;
        const uint8_t* next = eroded.data() + (y + 1) * width;
        uint8_t* out = dilated1.data() + y * width;

        for (int x = 1; x < width - 1; ++x) {
            uint8_t maxVal = curr[x];
            maxVal = std::max(maxVal, prev[x - 1]);
            maxVal = std::max(maxVal, prev[x]);
            maxVal = std::max(maxVal, prev[x + 1]);
            maxVal = std::max(maxVal, curr[x - 1]);
            maxVal = std::max(maxVal, curr[x + 1]);
            maxVal = std::max(maxVal, next[x - 1]);
            maxVal = std::max(maxVal, next[x]);
            maxVal = std::max(maxVal, next[x + 1]);
            out[x] = maxVal;
        }
    }

    for (int y = 1; y < height - 1; ++y) {
        const uint8_t* prev = dilated1.data() + (y - 1) * width;
        const uint8_t* curr = dilated1.data() + y * width;
        const uint8_t* next = dilated1.data() + (y + 1) * width;
        uint8_t* out = dst + y * width;

        for (int x = 1; x < width - 1; ++x) {
            uint8_t maxVal = curr[x];
            maxVal = std::max(maxVal, prev[x - 1]);
            maxVal = std::max(maxVal, prev[x]);
            maxVal = std::max(maxVal, prev[x + 1]);
            maxVal = std::max(maxVal, curr[x - 1]);
            maxVal = std::max(maxVal, curr[x + 1]);
            maxVal = std::max(maxVal, next[x - 1]);
            maxVal = std::max(maxVal, next[x]);
            maxVal = std::max(maxVal, next[x + 1]);
            out[x] = maxVal;
        }
    }
}

GhostMaskResult GhostMaskEstimator::computeGhostMask(
    const uint8_t* refY,
    const uint8_t* candY,
    const float homography[9],
    int width,
    int height,
    int stride,
    uint8_t* outMask,
    uint8_t residualThreshold
) {
    if (!refY || !candY || !outMask || width <= 0 || height <= 0) {
        return GhostMaskResult{ 0.0f, false };
    }

    std::vector<uint8_t> rawMask(width * height, 0);
    const int totalPixels = width * height;

    const float h0 = homography[0], h1 = homography[1], h2 = homography[2];
    const float h3 = homography[3], h4 = homography[4], h5 = homography[5];

    for (int y = 0; y < height; ++y) {
        const uint8_t* rRow = refY + y * stride;
        uint8_t* mRow = rawMask.data() + y * width;

        for (int x = 0; x < width; ++x) {
            // Backward-warp reference coordinate into candidate coordinate
            const float cx = h0 * x + h1 * y + h2;
            const float cy = h3 * x + h4 * y + h5;

            // If outside boundaries, mark as non-aligned boundary (moving/ghost)
            if (cx < 0 || cx >= width - 1 || cy < 0 || cy >= height - 1) {
                mRow[x] = 255;
                continue;
            }

            const uint8_t refVal = rRow[x];
            const uint8_t candVal = sampleBilinear(candY, width, height, stride, cx, cy);

            const int diff = std::abs(static_cast<int>(candVal) - static_cast<int>(refVal));
            if (diff > residualThreshold) {
                mRow[x] = 255;
            } else {
                mRow[x] = 0;
            }
        }
    }

    // Apply morphological opening & dilation to clean up mask and write into outMask
    morphologicalOpeningAndDilation(rawMask.data(), outMask, width, height);

    // Count final moving pixels in smoothed mask
    int finalMovingCount = 0;
    for (int i = 0; i < totalPixels; ++i) {
        if (outMask[i] > 0) finalMovingCount++;
    }

    const float coverage = static_cast<float>(finalMovingCount) / totalPixels;
    const bool isSignificant = coverage > 0.04f; // More than 4% moving area

    return GhostMaskResult{ coverage, isSignificant };
}

} // namespace optilens
