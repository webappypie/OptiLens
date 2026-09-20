package com.webappypie.optilens.core.ui.camera

import app.cash.turbine.test
import com.webappypie.optilens.core.camera.FakeCameraController
import com.webappypie.optilens.core.camera.analysis.LensDirtyState
import com.webappypie.optilens.core.camera.bestshot.BestShotCandidate
import com.webappypie.optilens.core.camera.bestshot.BestShotEngine
import com.webappypie.optilens.core.camera.bestshot.BestShotResult
import com.webappypie.optilens.core.camera.document.DocumentColorMode
import com.webappypie.optilens.core.camera.document.DocumentEngine
import com.webappypie.optilens.core.camera.document.DocumentOcrEngine
import com.webappypie.optilens.core.camera.model.CameraMode
import com.webappypie.optilens.core.camera.specialized.FoodModeEngine
import com.webappypie.optilens.core.camera.specialized.PetModeEngine
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraViewModelSpecializedModesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cameraController: FakeCameraController
    private lateinit var viewModel: CameraViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        cameraController = FakeCameraController()
        viewModel = CameraViewModel(
            cameraController = cameraController,
            bestShotEngine = BestShotEngine(),
            petModeEngine = PetModeEngine(),
            foodModeEngine = FoodModeEngine(),
            documentEngine = DocumentEngine(),
            documentOcrEngine = DocumentOcrEngine(),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `switching camera modes updates internal controller and ui state`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(CameraMode.PHOTO, initial.currentMode)

            // Switch to BEST_SHOT
            viewModel.setCameraMode(CameraMode.BEST_SHOT)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CameraMode.BEST_SHOT, awaitItem().currentMode)

            // Switch to PET
            viewModel.setCameraMode(CameraMode.PET)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CameraMode.PET, awaitItem().currentMode)

            // Switch to FOOD
            viewModel.setCameraMode(CameraMode.FOOD)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CameraMode.FOOD, awaitItem().currentMode)

            // Switch to DOCUMENT
            viewModel.setCameraMode(CameraMode.DOCUMENT)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(CameraMode.DOCUMENT, awaitItem().currentMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `document color mode can be switched`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertEquals(DocumentColorMode.COLOR, initial.documentColorMode)

            viewModel.setDocumentColorMode(DocumentColorMode.GRAYSCALE)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(DocumentColorMode.GRAYSCALE, awaitItem().documentColorMode)

            viewModel.setDocumentColorMode(DocumentColorMode.BLACK_AND_WHITE)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(DocumentColorMode.BLACK_AND_WHITE, awaitItem().documentColorMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `lens dirty emissions update UI and dismiss suppresses prompt`() = runTest {
        viewModel.uiState.test {
            awaitItem() // initial

            // Emit dirty lens state
            cameraController.emitLensDirtyState(
                LensDirtyState(isDirty = true, confidence = 0.85f, isDismissed = false)
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val dirtyState = awaitItem()
            assertTrue(dirtyState.lensDirtyState.isDirty)
            assertTrue(dirtyState.lensDirtyState.shouldShowPrompt)

            // User dismisses lens dirty prompt
            viewModel.dismissLensDirtyPrompt()
            testDispatcher.scheduler.advanceUntilIdle()

            val dismissedState = awaitItem()
            assertTrue(dismissedState.lensDirtyState.isDismissed)
            assertFalse(dismissedState.lensDirtyState.shouldShowPrompt)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `extractDocumentOcr is strictly on-demand and extracts decoupled text`() = runTest {
        viewModel.uiState.test {
            val initial = awaitItem()
            assertNull(initial.ocrExtractedText)

            // Capture a photo first so there is a URI
            viewModel.onPermissionResult(true)
            viewModel.setCameraMode(CameraMode.DOCUMENT)
            testDispatcher.scheduler.advanceUntilIdle()

            // Drain intermediate emissions until settled on DOCUMENT
            var current = awaitItem()
            while (current.currentMode != CameraMode.DOCUMENT) {
                current = awaitItem()
            }

            viewModel.takeDocumentPhoto(0)
            testDispatcher.scheduler.advanceUntilIdle()

            // Advance until capture completes and documentScanResult is populated
            while (current.documentScanResult == null) {
                current = awaitItem()
            }

            assertNotNull(current.documentScanResult)
            assertNull("OCR text must remain null during capture", current.ocrExtractedText)

            // Trigger on-demand OCR extraction
            viewModel.extractDocumentOcr()
            testDispatcher.scheduler.advanceUntilIdle()

            while (current.ocrExtractedText == null) {
                current = awaitItem()
            }

            val text = current.ocrExtractedText
            assertNotNull(text)
            assertTrue(text.contains("OptiLens Document OCR Text"))
            assertTrue(text.contains("Decoupled On-Demand Recognition"))

            cancelAndIgnoreRemainingEvents()
        }
    }
}
