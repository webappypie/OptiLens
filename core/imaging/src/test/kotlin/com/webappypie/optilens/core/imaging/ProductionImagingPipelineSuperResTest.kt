package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.imaging.fusion.NativeMultiFrameFusionEngine
import com.webappypie.optilens.core.imaging.sr.NativeSuperResolutionEngine
import com.webappypie.optilens.core.imaging.sr.SuperResolutionConfig
import com.webappypie.optilens.core.logging.NoOpLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductionImagingPipelineSuperResTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val appDispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }

    private val alignmentEngine = NativeFrameAlignmentEngine(appDispatchers, NoOpLogger())
    private val fusionEngine = NativeMultiFrameFusionEngine(appDispatchers, NoOpLogger())
    private val superResEngine = NativeSuperResolutionEngine().apply {
        setForceJvmFallback(true)
    }

    private lateinit var pipeline: ProductionImagingPipeline

    @Before
    fun setUp() {
        pipeline = ProductionImagingPipeline(
            alignmentEngine = alignmentEngine,
            fusionEngine = fusionEngine,
            superResEngine = superResEngine,
            dispatchers = appDispatchers,
            logger = NoOpLogger(),
        )
    }

    @Test
    fun `process executes multi-frame Super Resolution when stack is available`() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/test_zoom_mfsr.jpg",
            mode = ProcessingMode.SUPER_RES_ZOOM,
            burstFrameUris = listOf("frame_0.jpg", "frame_1.jpg", "frame_2.jpg", "frame_3.jpg"),
            targetZoomRatio = 2.5f,
            superResConfig = SuperResolutionConfig(scaleFactor = 2.0f),
        )

        var lastProgress = 0.0f
        val result = pipeline.process(request) { progress ->
            lastProgress = progress
        }

        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.SUPER_RES_ZOOM, data.modeUsed)
        assertTrue(data.isSuperResApplied)
        assertEquals("MULTI_FRAME_SR", data.superResMethod)
        assertEquals(2.5f, data.zoomFactor, 0.001f)
        assertFalse(data.isPureOptical)
        assertEquals(4000, data.width) // 2000 * 2.0f
        assertEquals(3000, data.height) // 1500 * 2.0f
        assertEquals(1.0f, lastProgress, 0.001f)
    }

    @Test
    fun `process executes single-frame fallback Super Resolution when no burst stack`() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/test_zoom_sfsr.jpg",
            mode = ProcessingMode.SUPER_RES_ZOOM,
            burstFrameUris = listOf("frame_0.jpg"),
            targetZoomRatio = 2.0f,
            superResConfig = SuperResolutionConfig(scaleFactor = 2.0f),
        )

        var lastProgress = 0.0f
        val result = pipeline.process(request) { progress ->
            lastProgress = progress
        }

        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.SUPER_RES_ZOOM, data.modeUsed)
        assertTrue(data.isSuperResApplied)
        assertEquals("SINGLE_FRAME_EDGE_SR", data.superResMethod)
        assertEquals(2.0f, data.zoomFactor, 0.001f)
        assertFalse(data.isPureOptical)
        assertEquals(1.0f, lastProgress, 0.001f)
    }

    @Test
    fun `cancel halts Super Resolution processing`() = runTest {
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/test_zoom_cancel.jpg",
            mode = ProcessingMode.SUPER_RES_ZOOM,
        )

        pipeline.cancel()
        val result = pipeline.process(request)
        assertTrue(result is OptiResult.Error)
    }
}
