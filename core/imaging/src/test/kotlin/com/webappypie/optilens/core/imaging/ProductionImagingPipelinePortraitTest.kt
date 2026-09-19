package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.camera.model.DetectedFace
import com.webappypie.optilens.core.camera.model.NormalizedRect
import com.webappypie.optilens.core.camera.portrait.PortraitAperture
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.imaging.fusion.NativeMultiFrameFusionEngine
import com.webappypie.optilens.core.imaging.portrait.NativePortraitEngine
import com.webappypie.optilens.core.imaging.portrait.PortraitConfig
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
class ProductionImagingPipelinePortraitTest {

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
    private val pipeline = ProductionImagingPipeline(
        alignmentEngine = alignmentEngine,
        fusionEngine = fusionEngine,
        portraitEngine = portraitEngine,
        dispatchers = appDispatchers,
        logger = NoOpLogger(),
    )

    @Test
    fun `process executes portrait pipeline successfully with face detection`() = runTest {
        val faces = listOf(
            DetectedFace(
                bounds = NormalizedRect(0.3f, 0.2f, 0.7f, 0.6f),
                meanLuminance = 110.0f,
            )
        )
        val portraitConfig = PortraitConfig(
            aperture = PortraitAperture.F2_0,
            skinSmoothingStrength = 0.25f,
            faces = faces,
        )

        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/portrait_test",
            mode = ProcessingMode.PORTRAIT,
            portraitConfig = portraitConfig,
        )

        var lastProgress = 0.0f
        val result = pipeline.process(request) { progress ->
            lastProgress = progress
        }

        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.PORTRAIT, data.modeUsed)
        assertTrue(data.isPortraitApplied)
        assertFalse(data.isNightModeApplied)
        assertFalse(data.isHdrApplied)
        assertEquals(1, data.faceCount)
        assertEquals(1.0f, lastProgress, 0.001f)
    }

    @Test
    fun `process handles multi-face portrait capture successfully`() = runTest {
        val faces = listOf(
            DetectedFace(bounds = NormalizedRect(0.1f, 0.2f, 0.4f, 0.6f)),
            DetectedFace(bounds = NormalizedRect(0.6f, 0.2f, 0.9f, 0.6f)),
        )
        val portraitConfig = PortraitConfig(
            aperture = PortraitAperture.F2_8,
            faces = faces,
        )

        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/multi_face_portrait",
            mode = ProcessingMode.PORTRAIT,
            portraitConfig = portraitConfig,
        )

        val result = pipeline.process(request)
        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(2, data.faceCount)
        assertTrue(data.isPortraitApplied)
    }
}
