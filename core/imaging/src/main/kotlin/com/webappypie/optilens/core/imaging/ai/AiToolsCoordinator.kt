package com.webappypie.optilens.core.imaging.ai

import android.graphics.Bitmap
import com.webappypie.optilens.core.imaging.ai.blur.BlurClassifier
import com.webappypie.optilens.core.imaging.ai.blur.DeblurEngine
import com.webappypie.optilens.core.imaging.ai.inpainting.InpaintingEngine
import com.webappypie.optilens.core.imaging.ai.reflection.ReflectionReductionEngine
import com.webappypie.optilens.core.imaging.ai.restoration.PhotoRestorationEngine
import com.webappypie.optilens.core.imaging.ai.tiling.TileProcessingCoordinator
import com.webappypie.optilens.core.imaging.ai.upscale.AiUpscaleEngine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/**
 * Top-level coordinator for Advanced AI computational transformations.
 *
 * Enforces:
 * - Tile-based memory safety.
 * - Genuine stage emission without artificial progress pauses.
 * - Non-destructive original preservation.
 * - Mandatory ethical disclosure labeling and transparency metadata tags.
 */
@Singleton
class AiToolsCoordinator @Inject constructor(
    private val blurClassifier: BlurClassifier,
    private val deblurEngine: DeblurEngine,
    private val inpaintingEngine: InpaintingEngine,
    private val reflectionEngine: ReflectionReductionEngine,
    private val upscaleEngine: AiUpscaleEngine,
    private val restorationEngine: PhotoRestorationEngine,
    private val tileCoordinator: TileProcessingCoordinator,
) {

    fun classifyBlur(bitmap: Bitmap): BlurClassificationResult =
        blurClassifier.classifyBlur(bitmap)

    fun deblur(
        bitmap: Bitmap,
        config: DeblurConfig = DeblurConfig(),
        presetClassification: BlurClassificationResult? = null,
        onStage: ((AiExecutionStage) -> Unit)? = null,
    ): AiToolExecutionResult {
        var resultBitmap: Bitmap = bitmap
        val timeMs = measureTimeMillis {
            onStage?.invoke(AiExecutionStage.ANALYZING_IMAGE)
            val classification = presetClassification ?: blurClassifier.classifyBlur(bitmap)

            onStage?.invoke(AiExecutionStage.EXECUTING_AI_INFERENCE)
            resultBitmap = deblurEngine.deblur(bitmap, config, classification)

            onStage?.invoke(AiExecutionStage.FINALIZING_BLENDING)
        }

        return AiToolExecutionResult(
            processedBitmap = resultBitmap,
            toolType = AiToolType.DEBLUR,
            executionTimeMs = timeMs,
            disclosureApplied = false,
            disclosureText = null,
            metadataTags = mapOf(
                "X-OptiLens-AI" to "Deblur",
                "X-OptiLens-AI-Deblur-Strength" to config.strength.toString(),
            ),
        )
    }

    fun inpaint(
        bitmap: Bitmap,
        maskBitmap: Bitmap,
        config: InpaintingConfig = InpaintingConfig(),
        onStage: ((AiExecutionStage) -> Unit)? = null,
    ): AiToolExecutionResult {
        var resultBitmap: Bitmap = bitmap
        val timeMs = measureTimeMillis {
            onStage?.invoke(AiExecutionStage.ANALYZING_IMAGE)
            onStage?.invoke(AiExecutionStage.EXECUTING_AI_INFERENCE)
            resultBitmap = inpaintingEngine.inpaint(bitmap, maskBitmap, config)
            onStage?.invoke(AiExecutionStage.FINALIZING_BLENDING)
        }

        return AiToolExecutionResult(
            processedBitmap = resultBitmap,
            toolType = AiToolType.INPAINTING,
            executionTimeMs = timeMs,
            disclosureApplied = true,
            disclosureText = AiToolType.INPAINTING.disclosureText,
            metadataTags = mapOf(
                "X-OptiLens-AI" to "Inpainting-Synthesized",
                "UserComment" to "AI Inpainting: Selected pixels synthesized from surrounding texture. Not guaranteed historical truth.",
            ),
        )
    }

    fun reduceReflections(
        bitmap: Bitmap,
        config: ReflectionConfig = ReflectionConfig(),
        onStage: ((AiExecutionStage) -> Unit)? = null,
    ): AiToolExecutionResult {
        var resultBitmap: Bitmap = bitmap
        val timeMs = measureTimeMillis {
            onStage?.invoke(AiExecutionStage.ANALYZING_IMAGE)
            onStage?.invoke(AiExecutionStage.EXECUTING_AI_INFERENCE)
            resultBitmap = reflectionEngine.reduceReflections(bitmap, config)
            onStage?.invoke(AiExecutionStage.FINALIZING_BLENDING)
        }

        return AiToolExecutionResult(
            processedBitmap = resultBitmap,
            toolType = AiToolType.REFLECTION_REDUCTION,
            executionTimeMs = timeMs,
            disclosureApplied = false,
            disclosureText = null,
            metadataTags = mapOf(
                "X-OptiLens-AI" to "Reflection-Suppressed",
                "X-OptiLens-AI-Reflection-Strength" to config.strength.toString(),
            ),
        )
    }

    fun upscale(
        bitmap: Bitmap,
        config: UpscaleConfig = UpscaleConfig(),
        onStage: ((AiExecutionStage) -> Unit)? = null,
    ): AiToolExecutionResult {
        var resultBitmap: Bitmap = bitmap
        val timeMs = measureTimeMillis {
            onStage?.invoke(AiExecutionStage.PREPARING_TILES)
            onStage?.invoke(AiExecutionStage.EXECUTING_AI_INFERENCE)
            resultBitmap = tileCoordinator.processTiled(
                srcBitmap = bitmap,
                scaleFactor = config.scaleFactor,
            ) { tile ->
                upscaleEngine.upscale(tile, config)
            }
            onStage?.invoke(AiExecutionStage.FINALIZING_BLENDING)
        }

        return AiToolExecutionResult(
            processedBitmap = resultBitmap,
            toolType = AiToolType.UPSCALE,
            executionTimeMs = timeMs,
            disclosureApplied = false,
            disclosureText = null,
            metadataTags = mapOf(
                "X-OptiLens-AI" to "SuperResolution-${config.scaleFactor}x",
            ),
        )
    }

    fun restore(
        bitmap: Bitmap,
        config: RestorationConfig = RestorationConfig(),
        onStage: ((AiExecutionStage) -> Unit)? = null,
    ): AiToolExecutionResult {
        var resultBitmap: Bitmap = bitmap
        val timeMs = measureTimeMillis {
            onStage?.invoke(AiExecutionStage.ANALYZING_IMAGE)
            onStage?.invoke(AiExecutionStage.EXECUTING_AI_INFERENCE)
            resultBitmap = restorationEngine.restore(bitmap, config)
            onStage?.invoke(AiExecutionStage.FINALIZING_BLENDING)
        }

        return AiToolExecutionResult(
            processedBitmap = resultBitmap,
            toolType = AiToolType.RESTORATION,
            executionTimeMs = timeMs,
            disclosureApplied = true,
            disclosureText = AiToolType.RESTORATION.disclosureText,
            metadataTags = mapOf(
                "X-OptiLens-AI" to "Photo-Restoration",
                "UserComment" to PhotoRestorationEngine.RESTORATION_DISCLOSURE_LABEL,
            ),
        )
    }
}
