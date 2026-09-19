package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraControllerTest {

    private val controller = FakeCameraController()

    @Test
    fun `capabilities stream emits default capabilities`() = runTest {
        val caps = controller.capabilities.first()
        assertTrue(caps.hasRearCamera)
        assertTrue(caps.supportsHdrCapture)
        assertEquals(listOf(0.6f, 1.0f, 2.0f, 5.0f), caps.availableZoomRatios)
    }

    @Test
    fun `start and stop preview update state`() = runTest {
        assertFalse(controller.isPreviewActive)
        val startResult = controller.startPreview()
        assertTrue(startResult is OptiResult.Success)
        assertTrue(controller.isPreviewActive)

        controller.stopPreview()
        assertFalse(controller.isPreviewActive)
    }

    @Test
    fun `capturePhoto returns success with content uri`() = runTest {
        val result = controller.capturePhoto()
        assertTrue(result is OptiResult.Success)
        val uri = (result as OptiResult.Success).data
        assertTrue(uri.startsWith("content://"))
    }

    @Test
    fun `zoom and flip camera update state`() = runTest {
        controller.setZoom(2.0f)
        assertEquals(2.0f, controller.currentZoom, 0.001f)

        assertFalse(controller.isFrontFacing)
        controller.flipCamera()
        assertTrue(controller.isFrontFacing)
    }
}
