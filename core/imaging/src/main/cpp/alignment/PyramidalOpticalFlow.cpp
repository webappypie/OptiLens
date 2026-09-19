#include "PyramidalOpticalFlow.hpp"
#include <cmath>
#include <algorithm>
#include <random>

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

void PyramidalOpticalFlow::buildPyramid(
    const uint8_t* src,
    int width,
    int height,
    int stride,
    std::vector<uint8_t>& l1,
    std::vector<uint8_t>& l2,
    int& w1, int& h1,
    int& w2, int& h2
) {
    w1 = width / 2;
    h1 = height / 2;
    l1.resize(w1 * h1);

    for (int y = 0; y < h1; ++y) {
        const uint8_t* r0 = src + (2 * y) * stride;
        const uint8_t* r1 = src + (2 * y + 1) * stride;
        uint8_t* outRow = l1.data() + y * w1;
        for (int x = 0; x < w1; ++x) {
            int sum = r0[2 * x] + r0[2 * x + 1] + r1[2 * x] + r1[2 * x + 1];
            outRow[x] = static_cast<uint8_t>(sum >> 2);
        }
    }

    w2 = w1 / 2;
    h2 = h1 / 2;
    l2.resize(w2 * h2);

    for (int y = 0; y < h2; ++y) {
        const uint8_t* r0 = l1.data() + (2 * y) * w1;
        const uint8_t* r1 = l1.data() + (2 * y + 1) * w1;
        uint8_t* outRow = l2.data() + y * w2;
        for (int x = 0; x < w2; ++x) {
            int sum = r0[2 * x] + r0[2 * x + 1] + r1[2 * x] + r1[2 * x + 1];
            outRow[x] = static_cast<uint8_t>(sum >> 2);
        }
    }
}

PyramidalOpticalFlow::Point2D PyramidalOpticalFlow::coarseShift(
    const uint8_t* refL2,
    const uint8_t* candL2,
    int w2,
    int h2
) {
    // 1D integral projections along X and Y
    std::vector<int64_t> projXRef(w2, 0), projXCand(w2, 0);
    std::vector<int64_t> projYRef(h2, 0), projYCand(h2, 0);

    for (int y = 0; y < h2; ++y) {
        const uint8_t* rRef = refL2 + y * w2;
        const uint8_t* rCand = candL2 + y * w2;
        for (int x = 0; x < w2; ++x) {
            projXRef[x] += rRef[x];
            projXCand[x] += rCand[x];
            projYRef[y] += rRef[x];
            projYCand[y] += rCand[x];
        }
    }

    const int searchRange = 12;
    int bestDx = 0;
    int64_t minDiffX = -1;

    for (int dx = -searchRange; dx <= searchRange; ++dx) {
        int64_t diff = 0;
        for (int x = searchRange; x < w2 - searchRange; ++x) {
            diff += std::abs(projXRef[x] - projXCand[x + dx]);
        }
        if (minDiffX == -1 || diff < minDiffX) {
            minDiffX = diff;
            bestDx = dx;
        }
    }

    int bestDy = 0;
    int64_t minDiffY = -1;

    for (int dy = -searchRange; dy <= searchRange; ++dy) {
        int64_t diff = 0;
        for (int y = searchRange; y < h2 - searchRange; ++y) {
            diff += std::abs(projYRef[y] - projYCand[y + dy]);
        }
        if (minDiffY == -1 || diff < minDiffY) {
            minDiffY = diff;
            bestDy = dy;
        }
    }

    return Point2D{ static_cast<float>(bestDx), static_cast<float>(bestDy) };
}

bool PyramidalOpticalFlow::trackPointLK(
    const uint8_t* ref,
    const uint8_t* cand,
    int w,
    int h,
    Point2D ptRef,
    Point2D& ptCand,
    int windowRadius
) {
    const int rx = static_cast<int>(ptRef.x);
    const int ry = static_cast<int>(ptRef.y);

    if (rx - windowRadius < 1 || rx + windowRadius >= w - 1 ||
        ry - windowRadius < 1 || ry + windowRadius >= h - 1) {
        return false;
    }

    // Spatial gradients Gx, Gy on reference window
    float Gxx = 0.0f, Gyy = 0.0f, Gxy = 0.0f;
    for (int wy = -windowRadius; wy <= windowRadius; ++wy) {
        const int y = ry + wy;
        const uint8_t* prev = ref + (y - 1) * w;
        const uint8_t* next = ref + (y + 1) * w;
        const uint8_t* curr = ref + y * w;

        for (int wx = -windowRadius; wx <= windowRadius; ++wx) {
            const int x = rx + wx;
            const float gx = (curr[x + 1] - curr[x - 1]) * 0.5f;
            const float gy = (next[x] - prev[x]) * 0.5f;

            Gxx += gx * gx;
            Gyy += gy * gy;
            Gxy += gx * gy;
        }
    }

    const float det = Gxx * Gyy - Gxy * Gxy;
    if (det < 1e-4f) return false; // Degenerate feature (flat region)

    const float invDet = 1.0f / det;

    // Iterative LK refinement
    float curX = ptCand.x;
    float curY = ptCand.y;

    for (int iter = 0; iter < 5; ++iter) {
        if (curX - windowRadius < 1 || curX + windowRadius >= w - 1 ||
            curY - windowRadius < 1 || curY + windowRadius >= h - 1) {
            return false;
        }

        float bx = 0.0f, by = 0.0f;
        for (int wy = -windowRadius; wy <= windowRadius; ++wy) {
            const int yRef = ry + wy;
            const float yCand = curY + wy;
            const uint8_t* prevRef = ref + (yRef - 1) * w;
            const uint8_t* nextRef = ref + (yRef + 1) * w;
            const uint8_t* currRef = ref + yRef * w;

            for (int wx = -windowRadius; wx <= windowRadius; ++wx) {
                const int xRef = rx + wx;
                const float xCand = curX + wx;

                const float gx = (currRef[xRef + 1] - currRef[xRef - 1]) * 0.5f;
                const float gy = (nextRef[xRef] - prevRef[xRef]) * 0.5f;

                const float iRef = currRef[xRef];
                const float iCand = sampleBilinear(cand, w, h, w, xCand, yCand);
                const float it = iCand - iRef;

                bx += gx * it;
                by += gy * it;
            }
        }

        const float du = (Gyy * (-bx) - Gxy * (-by)) * invDet;
        const float dv = (-Gxy * (-bx) + Gxx * (-by)) * invDet;

        curX += du;
        curY += dv;

        if (du * du + dv * dv < 0.01f) break;
    }

    ptCand.x = curX;
    ptCand.y = curY;
    return true;
}

AlignmentTransform PyramidalOpticalFlow::fitAffineRANSAC(
    const std::vector<PointCorrespondence>& correspondences,
    int width,
    int height
) {
    (void)width;
    (void)height;
    AlignmentTransform result;
    // Default identity matrix
    result.h[0] = 1.0f; result.h[1] = 0.0f; result.h[2] = 0.0f;
    result.h[3] = 0.0f; result.h[4] = 1.0f; result.h[5] = 0.0f;
    result.h[6] = 0.0f; result.h[7] = 0.0f; result.h[8] = 1.0f;
    result.confidence = 0.0f;
    result.inlierRatio = 0.0f;
    result.meanResidual = 999.0f;
    result.isRejected = true;

    if (correspondences.size() < 4) {
        return result;
    }

    const size_t N = correspondences.size();
    const int maxIterations = 50;
    const float inlierDistSqThreshold = 4.0f; // 2 pixels

    std::mt19937 rng(42);
    std::uniform_int_distribution<size_t> dist(0, N - 1);

    int bestInliersCount = 0;
    float bestH[6] = { 1.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f };

    for (int iter = 0; iter < maxIterations; ++iter) {
        // Sample 3 random distinct points
        size_t i0 = dist(rng);
        size_t i1 = dist(rng);
        size_t i2 = dist(rng);
        if (i0 == i1 || i1 == i2 || i0 == i2) continue;

        const auto& c0 = correspondences[i0];
        const auto& c1 = correspondences[i1];
        const auto& c2 = correspondences[i2];

        // Solve 2D affine transform (6 parameters):
        // x' = a00 * x + a01 * y + a02
        // y' = a10 * x + a11 * y + a12
        const float x0 = c0.pRef.x, y0 = c0.pRef.y;
        const float x1 = c1.pRef.x, y1 = c1.pRef.y;
        const float x2 = c2.pRef.x, y2 = c2.pRef.y;

        const float u0 = c0.pCand.x, v0 = c0.pCand.y;
        const float u1 = c1.pCand.x, v1 = c1.pCand.y;
        const float u2 = c2.pCand.x, v2 = c2.pCand.y;

        const float det = x0 * (y1 - y2) + x1 * (y2 - y0) + x2 * (y0 - y1);
        if (std::abs(det) < 1e-4f) continue;

        const float invDet = 1.0f / det;

        float a00 = (u0 * (y1 - y2) + u1 * (y2 - y0) + u2 * (y0 - y1)) * invDet;
        float a01 = (u0 * (x2 - x1) + u1 * (x0 - x2) + u2 * (x1 - x0)) * invDet;
        float a02 = (u0 * (x1 * y2 - x2 * y1) + u1 * (x2 * y0 - x0 * y2) + u2 * (x0 * y1 - x1 * y0)) * invDet;

        float a10 = (v0 * (y1 - y2) + v1 * (y2 - y0) + v2 * (y0 - y1)) * invDet;
        float a11 = (v0 * (x2 - x1) + v1 * (x0 - x2) + v2 * (x1 - x0)) * invDet;
        float a12 = (v0 * (x1 * y2 - x2 * y1) + v1 * (x2 * y0 - x0 * y2) + v2 * (x0 * y1 - x1 * y0)) * invDet;

        // Check inliers
        int inliers = 0;
        for (size_t i = 0; i < N; ++i) {
            const float rx = correspondences[i].pRef.x;
            const float ry = correspondences[i].pRef.y;
            const float expectedX = a00 * rx + a01 * ry + a02;
            const float expectedY = a10 * rx + a11 * ry + a12;

            const float dx = expectedX - correspondences[i].pCand.x;
            const float dy = expectedY - correspondences[i].pCand.y;
            if (dx * dx + dy * dy < inlierDistSqThreshold) {
                inliers++;
            }
        }

        if (inliers > bestInliersCount) {
            bestInliersCount = inliers;
            bestH[0] = a00; bestH[1] = a01; bestH[2] = a02;
            bestH[3] = a10; bestH[4] = a11; bestH[5] = a12;
        }
    }

    result.inlierRatio = static_cast<float>(bestInliersCount) / N;

    // Reject if too few inliers or model is degenerate
    if (result.inlierRatio < 0.35f || bestInliersCount < 4) {
        result.isRejected = true;
        return result;
    }

    // Populate 3x3 homography matrix
    result.h[0] = bestH[0]; result.h[1] = bestH[1]; result.h[2] = bestH[2];
    result.h[3] = bestH[3]; result.h[4] = bestH[4]; result.h[5] = bestH[5];
    result.h[6] = 0.0f;     result.h[7] = 0.0f;     result.h[8] = 1.0f;
    result.isRejected = false;

    return result;
}

float PyramidalOpticalFlow::computeNCC(
    const uint8_t* refY,
    const uint8_t* candY,
    int width,
    int height,
    int stride,
    const float h[9]
) {
    if (!refY || !candY || width <= 0 || height <= 0) return 0.0f;

    double sumRef = 0.0, sumWarp = 0.0;
    int count = 0;

    // Sample regularly over the central 70% of the image
    const int marginX = width * 0.15;
    const int marginY = height * 0.15;

    std::vector<float> sampleRef;
    std::vector<float> sampleWarp;
    sampleRef.reserve(2000);
    sampleWarp.reserve(2000);

    for (int y = marginY; y < height - marginY; y += 8) {
        for (int x = marginX; x < width - marginX; x += 8) {
            // Transform reference coordinate into candidate coordinate via H
            const float candX = h[0] * x + h[1] * y + h[2];
            const float candYCoord = h[3] * x + h[4] * y + h[5];

            if (candX < 0 || candX >= width - 1 || candYCoord < 0 || candYCoord >= height - 1) {
                continue;
            }

            const float rVal = refY[y * stride + x];
            const float cVal = sampleBilinear(candY, width, height, stride, candX, candYCoord);

            sampleRef.push_back(rVal);
            sampleWarp.push_back(cVal);
            sumRef += rVal;
            sumWarp += cVal;
            count++;
        }
    }

    if (count < 100) return 0.0f;

    const double meanRef = sumRef / count;
    const double meanWarp = sumWarp / count;

    double numerator = 0.0;
    double varRef = 0.0;
    double varWarp = 0.0;

    for (int i = 0; i < count; ++i) {
        const double dRef = sampleRef[i] - meanRef;
        const double dWarp = sampleWarp[i] - meanWarp;
        numerator += dRef * dWarp;
        varRef += dRef * dRef;
        varWarp += dWarp * dWarp;
    }

    const double denom = std::sqrt(varRef * varWarp);
    if (denom < 1e-5) return 0.0f;

    const float ncc = static_cast<float>(numerator / denom);
    return std::max(0.0f, std::min(1.0f, ncc));
}

AlignmentTransform PyramidalOpticalFlow::align(
    const uint8_t* refY,
    const uint8_t* candY,
    int width,
    int height,
    int stride
) {
    if (!refY || !candY || width < 32 || height < 32) {
        AlignmentTransform fail;
        fail.confidence = 0.0f;
        fail.inlierRatio = 0.0f;
        fail.meanResidual = 999.0f;
        fail.isRejected = true;
        for (int i = 0; i < 9; ++i) fail.h[i] = (i % 4 == 0) ? 1.0f : 0.0f;
        return fail;
    }

    // 1. Build 3-level image pyramids
    std::vector<uint8_t> refL1, refL2, candL1, candL2;
    int w1, h1, w2, h2;
    buildPyramid(refY, width, height, stride, refL1, refL2, w1, h1, w2, h2);
    buildPyramid(candY, width, height, stride, candL1, candL2, w1, h1, w2, h2);

    // 2. Coarse 2D shift on L2
    const Point2D coarseL2 = coarseShift(refL2.data(), candL2.data(), w2, h2);

    // 3. Grid of feature points on L0
    const int gridCols = 10;
    const int gridRows = 8;
    const float stepX = static_cast<float>(width) / (gridCols + 1);
    const float stepY = static_cast<float>(height) / (gridRows + 1);

    std::vector<PointCorrespondence> correspondences;
    correspondences.reserve(gridCols * gridRows);

    for (int r = 1; r <= gridRows; ++r) {
        for (int c = 1; c <= gridCols; ++c) {
            const float x0 = c * stepX;
            const float y0 = r * stepY;

            // Track coarse-to-fine:
            // L2: scale 1/4
            Point2D ptL2Ref{ x0 * 0.25f, y0 * 0.25f };
            Point2D ptL2Cand{ ptL2Ref.x + coarseL2.x, ptL2Ref.y + coarseL2.y };
            if (!trackPointLK(refL2.data(), candL2.data(), w2, h2, ptL2Ref, ptL2Cand, 3)) {
                continue;
            }

            // L1: scale 1/2
            Point2D ptL1Ref{ x0 * 0.5f, y0 * 0.5f };
            Point2D ptL1Cand{ ptL2Cand.x * 2.0f, ptL2Cand.y * 2.0f };
            if (!trackPointLK(refL1.data(), candL1.data(), w1, h1, ptL1Ref, ptL1Cand, 4)) {
                continue;
            }

            // L0: scale 1.0 (using subsampled work stride or full res)
            Point2D ptL0Ref{ x0, y0 };
            Point2D ptL0Cand{ ptL1Cand.x * 2.0f, ptL1Cand.y * 2.0f };
            if (trackPointLK(refY, candY, width, height, ptL0Ref, ptL0Cand, 5)) {
                correspondences.push_back({ ptL0Ref, ptL0Cand });
            }
        }
    }

    // 4. Fit Affine / Homography via RANSAC
    AlignmentTransform transform = fitAffineRANSAC(correspondences, width, height);

    if (transform.isRejected) {
        return transform;
    }

    // 5. Compute alignment confidence via Normalized Cross-Correlation
    transform.confidence = computeNCC(refY, candY, width, height, stride, transform.h);

    // Reject if confidence is poor (< 0.55)
    if (transform.confidence < 0.55f) {
        transform.isRejected = true;
    }

    return transform;
}

} // namespace optilens
