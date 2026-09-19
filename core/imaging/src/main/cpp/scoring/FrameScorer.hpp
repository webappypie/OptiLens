#ifndef OPTILENS_FRAME_SCORER_HPP
#define OPTILENS_FRAME_SCORER_HPP

#include <cstdint>
#include <vector>

namespace optilens {

struct NativeFrameScore {
    float sharpnessScore;     // 0.0 to 100.0 (higher = sharper)
    float exposurePenalty;    // 0.0 to 1.0 (0.0 = perfect, 1.0 = severely clipped)
    float motionDifference;   // 0.0 to 255.0 (inter-frame SAD vs reference)
    float focusConfidence;    // 0.0 to 1.0 (high-frequency gradient concentration)
    float compositeScore;     // Combined selection score
};

class FrameScorer {
public:
    /**
     * Evaluates high-frequency Tenengrad gradient energy with central weighting.
     */
    static float computeSharpness(
        const uint8_t* yPlane,
        int width,
        int height,
        int stride
    );

    /**
     * Evaluates highlight saturation (Y >= 250) and shadow crushing (Y <= 8).
     */
    static float computeExposurePenalty(
        const uint8_t* yPlane,
        int width,
        int height,
        int stride
    );

    /**
     * Computes normalized Sum of Absolute Differences (SAD) against reference frame.
     */
    static float computeMotionDifference(
        const uint8_t* yPlane1,
        const uint8_t* yPlane2,
        int width,
        int height,
        int stride
    );

    /**
     * Evaluates focus confidence via gradient distribution.
     */
    static float computeFocusConfidence(
        const uint8_t* yPlane,
        int width,
        int height,
        int stride
    );

    /**
     * Computes complete score bundle for a single frame.
     */
    static NativeFrameScore scoreFrame(
        const uint8_t* yPlane,
        const uint8_t* refYPlane,
        int width,
        int height,
        int stride
    );
};

} // namespace optilens

#endif // OPTILENS_FRAME_SCORER_HPP
