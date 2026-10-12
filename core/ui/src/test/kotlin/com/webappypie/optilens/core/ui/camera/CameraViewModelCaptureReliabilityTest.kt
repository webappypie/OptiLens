package com.webappypie.optilens.core.ui.camera

import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.model.CameraMode
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

/**
 * Phase 01 regressions: every shutter press in a burst mode must persist a real captured
 * frame, per-shot exposure overrides must not leak, and unimplemented modes are not offered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelCaptureReliabilityTest {

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

    private fun computationalNightPlan(evOffsets: List<Int>) = NightExecutionPlan(
        mode = NightModeType.CUSTOM_COMPUTATIONAL,
        exposurePlan = NightExposurePlan(
            frameCount = 6,
            targetShutterNanos = 80_000_000L,
            evOffsets = evOffsets,
            expectedCaptureDurationMs = 600L,
            stabilityRequired = StabilityClassification.HANDHELD_STABLE,
        ),
        reason = "test",
        isPreviewBoostRecommended = false,
    )

    @Test
    fun `computational night shot saves exactly one base-exposure frame`() = runTest(testDispatcher) {
        cameraController.emitNightPlan(computationalNightPlan(evOffsets = listOf(0, -2)))
        observeUiState()

        viewModel.takeNightPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, cameraController.savedBurstFrames.size)
        assertEquals(0, cameraController.savedBurstFrames.single().sequenceIndex % 2)
        assertNotNull(cameraController.lastCapturedPhoto.first())
        val state = viewModel.uiState.value
        assertFalse(state.isCapturing)
        assertFalse(state.isProcessingNightShot)
        assertNull(state.holdSteadyRemainingSec)
        assertNull(state.errorMessage)
    }

    @Test
    fun `failed night burst reports an error and saves nothing`() = runTest(testDispatcher) {
        cameraController.emitNightPlan(computationalNightPlan(evOffsets = listOf(0)))
        cameraController.fakeBurstEngine.shouldFail = true
        observeUiState()

        viewModel.takeNightPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(cameraController.savedBurstFrames.isEmpty())
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isCapturing)
    }

    @Test
    fun `best shot keep saves the recommended frame`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.takeBestShotPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        val result = viewModel.uiState.value.bestShotResult
        assertNotNull(result)
        assertTrue(viewModel.uiState.value.isBestShotSheetVisible)
        assertTrue("Nothing is saved before the user decides", cameraController.savedBurstFrames.isEmpty())

        viewModel.keepBestShotSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(result!!.selectedIndex, cameraController.savedBurstFrames.single().sequenceIndex)
        assertFalse(viewModel.uiState.value.isBestShotSheetVisible)
    }

    @Test
    fun `best shot keep after override saves the chosen alternate`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.takeBestShotPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        val recommended = viewModel.uiState.value.bestShotResult!!.bestIndex
        val alternate = viewModel.uiState.value.bestShotResult!!.candidates
            .first { it.index != recommended }.index

        viewModel.overrideBestShotSelection(alternate)
        viewModel.keepBestShotSelection()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(alternate, cameraController.savedBurstFrames.single().sequenceIndex)
    }

    @Test
    fun `best shot discard saves nothing`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.takeBestShotPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.dismissBestShotSheet()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(cameraController.savedBurstFrames.isEmpty())
        assertFalse(viewModel.uiState.value.isBestShotSheetVisible)
    }

    @Test
    fun `pet shot restores automatic shutter afterwards`() = runTest(testDispatcher) {
        observeUiState()

        viewModel.takePetPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(cameraController.proState.first().shutterSpeedNanos)
        assertNotNull(cameraController.lastCapturedPhoto.first())
        assertFalse(viewModel.uiState.value.isProcessingPetShot)
    }

    @Test
    fun `moon shot saves a frame and restores exposure overrides`() = runTest(testDispatcher) {
        observeUiState()
        val evBefore = viewModel.uiState.value.exposureState.index

        viewModel.takeMoonPhoto(targetRotation = 0)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, cameraController.savedBurstFrames.size)
        assertNull(cameraController.proState.first().shutterSpeedNanos)
        assertEquals(evBefore, cameraController.exposureState.first().index)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `video mode is not offered because no recording pipeline exists`() = runTest(testDispatcher) {
        observeUiState()

        assertFalse(CameraMode.VIDEO in viewModel.uiState.value.availableModes)
        assertTrue(CameraMode.PHOTO in viewModel.uiState.value.availableModes)
        assertTrue(CameraMode.NIGHT in viewModel.uiState.value.availableModes)
    }
}
