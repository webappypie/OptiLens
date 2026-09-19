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
class ProductionImagingPipelineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }
    private val pool = BoundedBufferPool(maxCapacity = 16, defaultBufferSize = 4096)
    private val alignmentEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())
    private val fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger())
    private val pipeline = ProductionImagingPipeline(
        alignmentEngine = alignmentEngine,
        fusionEngine = fusionEngine,
        dispatchers = appDispatchers,
        logger = NoOpLogger(),
    )

    @Test
    fun `process executes multi-frame HDR pipeline successfully`() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/test_ref",
            mode = ProcessingMode.HDR,
            burstFrameUris = listOf("frame_0", "frame_1", "frame_2"),
            applyDenoise = true,
            enhanceLighting = true,
        )

        var lastProgress = 0.0f
        val result = pipeline.process(request) { progress ->
            lastProgress = progress
        }

        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.HDR, data.modeUsed)
        assertTrue(data.isHdrApplied)
        assertEquals(1.0f, lastProgress, 0.001f)
    }

    @Test
    fun `process gracefully falls back to single frame when only 1 frame is provided`() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/single_photo",
            mode = ProcessingMode.STANDARD,
            burstFrameUris = emptyList(), // No burst
        )

        val result = pipeline.process(request)
        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals("content://media/external/images/media/single_photo", data.outputUri)
        assertFalse(data.isHdrApplied)
    }

    @Test
    fun `cancel aborts subsequent execution`() = runTest {
        pipeline.cancel()
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/cancelled",
            mode = ProcessingMode.HDR,
            burstFrameUris = listOf("f1", "f2"),
        )
        val result = pipeline.process(request)
        assertTrue(result is OptiResult.Error)
    }
}
