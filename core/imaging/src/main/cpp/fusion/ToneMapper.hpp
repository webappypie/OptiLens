#ifndef OPTILENS_TONE_MAPPER_HPP
#define OPTILENS_TONE_MAPPER_HPP

#include <cstdint>
#include <vector>

namespace optilens {

struct ToneMapperParams {
    bool enableHighlightRollOff = true;
    bool enableShadowRecovery = true;
    float shadowLiftAmount = 0.35f;   // Strength of shadow detail lift (0.0 to 1.0)
    float highlightKnee = 0.72f;      // Transition threshold for highlight roll-off
    float exposureCompensation = 1.0f; // Global exposure multiplier
    bool enableNightHighlightProtection = true; // Protect neon signs / point lights from harsh clipping
};

class ToneMapper {
public:
    /**
     * Applies dynamic range compression, shadow detail recovery, highlight roll-off,
     * and filmic S-curve tone mapping to floating-point fused luminance.
     *
     * @param inY Fused floating point luminance [0..255+]
     * @param width Frame width
     * @param height Frame height
     * @param params Tone mapping parameters
     * @param outY Destination buffer for tone-mapped luminance [0..255]
     */
    static void mapLuminance(
        const float* inY,
        int width,
        int height,
        const ToneMapperParams& params,
        float* outY
    );

    /**
     * Highlight roll-off: smoothly compresses values exceeding knee threshold into [knee, 1.0]
     * avoiding harsh specular clipping on neon signs, sunsets, and specular reflections.
     */
    static float applyHighlightRollOff(float normalizedY, float knee = 0.72f);

    /**
     * Shadow recovery: lifts underexposed shadows while preserving true black.
     */
    static float applyShadowRecovery(float normalizedY, float liftAmount = 0.35f);

    /**
     * Filmic ACES S-curve tone mapping operator.
     */
    static float applyFilmicCurve(float x);
};

} // namespace optilens

#endif // OPTILENS_TONE_MAPPER_HPP
