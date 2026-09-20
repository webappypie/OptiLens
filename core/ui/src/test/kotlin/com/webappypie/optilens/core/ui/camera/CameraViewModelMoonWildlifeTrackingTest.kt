package com.webappypie.optilens.core.ui.camera

import app.cash.turbine.test
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.model.CameraMode
import com.webappypie.optilens.core.camera.moon.MoonDetectionState
import com.webappypie.optilens.core.camera.moon.MoonDiscRoi
import com.webappypie.optilens.core.camera.moon.MoonModeEngine
import com.webappypie.optilens.core.camera.moon.StabilityCue
import com.webappypie.optilens.core.camera.tracking.TrackedObjectBounds
import com.webappypie.optilens.core.camera.tracking.TrackedObjectState
import com.webappypie.optilens.core.camera.tracking.TrackingStatus
import com.webappypie.optilens.core.camera.wildlife.WildlifeDetectionState
import com.webappypie.optilens.core.camera.wildlife.WildlifeModeEngine
import com.webappypie.optilens.core.camera.wildlife.WildlifeRoi
import com.webappypie.optilens.core.camera.wildlife.WildlifeSubjectType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelMoonWildlifeTrackingTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cameraController: FakeCameraController
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraController = FakeCameraController()
        viewModel = CameraViewModel(
            cameraController = cameraController,
            moonModeEngine = MoonModeEngine(),
            wildlifeModeEngine = WildlifeModeEngine(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching to MOON and WILDLIFE modes updates internal controller and ui state`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(CameraMode.PHOTO, initial.currentMode)

            // Switch to MOON
            viewModel.setCameraMode(CameraMode.MOON)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CameraMode.MOON, awaitItem().currentMode)

            // Switch to WILDLIFE
            viewModel.setCameraMode(CameraMode.WILDLIFE)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CameraMode.WILDLIFE, awaitItem().currentMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moon detection state emissions update ui state with stability cue and roi`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            val detectedRoi = MoonDiscRoi(
                left = 0.45f, top = 0.45f, right = 0.55f, bottom = 0.55f,
                centerX = 0.5f, centerY = 0.5f, radius = 0.05f,
                meanLuma = 220f, circularity = 0.95f, confidence = 0.92f
            )
            val moonState = MoonDetectionState(
                isMoonDetected = true,
                roi = detectedRoi,
                confidence = 0.92f,
                stabilityCue = StabilityCue.STABLE,
                suggestedEvOffset = -3,
            )

            cameraController.emitMoonState(moonState)
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.moonDetectionState.isMoonDetected)
            assertEquals(StabilityCue.STABLE, updated.moonDetectionState.stabilityCue)
            assertEquals(-3, updated.moonDetectionState.suggestedEvOffset)
            assertNotNull(updated.moonDetectionState.roi)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `wildlife detection state emissions update ui state with subject type`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            val wildlifeRoi = WildlifeRoi(
                left = 0.3f, top = 0.3f, right = 0.7f, bottom = 0.7f,
                centerX = 0.5f, centerY = 0.5f,
                subjectType = WildlifeSubjectType.BIRD,
                confidence = 0.88f,
            )
            val wildlifeState = WildlifeDetectionState(
                isDetected = true,
                roi = wildlifeRoi,
                confidence = 0.88f,
                suggestedShutterNanos = 1_000_000L,
            )

            cameraController.emitWildlifeState(wildlifeState)
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.wildlifeDetectionState.isDetected)
            assertEquals(WildlifeSubjectType.BIRD, updated.wildlifeDetectionState.roi?.subjectType)
            assertEquals(1_000_000L, updated.wildlifeDetectionState.suggestedShutterNanos)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `startObjectTracking and stopObjectTracking update trackedObjectState`() = runTest {
        viewModel.uiState.test {
            awaitItem()

            viewModel.startObjectTracking(0.5f, 0.5f)
            testDispatcher.scheduler.advanceUntilIdle()

            val trackedState = awaitItem().trackedObjectState
            assertEquals(TrackingStatus.TRACKING, trackedState.status)
            assertTrue(trackedState.status.isVisualActive)
            assertEquals(0.5f, trackedState.bounds?.centerX ?: 0f, 0.05f)

            viewModel.stopObjectTracking()
            testDispatcher.scheduler.advanceUntilIdle()

            val stoppedState = awaitItem().trackedObjectState
            assertEquals(TrackingStatus.INACTIVE, stoppedState.status)
            assertFalse(stoppedState.status.isVisualActive)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takeMoonPhoto executes burst capture with spot exposure without error`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.setCameraMode(CameraMode.MOON)
            testDispatcher.scheduler.advanceUntilIdle()
            val modeState = awaitItem()
            assertEquals(CameraMode.MOON, modeState.currentMode)

            viewModel.takeMoonPhoto(targetRotation = 0)
            testDispatcher.scheduler.advanceUntilIdle()

            var latest = awaitItem()
            while (latest.isCapturing || latest.isProcessingMoonShot) {
                latest = awaitItem()
            }

            assertFalse(latest.isCapturing)
            assertFalse(latest.isProcessingMoonShot)
            assertEquals(null, latest.errorMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takeWildlifePhoto executes burst and displays best shot alternates`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            viewModel.setCameraMode(CameraMode.WILDLIFE)
            testDispatcher.scheduler.advanceUntilIdle()
            val modeState = awaitItem()
            assertEquals(CameraMode.WILDLIFE, modeState.currentMode)

            viewModel.takeWildlifePhoto(targetRotation = 0)
            testDispatcher.scheduler.advanceUntilIdle()

            var latest = awaitItem()
            while (latest.isCapturing || latest.isProcessingWildlifeShot || !latest.isBestShotSheetVisible) {
                latest = awaitItem()
            }

            assertFalse(latest.isCapturing)
            assertFalse(latest.isProcessingWildlifeShot)
            assertTrue("Best shot sheet must be presented after wildlife burst. Error was: ${latest.errorMessage}", latest.isBestShotSheetVisible)
            assertNotNull(latest.bestShotResult)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
