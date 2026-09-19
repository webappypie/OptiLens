package com.webappypie.optilens.core.ui.camera

import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.compose.ui.geometry.Offset
import app.cash.turbine.test
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.model.FlashMode
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
    fun `zoom ratio change propagates to state`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.onZoomRatioChanged(2.5f)
            testScheduler.advanceUntilIdle()

            val state = awaitItem()
            assertEquals(2.5f, state.zoomState.currentZoom, 0.01f)

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
