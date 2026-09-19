package com.webappypie.optilens.core.ui.camera

import app.cash.turbine.test
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.portrait.PortraitAperture
import com.webappypie.optilens.core.camera.portrait.PortraitExecutionPlan
import com.webappypie.optilens.core.camera.portrait.PortraitModeType
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
class CameraViewModelPortraitTest {

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
    fun `portrait aperture selection updates uiState and controller`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(PortraitAperture.DEFAULT, initial.portraitAperture)

            viewModel.setPortraitAperture(PortraitAperture.F1_4)
            testDispatcher.scheduler.advanceUntilIdle()

            val updated = awaitItem()
            assertEquals(PortraitAperture.F1_4, updated.portraitAperture)

            viewModel.setPortraitAperture(PortraitAperture.F5_6)
            testDispatcher.scheduler.advanceUntilIdle()

            val updatedF56 = awaitItem()
            assertEquals(PortraitAperture.F5_6, updatedF56.portraitAperture)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `portrait execution plan emissions flow into uiState`() = runTest {
        val plan = PortraitExecutionPlan(
            mode = PortraitModeType.CUSTOM_SOFTWARE_BOKEH,
            faceCount = 2,
            isBacklitScene = true,
            faceExposureCompensationEv = 0.8f,
            aperture = PortraitAperture.F2_0,
            skinSmoothingStrength = 0.30f,
            reason = "Test plan",
        )

        viewModel.uiState.test {
            awaitItem() // initial null

            cameraController.emitPortraitPlan(plan)
            testDispatcher.scheduler.advanceUntilIdle()

            val stateWithPlan = awaitItem()
            assertNotNull(stateWithPlan.portraitExecutionPlan)
            assertEquals(PortraitModeType.CUSTOM_SOFTWARE_BOKEH, stateWithPlan.portraitExecutionPlan?.mode)
            assertEquals(2, stateWithPlan.portraitExecutionPlan?.faceCount)
            assertTrue(stateWithPlan.portraitExecutionPlan?.isBacklitScene == true)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `takePortraitPhoto triggers capture and resets processing state`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertFalse(initial.isCapturing)
            assertFalse(initial.isProcessingPortraitShot)

            viewModel.takePortraitPhoto()
            advanceTimeBy(100L)

            val capturing = awaitItem()
            assertTrue(capturing.isCapturing)
            assertTrue(capturing.isProcessingPortraitShot)

            testDispatcher.scheduler.advanceUntilIdle()

            // After completion, captures resolve
            val finished = expectMostRecentItem()
            assertFalse(finished.isCapturing)
            assertFalse(finished.isProcessingPortraitShot)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
