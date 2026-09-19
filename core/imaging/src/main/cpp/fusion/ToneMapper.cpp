#include "ToneMapper.hpp"
#include <algorithm>
#include <cmath>

namespace optilens {

float ToneMapper::applyShadowRecovery(float normalizedY, float liftAmount) {
    if (normalizedY <= 0.0f) return 0.0f;
    if (normalizedY >= 0.45f || liftAmount <= 0.0f) return normalizedY;

    // Smooth falloff factor from 0 to 0.45
    const float t = normalizedY / 0.45f;
    const float falloff = (1.0f - t) * (1.0f - t);
    // Rational curve preserves black point (0 remains 0) and smoothly lifts shadows
    const float lift = falloff * (normalizedY / (normalizedY + 0.12f));
    return normalizedY + (liftAmount * 0.22f * lift);
}

float ToneMapper::applyHighlightRollOff(float normalizedY, float knee) {
    if (normalizedY <= knee) return normalizedY;

    // Rational soft knee roll-off smoothly and monotonically approaching 1.0
    const float excess = normalizedY - knee;
    const float headroom = 1.0f - knee;
    if (headroom <= 0.001f) return knee;

    const float scaledExcess = excess / headroom;
    const float compression = scaledExcess / (1.0f + scaledExcess);
    return knee + compression * headroom;
}

float ToneMapper::applyFilmicCurve(float x) {
    x = std::max(0.0f, x);
    // ACES filmic approximation: f(x) = (x*(a*x+b))/(x*(c*x+d)+e)
    const float a = 2.51f;
    const float b = 0.03f;
    const float c = 2.43f;
    const float d = 0.59f;
    const float e = 0.14f;
    const float num = x * (a * x + b);
    const float den = x * (c * x + d) + e;
    const float val = num / den;
    return std::max(0.0f, std::min(1.0f, val));
}

void ToneMapper::mapLuminance(
    const float* inY,
    int width,
    int height,
    const ToneMapperParams& params,
    float* outY
) {
    if (!inY || !outY || width <= 0 || height <= 0) return;

    const int totalPixels = width * height;
    const float expComp = std::max(0.1f, params.exposureCompensation);

    for (int i = 0; i < totalPixels; ++i) {
        // Normalize to [0.0..1.0] domain
        float normY = (inY[i] / 255.0f) * expComp;

        // 1. Shadow recovery (lift dark regions)
        if (params.enableShadowRecovery) {
            normY = applyShadowRecovery(normY, params.shadowLiftAmount);
        }

        // 2. Highlight roll-off (soft-knee compression)
        if (params.enableHighlightRollOff) {
            normY = applyHighlightRollOff(normY, params.highlightKnee);
        }

        // 3. Filmic S-curve tone mapping
        const float filmic = applyFilmicCurve(normY);

        // Convert back to [0.0..255.0] range
        outY[i] = filmic * 255.0f;
    }
}

} // namespace optilens
