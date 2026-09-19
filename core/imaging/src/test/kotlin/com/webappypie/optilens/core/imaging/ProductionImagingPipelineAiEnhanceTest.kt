package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.imaging.enhance.AiEnhanceConfig
import com.webappypie.optilens.core.imaging.enhance.NativeAiEnhanceEngine
import com.webappypie.optilens.core.imaging.fusion.NativeMultiFrameFusionEngine
import com.webappypie.optilens.core.imaging.portrait.NativePortraitEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionImagingPipelineAiEnhanceTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private val alignmentEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())
    private val fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger())
    private val portraitEngine = NativePortraitEngine()
    private val aiEnhanceEngine = NativeAiEnhanceEngine(useNativeIfAvailable = false)
    private val pipeline = ProductionImagingPipeline(
        alignmentEngine = alignmentEngine,
        fusionEngine = fusionEngine,
        portraitEngine = portraitEngine,
        aiEnhanceEngine = aiEnhanceEngine,
        dispatchers = appDispatchers,
        logger = NoOpLogger(),
    )

    @Test
    fun `process executes AI enhance pipeline successfully and reports progress`() = runTest {
        val config = AiEnhanceConfig(strength = 1.0f)
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/ai_enhance_test",
            mode = ProcessingMode.AI_ENHANCE,
            aiEnhanceConfig = config,
        )

        var progressReported = 0f
        val result = pipeline.process(request) { progress ->
            progressReported = progress
        }

        assertTrue("Result must be success", result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.AI_ENHANCE, data.modeUsed)
        assertTrue("AI Enhance should be reported as applied", data.isAiEnhanceApplied)
        assertTrue("Progress should reach final stage", progressReported >= 0.8f)
    }
}
