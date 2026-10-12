package com.webappypie.optilens.core.ui.aitools

import androidx.annotation.StringRes
import com.webappypie.optilens.core.imaging.ai.AiExecutionStage
import com.webappypie.optilens.core.imaging.ai.AiToolType
import com.webappypie.optilens.core.imaging.ai.BlurType
import com.webappypie.optilens.core.ui.R

/** Localized names for imaging-layer enums (their `displayName` stays as English identifiers). */
@get:StringRes
internal val AiToolType.labelRes: Int
    get() = when (this) {
        AiToolType.DEBLUR -> R.string.tool_deblur
        AiToolType.INPAINTING -> R.string.tool_cleanup
        AiToolType.REFLECTION_REDUCTION -> R.string.tool_reflection
        AiToolType.UPSCALE -> R.string.tool_upscale
        AiToolType.RESTORATION -> R.string.tool_restore
    }

/** Localized fidelity disclosure for tools that reconstruct content, or null. */
@get:StringRes
internal val AiToolType.disclosureRes: Int?
    get() = when (this) {
        AiToolType.INPAINTING -> R.string.disclosure_cleanup
        AiToolType.RESTORATION -> R.string.disclosure_restore
        else -> null
    }

@get:StringRes
internal val BlurType.labelRes: Int
    get() = when (this) {
        BlurType.SHARP -> R.string.blur_sharp
        BlurType.MOTION_BLUR -> R.string.blur_motion
        BlurType.DEFOCUS_BLUR -> R.string.blur_defocus
        BlurType.SEVERE_UNRECOVERABLE -> R.string.blur_severe
    }

@get:StringRes
internal val AiExecutionStage.labelRes: Int
    get() = when (this) {
        AiExecutionStage.PREPARING_TILES -> R.string.stage_preparing
        AiExecutionStage.ANALYZING_IMAGE -> R.string.stage_analyzing
        AiExecutionStage.EXECUTING_AI_INFERENCE -> R.string.stage_applying
        AiExecutionStage.RECONSTRUCTING_HIGH_RES -> R.string.stage_reconstructing
        AiExecutionStage.FINALIZING_BLENDING -> R.string.stage_finishing
    }
