#ifndef OPTILENS_PYRAMIDAL_OPTICAL_FLOW_HPP
#define OPTILENS_PYRAMIDAL_OPTICAL_FLOW_HPP

#include <cstdint>
#include <vector>

namespace optilens {

struct AlignmentTransform {
    float h[9];              // 3x3 homography matrix (row-major)
    float confidence;        // 0.0 to 1.0 (Normalized cross-correlation)
    float inlierRatio;       // Fraction of tracked points agreeing with model
    float meanResidual;      // Average pixel error after alignment
    bool isRejected;         // True if frame cannot be aligned reliably
};

class PyramidalOpticalFlow {
public:
    /**
     * Estimates the 3x3 homography transformation aligning candidate frame to reference frame.
     *
     * @param refY Reference luminance plane.
     * @param candY Candidate luminance plane to be aligned.
     * @param width Image width.
     * @param height Image height.
     * @param stride Bytes per row.
     * @return AlignmentTransform with homography matrix and confidence.
     */
    static AlignmentTransform align(
        const uint8_t* refY,
        const uint8_t* candY,
        int width,
        int height,
        int stride
    );

private:
    struct Point2D {
        float x;
        float y;
    };

    struct PointCorrespondence {
        Point2D pRef;
        Point2D pCand;
    };

    static void buildPyramid(
        const uint8_t* src,
        int width,
        int height,
        int stride,
        std::vector<uint8_t>& l1,
        std::vector<uint8_t>& l2,
        int& w1, int& h1,
        int& w2, int& h2
    );

    static Point2D coarseShift(
        const uint8_t* refL2,
        const uint8_t* candL2,
        int w2,
        int h2
    );

    static bool trackPointLK(
        const uint8_t* ref,
        const uint8_t* cand,
        int w,
        int h,
        Point2D ptRef,
        Point2D& ptCand,
        int windowRadius
    );

    static AlignmentTransform fitAffineRANSAC(
        const std::vector<PointCorrespondence>& correspondences,
        int width,
        int height
    );

    static float computeNCC(
        const uint8_t* refY,
        const uint8_t* candY,
        int width,
        int height,
        int stride,
        const float h[9]
    );
};

} // namespace optilens

#endif // OPTILENS_PYRAMIDAL_OPTICAL_FLOW_HPP
