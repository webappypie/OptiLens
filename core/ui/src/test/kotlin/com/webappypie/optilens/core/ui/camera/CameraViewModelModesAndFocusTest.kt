package com.webappypie.optilens.core.ui.camera

import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.compose.ui.geometry.Offset
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.model.CameraMode
import com.webappypie.optilens.core.camera.model.QualityMetrics
import com.webappypie.optilens.core.camera.night.NightExecutionPlan
import com.webappypie.optilens.core.camera.night.NightExposurePlan
import com.webappypie.optilens.core.camera.night.NightModeType
import com.webappypie.optilens.core.camera.night.StabilityClassification
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

/** Portrait/Landscape availability, Landscape HDR decision, Night capture path, focus lock and EV drag. */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelModesAndFocusTest {

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

    private fun TestScope.observeUiState() {
        backgroundScope.launch(testDispatcher) { viewModel.uiState.collect {} }
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private val point = SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)

    @Test
    fun `portrait and landscape are offered, unimplemented modes are not`() = runTest(testDispatcher) {
        observeUiState()
        val modes = viewModel.uiState.value.availableModes
        assertTrue(CameraMode.PORTRAIT in modes)
        assertTrue(CameraMode.LANDSCAPE in modes)
        assertFalse(CameraMode.FOOD in modes)
        assertFalse(CameraMode.DOCUMENT in modes)
        assertFalse(CameraMode.VIDEO in modes)
    }

    @Test
    fun `landscape in normal range takes a single frame`() = runTest(testDispatcher) {
        cameraController.emitQuality(QualityMetrics.DEFAULT.copy(dynamicRangeScore = 40f))
        observeUiState()

        viewModel.takeLandscapePhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, cameraController.savedBurstFrames.size)
        assertNotNull(cameraController.lastCapturedPhoto.first())
        assertFalse(viewModel.uiState.value.isCapturing)
    }

    @Test
    fun `landscape in high dynamic range uses the multi-frame path and restores exposure`() = runTest(testDispatcher) {
        cameraController.emitQuality(QualityMetrics.DEFAULT.copy(dynamicRangeScore = 85f))
        observeUiState()
        val evBefore = viewModel.uiState.value.exposureState.index

        viewModel.takeLandscapePhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Without a merge processor (unit test) the steadiest real burst frame is saved.
        assertEquals(1, cameraController.savedBurstFrames.size)
        assertEquals(evBefore, cameraController.exposureState.first().index)
        assertFalse(viewModel.uiState.value.isCapturing)
    }

    @Test
    fun `night with a vendor-extension plan still captures the real multi-frame burst`() = runTest(testDispatcher) {
        cameraController.emitNightPlan(
            NightExecutionPlan(
                mode = NightModeType.VENDOR_EXTENSION,
                exposurePlan = NightExposurePlan(
                    frameCount = 4,
                    targetShutterNanos = 60_000_000L,
                    evOffsets = listOf(0),
                    expectedCaptureDurationMs = 400L,
                    stabilityRequired = StabilityClassification.HANDHELD_STABLE,
                ),
                reason = "test",
                isPreviewBoostRecommended = false,
            ),
        )
        observeUiState()

        viewModel.takeNightPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, cameraController.savedBurstFrames.size)
    }

    @Test
    fun `long press holds an AE AF lock until the mode changes`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.onTapToFocus(Offset(100f, 120f), point, lock = true)
        testDispatcher.scheduler.advanceUntilIdle()
        testDispatcher.scheduler.advanceTimeBy(10_000L)
        testDispatcher.scheduler.advanceUntilIdle()

        var state = viewModel.uiState.value
        assertTrue(state.isFocusLocked)
        assertEquals(Offset(100f, 120f), state.focusTarget)
        assertEquals(true, state.focusSucceeded)

        viewModel.setCameraMode(CameraMode.PORTRAIT)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertFalse(state.isFocusLocked)
        assertNull(state.focusTarget)
    }

    @Test
    fun `plain tap reticle disappears after a short time`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.onTapToFocus(Offset(10f, 10f), point)
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isFocusLocked)
        assertNull(viewModel.uiState.value.focusTarget)
    }

    @Test
    fun `exposure drag moves compensation in steps and clamps to the device range`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.nudgeExposure(3)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, cameraController.exposureState.first().index)

        viewModel.nudgeExposure(-2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, cameraController.exposureState.first().index)

        viewModel.nudgeExposure(100)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(12, cameraController.exposureState.first().index)
    }
}
