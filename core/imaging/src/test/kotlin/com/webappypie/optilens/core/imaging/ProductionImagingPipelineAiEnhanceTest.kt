package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.imaging.enhance.AiEnhanceConfig
import com.webappypie.optilens.core.imaging.enhance.OnDeviceEnhancementEngine
import com.webappypie.optilens.core.imaging.fusion.NativeMultiFrameFusionEngine
import com.webappypie.optilens.core.imaging.portrait.NativePortraitEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The enhance mode must never report a successful enhancement it did not perform
 * (it previously processed a synthetic flat buffer and returned the original URI as success).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProductionImagingPipelineAiEnhanceTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }

    private fun pipeline(withEngine: Boolean) = ProductionImagingPipeline(
        alignmentEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger()),
        fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger()),
        portraitEngine = NativePortraitEngine(),
        onDeviceEnhanceEngine = if (withEngine) OnDeviceEnhancementEngine(appDispatchers, NoOpLogger()) else null,
        dispatchers = appDispatchers,
        logger = NoOpLogger(),
    )

    private fun request(uri: String) = ProcessingRequest(
        inputUri = uri,
        mode = ProcessingMode.AI_ENHANCE,
        aiEnhanceConfig = AiEnhanceConfig(strength = 1.0f),
    )

    @Test
    fun `enhance without an engine returns an error instead of a fake success`() = runTest {
        val result = pipeline(withEngine = false).process(request("/sdcard/DCIM/OptiLens/photo.jpg")) {}

        assertTrue("Must not report success without processing", result is OptiResult.Error)
    }

    @Test
    fun `enhance with undecodable input returns an error instead of a fake success`() = runTest {
        val result = pipeline(withEngine = true)
            .process(request("content://media/external/images/media/ai_enhance_test")) {}

        assertTrue("Must not report success for input it could not read", result is OptiResult.Error)
    }
}
