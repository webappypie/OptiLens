package com.webappypie.optilens.core.ui.camera

import app.cash.turbine.test
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.night.NightExecutionPlan
import com.webappypie.optilens.core.camera.night.NightExposurePlan
import com.webappypie.optilens.core.camera.night.NightModeType
import com.webappypie.optilens.core.camera.night.StabilityAssessment
import com.webappypie.optilens.core.camera.night.StabilityClassification
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelNightTest {

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
    fun togglePreviewLowLightBoost_togglesBoostState() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.isPreviewBoostActive)

            viewModel.togglePreviewLowLightBoost()
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertTrue(updated.isPreviewBoostActive)

            viewModel.togglePreviewLowLightBoost()
            testDispatcher.scheduler.advanceUntilIdle()

            val toggledBack = awaitItem()
            assertFalse(toggledBack.isPreviewBoostActive)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun nightPlanAndStabilityEmissions_flowIntoUiState() = runTest {
        val plan = NightExecutionPlan(
            mode = NightModeType.CUSTOM_COMPUTATIONAL,
            exposurePlan = NightExposurePlan(
                frameCount = 8,
                targetShutterNanos = 80_000_000L,
                evOffsets = listOf(0),
                expectedCaptureDurationMs = 1200L,
                stabilityRequired = StabilityClassification.HANDHELD_STABLE,
            ),
            reason = "Custom test plan",
            isPreviewBoostRecommended = true,
        )
        val stability = StabilityAssessment(
            classification = StabilityClassification.TRIPOD,
            stabilityScore = 98.0f,
            stabilityConfidence = 0.95f,
            averageAngularVelocity = 0.01f,
            isTripod = true,
        )

        viewModel.uiState.test {
            awaitItem() // Initial

            cameraController.emitNightPlan(plan)
            cameraController.emitStability(stability)
            cameraController.emitThermalState(DeviceThermalState.MODERATE)
            testDispatcher.scheduler.advanceUntilIdle()

            // Find state with updated night values
            var item = awaitItem()
            while (item.nightExecutionPlan == null || item.thermalState != DeviceThermalState.MODERATE) {
                item = awaitItem()
            }

            assertEquals(NightModeType.CUSTOM_COMPUTATIONAL, item.nightExecutionPlan?.mode)
            assertEquals(8, item.nightExecutionPlan?.exposurePlan?.frameCount)
            assertEquals(StabilityClassification.TRIPOD, item.stabilityAssessment?.classification)
            assertEquals(DeviceThermalState.MODERATE, item.thermalState)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun takeNightPhoto_initiatesCaptureAndCompletesGracefully() = runTest {
        val plan = NightExecutionPlan(
            mode = NightModeType.CUSTOM_COMPUTATIONAL,
            exposurePlan = NightExposurePlan(
                frameCount = 4,
                targetShutterNanos = 50_000_000L,
                evOffsets = listOf(0),
                expectedCaptureDurationMs = 400L,
                stabilityRequired = StabilityClassification.HANDHELD_STABLE,
            ),
            reason = "Handheld test",
        )
        cameraController.emitNightPlan(plan)
        testScheduler.advanceUntilIdle()

        viewModel.uiState.test {
            awaitItem() // Consume initial

            viewModel.takeNightPhoto()
            testScheduler.advanceTimeBy(50L)

            val capturingItem = expectMostRecentItem()
            assertTrue(capturingItem.isCapturing)
            assertNotNull(capturingItem.holdSteadyRemainingSec)

            // Advance through the capture duration and processing delay
            testScheduler.advanceTimeBy(1000L)
            testScheduler.advanceUntilIdle()

            val finalItem = expectMostRecentItem()
            assertFalse(finalItem.isCapturing)
            assertFalse(finalItem.isProcessingNightShot)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
