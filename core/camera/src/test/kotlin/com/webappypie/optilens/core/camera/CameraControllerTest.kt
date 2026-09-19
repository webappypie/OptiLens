package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.CameraSessionState
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.LensFacing
import com.webappypie.optilens.core.camera.model.PhysicalSensorInfo
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.common.result.OptiResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `deriveFromProfile never labels digital crop as optical`() {
        // Device with primary 1x (f=4.5mm), ultrawide 0.6x (f=2.2mm), and periscope 5x (f=22.5mm)
        // Note: NO physical 2x or 3x sensor!
        val profile = CameraDeviceProfile(
            id = "0",
            lensFacing = LensFacing.BACK,
            hardwareLevel = CameraHardwareLevel.LEVEL_3,
            focalLengthsMm = listOf(4.5f),
            minZoom = 0.6f,
            maxZoom = 10.0f,
            physicalSensors = listOf(
                PhysicalSensorInfo(id = "2", focalLengthMm = 2.2f, lensFacing = LensFacing.BACK), // ~0.6x UW
                PhysicalSensorInfo(id = "3", focalLengthMm = 22.5f, lensFacing = LensFacing.BACK), // 5x Tele
            ),
        )

        val stops = ZoomStop.deriveFromProfile(profile)
        assertTrue(stops.isNotEmpty())

        val stop1x = stops.first { it.ratio == 1.0f }
        assertTrue("1x must be optical", stop1x.isOptical)

        val stop06x = stops.first { it.ratio == 0.6f }
        assertTrue("0.6x backed by physical sensor must be optical", stop06x.isOptical)

        val stop2x = stops.first { it.ratio == 2.0f }
        assertFalse("2x digital crop must NEVER be labeled optical", stop2x.isOptical)

        val stop3x = stops.firstOrNull { it.ratio == 3.0f }
        if (stop3x != null) {
            assertFalse("3x digital crop without 3x sensor must NEVER be labeled optical", stop3x.isOptical)
        }

        val stop5x = stops.first { it.ratio == 5.0f }
        assertTrue("5x backed by 22.5mm physical sensor must be optical", stop5x.isOptical)
    }

    @Test
    fun `pro controls update ISO, shutter, focus, and white balance`() = runTest {
        controller.setIso(800)
        assertEquals(800, controller.proState.first().iso)

        controller.setShutterSpeed(16_666_666L) // ~1/60s
        assertEquals(16_666_666L, controller.proState.first().shutterSpeedNanos)

        controller.setFocusDistance(5.0f)
        assertEquals(5.0f, controller.proState.first().focusDistanceDiopters)

        controller.setWhiteBalance(WhiteBalanceMode.DAYLIGHT)
        assertEquals(WhiteBalanceMode.DAYLIGHT, controller.proState.first().whiteBalanceMode)

        assertTrue(controller.proState.first().isAnyManualActive)
    }

    @Test
    fun `resetProToAuto restores all manual controls to default AUTO values`() = runTest {
        controller.setIso(1600)
        controller.setShutterSpeed(1_000_000L)
        controller.setFocusDistance(2.0f)
        controller.setWhiteBalance(WhiteBalanceMode.CLOUDY)
        controller.setExposureCompensation(2)
        assertTrue(controller.proState.first().isAnyManualActive)

        controller.resetProToAuto()
        val state = controller.proState.first()

        assertNull(state.iso)
        assertNull(state.shutterSpeedNanos)
        assertNull(state.focusDistanceDiopters)
        assertEquals(WhiteBalanceMode.AUTO, state.whiteBalanceMode)
        assertEquals(0, state.evIndex)
        assertFalse(state.isAnyManualActive)
    }

    @Test
    fun `histogram data stream emits 64 bins and respects toggle`() = runTest {
        val hist = controller.histogramData.first()
        assertEquals(64, hist.bins.size)

        controller.setHistogramEnabled(true)
        assertTrue(controller.isHistogramEnabled)

        controller.setHistogramEnabled(false)
        assertFalse(controller.isHistogramEnabled)
    }

    @Test
    fun `sceneClassification stream emits default and custom scenes`() = runTest {
        val initial = controller.sceneClassification.first()
        assertEquals(com.webappypie.optilens.core.camera.model.SceneType.GENERAL, initial.primaryScene)

        controller.emitScene(com.webappypie.optilens.core.camera.model.SceneClassification(primaryScene = com.webappypie.optilens.core.camera.model.SceneType.PORTRAIT))
        assertEquals(com.webappypie.optilens.core.camera.model.SceneType.PORTRAIT, controller.sceneClassification.first().primaryScene)
    }

    @Test
    fun `qualityMetrics and motionState streams emit updates`() = runTest {
        controller.emitQuality(com.webappypie.optilens.core.camera.model.QualityMetrics(luminance = 200f, isBacklit = true))
        val q = controller.qualityMetrics.first()
        assertEquals(200f, q.luminance, 0.01f)
        assertTrue(q.isBacklit)

        controller.emitMotion(com.webappypie.optilens.core.camera.model.MotionState(cameraShakeLevel = com.webappypie.optilens.core.camera.model.CameraShakeLevel.HIGH))
        val m = controller.motionState.first()
        assertTrue(m.isCameraShaking)
    }

    @Test
    fun `captureStrategy and detectedFaces streams emit updates`() = runTest {
        controller.emitStrategy(com.webappypie.optilens.core.camera.strategy.CaptureStrategy(mode = com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode.NIGHT_STACK, recommendedFrameCount = 8))
        val s = controller.captureStrategy.first()
        assertEquals(com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode.NIGHT_STACK, s.mode)
        assertEquals(8, s.recommendedFrameCount)

        val faces = listOf(com.webappypie.optilens.core.camera.model.DetectedFace(bounds = com.webappypie.optilens.core.camera.model.NormalizedRect(0f, 0f, 0.5f, 0.5f)))
        controller.emitFaces(faces)
        assertEquals(1, controller.detectedFaces.first().size)
    }
}
