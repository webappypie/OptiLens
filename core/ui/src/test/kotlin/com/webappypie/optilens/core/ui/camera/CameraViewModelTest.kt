package com.webappypie.optilens.core.ui.camera

import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cameraController: FakeCameraController
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraController = FakeCameraController()
        viewModel = CameraViewModel(cameraController)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has expected defaults`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.hasCameraPermission)
            assertFalse(initial.isFrontCamera)
            assertEquals(FlashMode.AUTO, initial.flashMode)
            assertEquals(TimerState.OFF, initial.timerState)
            assertEquals(CameraAspectRatio.RATIO_4_3, initial.aspectRatio)
            assertFalse(initial.isCapturing)
            assertNull(initial.lastCapturedPhoto)
            assertNull(initial.focusTarget)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `permission result updates UI state`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.onPermissionResult(true)
            testScheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.hasCameraPermission)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle flash mode cycles through flash modes`() = runTest {
        viewModel.uiState.test {
            awaitItem() // AUTO

            viewModel.toggleFlashMode()
            testScheduler.advanceUntilIdle()
            assertEquals(FlashMode.ON, awaitItem().flashMode)

            viewModel.toggleFlashMode()
            testScheduler.advanceUntilIdle()
            assertEquals(FlashMode.OFF, awaitItem().flashMode)

            viewModel.toggleFlashMode()
            testScheduler.advanceUntilIdle()
            assertEquals(FlashMode.AUTO, awaitItem().flashMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle timer cycles through OFF, 3s, 10s`() = runTest {
        viewModel.uiState.test {
            assertEquals(TimerState.OFF, awaitItem().timerState)

            viewModel.toggleTimer()
            testScheduler.advanceUntilIdle()
            assertEquals(TimerState.SEC_3, awaitItem().timerState)

            viewModel.toggleTimer()
            testScheduler.advanceUntilIdle()
            assertEquals(TimerState.SEC_10, awaitItem().timerState)

            viewModel.toggleTimer()
            testScheduler.advanceUntilIdle()
            assertEquals(TimerState.OFF, awaitItem().timerState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle aspect ratio cycles through 4-3, 16-9, 1-1`() = runTest {
        viewModel.uiState.test {
            assertEquals(CameraAspectRatio.RATIO_4_3, awaitItem().aspectRatio)

            viewModel.toggleAspectRatio()
            testScheduler.advanceUntilIdle()
            assertEquals(CameraAspectRatio.RATIO_16_9, awaitItem().aspectRatio)

            viewModel.toggleAspectRatio()
            testScheduler.advanceUntilIdle()
            assertEquals(CameraAspectRatio.RATIO_1_1, awaitItem().aspectRatio)

            viewModel.toggleAspectRatio()
            testScheduler.advanceUntilIdle()
            assertEquals(CameraAspectRatio.RATIO_4_3, awaitItem().aspectRatio)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `zoom ratio change propagates to state and triggers haptics at optical stops`() = runTest {
        viewModel.opticalHapticFlow.test {
            // FakeCameraController has optical stops at 0.6x, 1x, 5x, and digital crop at 2x
            viewModel.onZoomRatioChanged(0.6f)
            testScheduler.advanceUntilIdle()
            awaitItem() // Haptic fired crossing 0.6x optical stop

            viewModel.onZoomRatioChanged(2.0f) // 2.0x is digital crop, no optical crossing event
            testScheduler.advanceUntilIdle()

            viewModel.onZoomRatioChanged(5.0f) // 5.0x optical telephoto stop
            testScheduler.advanceUntilIdle()
            awaitItem() // Haptic fired crossing 5.0x optical stop

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `flip camera toggles between back and front`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.flipCamera()
            testScheduler.advanceUntilIdle()

            val frontState = awaitItem()
            assertTrue(frontState.isFrontCamera)

            viewModel.flipCamera()
            testScheduler.advanceUntilIdle()

            val backState = awaitItem()
            assertFalse(backState.isFrontCamera)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pro manual controls update ISO, shutter, focus, WB and reset to auto`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setIso(400)
            testScheduler.advanceUntilIdle()
            assertEquals(400, expectMostRecentItem().proState.iso)

            viewModel.setShutterSpeed(8_000_000L) // 1/125s
            testScheduler.advanceUntilIdle()
            assertEquals(8_000_000L, expectMostRecentItem().proState.shutterSpeedNanos)

            viewModel.setFocusDistance(3.0f)
            testScheduler.advanceUntilIdle()
            assertEquals(3.0f, expectMostRecentItem().proState.focusDistanceDiopters)

            viewModel.setWhiteBalance(WhiteBalanceMode.INCANDESCENT)
            testScheduler.advanceUntilIdle()
            assertEquals(WhiteBalanceMode.INCANDESCENT, expectMostRecentItem().proState.whiteBalanceMode)

            viewModel.resetProToAuto()
            testScheduler.advanceUntilIdle()
            val resetState = expectMostRecentItem()
            assertNull(resetState.proState.iso)
            assertNull(resetState.proState.shutterSpeedNanos)
            assertNull(resetState.proState.focusDistanceDiopters)
            assertEquals(WhiteBalanceMode.AUTO, resetState.proState.whiteBalanceMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takePhoto captures and updates lastCapturedPhoto`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.takePhoto(targetRotation = 0)
            testScheduler.advanceUntilIdle()

            val state = expectMostRecentItem()
            assertFalse(state.isCapturing)
            assertNotNull(state.lastCapturedPhoto)
            assertTrue(state.lastCapturedPhoto?.uri?.startsWith("content://") == true)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takePhotoWithTimer executes countdown before triggering capture`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.toggleTimer() // switch to 3s
            testScheduler.advanceUntilIdle()
            assertEquals(TimerState.SEC_3, expectMostRecentItem().timerState)

            viewModel.takePhotoWithTimer(targetRotation = 0)
            testScheduler.advanceTimeBy(100)
            assertEquals(3, expectMostRecentItem().timerCountdown)

            testScheduler.advanceTimeBy(1_000)
            assertEquals(2, expectMostRecentItem().timerCountdown)

            testScheduler.advanceTimeBy(1_000)
            assertEquals(1, expectMostRecentItem().timerCountdown)

            testScheduler.advanceTimeBy(1_100)
            val capturedState = expectMostRecentItem()
            assertNull(capturedState.timerCountdown)
            assertNotNull(capturedState.lastCapturedPhoto)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeated capture stress test handles sequential captures cleanly`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            for (i in 1..5) {
                viewModel.takePhoto(targetRotation = 0)
                testScheduler.advanceUntilIdle()
                val item = expectMostRecentItem()
                assertFalse("Capture $i should not remain capturing", item.isCapturing)
                assertNotNull("Capture $i should produce a captured photo", item.lastCapturedPhoto)
            }

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tap to focus sets and then clears focus target after timeout`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            val pointFactory = SurfaceOrientedMeteringPointFactory(1f, 1f)
            val meteringPoint = pointFactory.createPoint(0.5f, 0.5f)
            val tapOffset = Offset(100f, 200f)

            viewModel.onTapToFocus(tapOffset, meteringPoint)
            testScheduler.advanceTimeBy(100)

            val focusingState = awaitItem()
            assertEquals(tapOffset, focusingState.focusTarget)

            testScheduler.advanceTimeBy(2_100)
            val clearedState = awaitItem()
            assertNull(clearedState.focusTarget)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `intelligence stream updates propagate scene, quality, motion, strategy, and faces to uiState`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            // 1. Emit scene
            cameraController.emitScene(
                com.webappypie.optilens.core.camera.model.SceneClassification(
                    primaryScene = com.webappypie.optilens.core.camera.model.SceneType.PORTRAIT,
                    confidence = 0.92f,
                )
            )
            testScheduler.advanceUntilIdle()
            assertEquals(com.webappypie.optilens.core.camera.model.SceneType.PORTRAIT, awaitItem().sceneClassification.primaryScene)

            // 2. Emit quality metrics
            cameraController.emitQuality(
                com.webappypie.optilens.core.camera.model.QualityMetrics(
                    luminance = 210f,
                    isBacklit = true,
                )
            )
            testScheduler.advanceUntilIdle()
            val qualityItem = awaitItem()
            assertEquals(210f, qualityItem.qualityMetrics.luminance, 0.01f)
            assertTrue(qualityItem.qualityMetrics.isBacklit)

            // 3. Emit motion state
            cameraController.emitMotion(
                com.webappypie.optilens.core.camera.model.MotionState(
                    cameraShakeLevel = com.webappypie.optilens.core.camera.model.CameraShakeLevel.HIGH,
                )
            )
            testScheduler.advanceUntilIdle()
            assertTrue(awaitItem().motionState.isCameraShaking)

            // 4. Emit capture strategy
            cameraController.emitStrategy(
                com.webappypie.optilens.core.camera.strategy.CaptureStrategy(
                    mode = com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode.NIGHT_STACK,
                    recommendedFrameCount = 8,
                    uiHint = com.webappypie.optilens.core.camera.strategy.CaptureUiHint.NIGHT_SUGGESTED,
                )
            )
            testScheduler.advanceUntilIdle()
            val strategyItem = awaitItem()
            assertEquals(com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode.NIGHT_STACK, strategyItem.captureStrategy.mode)
            assertEquals(com.webappypie.optilens.core.camera.strategy.CaptureUiHint.NIGHT_SUGGESTED, strategyItem.captureStrategy.uiHint)

            // 5. Emit faces
            val face = com.webappypie.optilens.core.camera.model.DetectedFace(
                bounds = com.webappypie.optilens.core.camera.model.NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
            )
            cameraController.emitFaces(listOf(face))
            testScheduler.advanceUntilIdle()
            assertEquals(1, awaitItem().detectedFaces.size)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takeBurstPhoto triggers burst acquisition and emits lastBurstResult`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.takeBurstPhoto(frameCount = 3)
            testScheduler.advanceUntilIdle()

            var latest = awaitItem()
            while (latest.lastBurstResult == null) {
                latest = awaitItem()
            }

            assertEquals(3, latest.lastBurstResult.frameCount)
            assertFalse(latest.isBurstCapturing)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takeBurstPhoto uses recommendedFrameCount from active capture strategy`() = runTest {
        cameraController.emitStrategy(
            com.webappypie.optilens.core.camera.strategy.CaptureStrategy(
                mode = com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode.NIGHT_STACK,
                recommendedFrameCount = 6,
            )
        )

        viewModel.uiState.test {
            var item = awaitItem()
            while (item.captureStrategy.recommendedFrameCount != 6) {
                item = awaitItem()
            }

            viewModel.takeBurstPhoto()
            testScheduler.advanceUntilIdle()

            var latest = awaitItem()
            while (latest.lastBurstResult == null) {
                latest = awaitItem()
            }

            assertEquals(6, latest.lastBurstResult.frameCount)
            assertEquals(com.webappypie.optilens.core.camera.strategy.CaptureStrategyMode.NIGHT_STACK, latest.lastBurstResult.modeUsed)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `pro manual settings update uiState and auto reset restores default`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setIso(400)
            viewModel.setShutterSpeed(2_000_000L)
            viewModel.setFocusDistance(1.5f)
            viewModel.setWhiteBalance(WhiteBalanceMode.CLOUDY)
            viewModel.onExposureCompensationChanged(-1)
            testScheduler.advanceUntilIdle()

            var latest = expectMostRecentItem()
            assertEquals(400, latest.proState.iso)
            assertEquals(2_000_000L, latest.proState.shutterSpeedNanos)
            assertEquals(1.5f, latest.proState.focusDistanceDiopters ?: 0f, 0.01f)
            assertEquals(WhiteBalanceMode.CLOUDY, latest.proState.whiteBalanceMode)
            assertEquals(-1, latest.proState.evIndex)
            assertTrue(latest.proState.isAnyManualActive)

            viewModel.resetProToAuto()
            testScheduler.advanceUntilIdle()

            latest = expectMostRecentItem()
            assertNull(latest.proState.iso)
            assertNull(latest.proState.shutterSpeedNanos)
            assertNull(latest.proState.focusDistanceDiopters)
            assertEquals(WhiteBalanceMode.AUTO, latest.proState.whiteBalanceMode)
            assertEquals(0, latest.proState.evIndex)
            assertFalse(latest.proState.isAnyManualActive)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `raw format and companion toggles update uiState`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.setRawCaptureEnabled(true)
            viewModel.setRawCaptureFormat(com.webappypie.optilens.core.camera.model.RawCaptureFormat.RAW_SENSOR)
            viewModel.setSaveCompanionJpeg(false)
            testScheduler.advanceUntilIdle()

            var latest = expectMostRecentItem()
            assertTrue(latest.proState.isRawEnabled)
            assertEquals(com.webappypie.optilens.core.camera.model.RawCaptureFormat.RAW_SENSOR, latest.proState.rawFormat)
            assertFalse(latest.proState.saveCompanionJpeg)

            viewModel.setSaveCompanionJpeg(true)
            testScheduler.advanceUntilIdle()
            latest = expectMostRecentItem()
            assertTrue(latest.proState.saveCompanionJpeg)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `visual aids peaking and zebra toggles and histogram mode cycling work properly`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            assertFalse(viewModel.uiState.value.focusPeakingData.isEnabled)
            assertFalse(viewModel.uiState.value.exposureZebraData.isEnabled)

            viewModel.toggleFocusPeaking()
            testScheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().focusPeakingData.isEnabled)

            viewModel.toggleExposureZebra()
            testScheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().exposureZebraData.isEnabled)

            viewModel.cycleHistogramMode()
            testScheduler.advanceUntilIdle()
            assertEquals(com.webappypie.optilens.core.camera.model.HistogramMode.RGB, expectMostRecentItem().proState.histogramMode)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
