#ifndef OPTILENS_GHOST_MASK_ESTIMATOR_HPP
#define OPTILENS_GHOST_MASK_ESTIMATOR_HPP

#include <cstdint>
#include <vector>

namespace optilens {

struct GhostMaskResult {
    float motionCoverageFraction; // 0.0 to 1.0 (fraction of frame marked as moving)
    bool hasSignificantMotion;    // True if coverage exceeds threshold (e.g. > 5%)
};

class GhostMaskEstimator {
public:
    /**
     * Evaluates photometric residuals between aligned candidate frame and reference frame,
     * applying adaptive noise thresholding and morphological filtering to segment moving objects.
     *
     * @param refY Reference luminance buffer.
     * @param candY Candidate luminance buffer.
     * @param homography 3x3 transform aligning candidate to reference (row-major).
     * @param width Image width.
     * @param height Image height.
     * @param stride Row stride in bytes.
     * @param outMask Output binary mask buffer (0 = static, 255 = moving subject/ghost).
     * @param residualThreshold Photometric error threshold (default 20).
     */
    static GhostMaskResult computeGhostMask(
        const uint8_t* refY,
        const uint8_t* candY,
        const float homography[9],
        int width,
        int height,
        int stride,
        uint8_t* outMask,
        uint8_t residualThreshold = 20
    );

private:
    static void morphologicalOpeningAndDilation(
        const uint8_t* src,
        uint8_t* dst,
        int width,
        int height
    );
};

} // namespace optilens

#endif // OPTILENS_GHOST_MASK_ESTIMATOR_HPP
