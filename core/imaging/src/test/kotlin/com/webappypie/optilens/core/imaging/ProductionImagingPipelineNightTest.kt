package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.imaging.fusion.NativeMultiFrameFusionEngine
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionImagingPipelineNightTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private val alignmentEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())
    private val fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger())
    private val pipeline = ProductionImagingPipeline(
        alignmentEngine = alignmentEngine,
        fusionEngine = fusionEngine,
        dispatchers = appDispatchers,
        logger = NoOpLogger(),
    )

    @Test
    fun process_executesNightStackPipelineSuccessfully() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/night_ref",
            mode = ProcessingMode.NIGHT,
            burstFrameUris = listOf("night_0", "night_1", "night_2", "night_3"),
            applyDenoise = true,
            enhanceLighting = true,
        )

        var lastProgress = 0.0f
        val result = pipeline.process(request) { progress ->
            lastProgress = progress
        }

        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.NIGHT, data.modeUsed)
        assertTrue(data.isNightModeApplied)
        assertFalse(data.isHdrApplied)
        assertEquals(1.0f, lastProgress, 0.001f)
    }

    @Test
    fun process_nightModeSingleFrameFallback_marksFallbackUsed() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/night_single",
            mode = ProcessingMode.NIGHT,
            burstFrameUris = emptyList(),
        )

        val result = pipeline.process(request)
        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.NIGHT, data.modeUsed)
        assertTrue(data.isNightModeApplied)
        assertTrue(data.isFallbackUsed)
        assertEquals("content://media/external/images/media/night_single", data.outputUri)
    }
}
