package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImagingPipelineTest {

    private val pipeline = FakeImagingPipeline()

    @Test
    fun `process returns successful result and notifies progress`() = runTest {
        val progressValues = mutableListOf<Float>()
        val request = ProcessingRequest(
            inputUri = "content://media/external/images/media/101",
            mode = ProcessingMode.HDR,
            keepOriginal = true,
        )

        val result = pipeline.process(request) { progress ->
            progressValues.add(progress)
        }

        assertTrue(result is OptiResult.Success)
        val data = (result as OptiResult.Success).data
        assertEquals(ProcessingMode.HDR, data.modeUsed)
        assertTrue(data.isHdrApplied)
        assertEquals(request.inputUri, data.originalUri)
        assertNotNull(data.outputUri)
        assertEquals(listOf(0.5f, 1.0f), progressValues)
    }

    @Test
    fun `cancel updates cancellation state`() = runTest {
        assertFalse(pipeline.isCancelled)
        pipeline.cancel()
        assertTrue(pipeline.isCancelled)
    }
}
