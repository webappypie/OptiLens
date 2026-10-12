package com.webappypie.optilens.core.imaging.ai.registry

import com.webappypie.optilens.core.imaging.ai.AiToolType
import javax.inject.Inject
import javax.inject.Singleton

/** What actually implements a tool: first-party image-processing code, or a model file. */
enum class EngineKind {
    /** Deterministic first-party algorithm compiled into the app; there is no model file. */
    EMBEDDED_ALGORITHM,

    /** A trained model file (e.g. LiteRT). Requires source, licence, checksum and benchmarks. */
    ML_MODEL,
}

/**
 * Governance record for one editing-tool engine (see docs/14_DEPENDENCY_AND_MODEL_LICENSES.md).
 *
 * Fields that only apply to model files are nullable and stay null for embedded algorithms,
 * rather than holding placeholder values.
 */
data class ModelRegistryEntry(
    val modelId: String,
    val toolType: AiToolType,
    val kind: EngineKind,
    val name: String,
    val source: String,
    val version: String,
    /** Licence of the implementation or model. */
    val license: String,
    /** SHA-256 of the model file; null when [kind] is [EngineKind.EMBEDDED_ALGORITHM]. */
    val checksumSha256: String?,
    val inputSpec: String,
    val outputSpec: String,
    /** Measured on-device latency; null until a benchmark has actually been recorded. */
    val benchmarkLatencyMs: Long?,
    val sizeBytes: Long,
    val isBundledLocally: Boolean = true,
)

/**
 * Catalog of the engines behind the editing tools. Every current tool is a first-party
 * classical image-processing algorithm bundled in the APK; no ML model files are shipped.
 */
@Singleton
class AiModelRegistry @Inject constructor() {

    private val entries = listOf(
        ModelRegistryEntry(
            modelId = "optilens_deblur_regularized_v1",
            toolType = AiToolType.DEBLUR,
            kind = EngineKind.EMBEDDED_ALGORITHM,
            name = "Directional Richardson–Lucy deconvolution with shock-filter sharpening",
            source = FIRST_PARTY_SOURCE,
            version = "1.2.0",
            license = FIRST_PARTY_LICENSE,
            checksumSha256 = null,
            inputSpec = "ARGB_8888 bitmap",
            outputSpec = "ARGB_8888 bitmap, same size",
            benchmarkLatencyMs = null,
            sizeBytes = 0L,
        ),
        ModelRegistryEntry(
            modelId = "optilens_inpainting_telea_v1",
            toolType = AiToolType.INPAINTING,
            kind = EngineKind.EMBEDDED_ALGORITHM,
            name = "Fast-marching (Telea) inpainting with seam smoothing",
            source = FIRST_PARTY_SOURCE,
            version = "1.1.0",
            license = FIRST_PARTY_LICENSE,
            checksumSha256 = null,
            inputSpec = "ARGB_8888 bitmap + binary mask",
            outputSpec = "ARGB_8888 bitmap, masked pixels filled from surroundings",
            benchmarkLatencyMs = null,
            sizeBytes = 0L,
        ),
        ModelRegistryEntry(
            modelId = "optilens_reflection_separator_v1",
            toolType = AiToolType.REFLECTION_REDUCTION,
            kind = EngineKind.EMBEDDED_ALGORITHM,
            name = "Low-frequency veil estimation and subtraction",
            source = FIRST_PARTY_SOURCE,
            version = "1.0.4",
            license = FIRST_PARTY_LICENSE,
            checksumSha256 = null,
            inputSpec = "ARGB_8888 bitmap",
            outputSpec = "ARGB_8888 bitmap, same size",
            benchmarkLatencyMs = null,
            sizeBytes = 0L,
        ),
        ModelRegistryEntry(
            modelId = "optilens_upscale_edi_v2",
            toolType = AiToolType.UPSCALE,
            kind = EngineKind.EMBEDDED_ALGORITHM,
            name = "Edge-directed interpolation upscaler (2x/4x)",
            source = FIRST_PARTY_SOURCE,
            version = "2.0.1",
            license = FIRST_PARTY_LICENSE,
            checksumSha256 = null,
            inputSpec = "ARGB_8888 bitmap",
            outputSpec = "ARGB_8888 bitmap scaled by 2 or 4 (interpolated, no detail synthesis)",
            benchmarkLatencyMs = null,
            sizeBytes = 0L,
        ),
        ModelRegistryEntry(
            modelId = "optilens_restoration_archival_v1",
            toolType = AiToolType.RESTORATION,
            kind = EngineKind.EMBEDDED_ALGORITHM,
            name = "Scratch detection/repair with colour-cast and contrast correction",
            source = FIRST_PARTY_SOURCE,
            version = "1.0.0",
            license = FIRST_PARTY_LICENSE,
            checksumSha256 = null,
            inputSpec = "ARGB_8888 bitmap",
            outputSpec = "ARGB_8888 bitmap, same size",
            benchmarkLatencyMs = null,
            sizeBytes = 0L,
        ),
    )

    fun getAllEntries(): List<ModelRegistryEntry> = entries

    fun getEntry(modelId: String): ModelRegistryEntry? =
        entries.firstOrNull { it.modelId == modelId }

    fun getEntryForTool(toolType: AiToolType): ModelRegistryEntry =
        entries.firstOrNull { it.toolType == toolType } ?: entries.first()

    companion object {
        const val FIRST_PARTY_SOURCE = "OptiLens first-party Kotlin implementation (core:imaging)"
        /** No project licence file exists yet; the owner must confirm the licence. */
        const val FIRST_PARTY_LICENSE = "OptiLens first-party code (project licence pending owner confirmation)"
    }
}
