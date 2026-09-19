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
}
