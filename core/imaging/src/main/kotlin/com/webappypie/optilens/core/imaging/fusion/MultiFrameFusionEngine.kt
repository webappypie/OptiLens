package com.webappypie.optilens.core.imaging.fusion

import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.AlignedStack

/**
 * Engine responsible for fusing an aligned stack of burst frames into a single
 * high-dynamic-range, low-noise photographic output.
 */
interface MultiFrameFusionEngine {

    /**
     * Fuses an [AlignedStack] into a [FusedPhoto] based on the supplied [config].
     */
    suspend fun fuse(
        stack: AlignedStack,
        config: FusionConfig = FusionConfig(),
    ): OptiResult<FusedPhoto>
}
