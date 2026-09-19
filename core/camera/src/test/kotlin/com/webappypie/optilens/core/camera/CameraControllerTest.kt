package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

        val state = controller.sessionState.first()
        assertEquals(CameraSessionState.PREVIEW_ACTIVE, state)

        controller.stopPreview()
        assertFalse(controller.isPreviewActive)
        assertEquals(CameraSessionState.IDLE, controller.sessionState.first())
    }

    @Test
    fun `capturePhoto returns success with content uri`() = runTest {
        val result = controller.capturePhoto()
        assertTrue(result is OptiResult.Success)
        val uri = (result as OptiResult.Success).data
        assertTrue(uri.startsWith("content://"))
    }

    @Test
    fun `capturePhoto with rotation emits CapturedPhoto with valid metadata`() = runTest {
        val result = controller.capturePhoto(targetRotation = 90)
        assertTrue(result is OptiResult.Success)
        val photo = (result as OptiResult.Success).data
        assertTrue(photo.uri.startsWith("content://"))
        assertEquals(90, photo.orientationDegrees)
        assertTrue(photo.width > 0)
        assertTrue(photo.height > 0)

        val lastPhoto = controller.lastCapturedPhoto.first()
        assertNotNull(lastPhoto)
        assertEquals(photo.uri, lastPhoto?.uri)
    }

    @Test
    fun `zoom and flip camera update state`() = runTest {
        controller.setZoom(2.0f)
        assertEquals(2.0f, controller.currentZoom, 0.001f)

        assertFalse(controller.isFrontCamera)
        controller.flipCamera()
        assertTrue(controller.isFrontCamera)
    }

    @Test
    fun `flash and torch modes update properly`() = runTest {
        controller.setFlashMode(FlashMode.ON)
        assertEquals(FlashMode.ON, controller.flashMode.first())

        controller.enableTorch(true)
        assertTrue(controller.isTorchEnabled)

        controller.enableTorch(false)
        assertFalse(controller.isTorchEnabled)
    }

    @Test
    fun `exposure compensation updates index`() = runTest {
        controller.setExposureCompensation(2)
        val exp = controller.exposureState.first()
        assertEquals(2, exp.index)
    }
}
