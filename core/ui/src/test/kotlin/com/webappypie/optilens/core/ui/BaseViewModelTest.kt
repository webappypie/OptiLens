package com.webappypie.optilens.core.ui

import app.cash.turbine.test
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.ui.state.UiState
import com.webappypie.optilens.core.ui.state.isError
import com.webappypie.optilens.core.ui.state.isLoading
import com.webappypie.optilens.core.ui.viewmodel.BaseViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val dispatchers = object : AppDispatchers {
        override val default: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
        override val mainImmediate: CoroutineDispatcher = testDispatcher
    }

    private class TestViewModel(dispatchers: AppDispatchers) : BaseViewModel<String>(dispatchers) {
        fun load() {
            setLoading("Fetching...", 0.5f)
        }

        fun succeed(data: String) {
            setSuccess(data)
        }

        fun fail(msg: String) {
            setError(OptiError.Unknown(message = msg))
        }

        fun reset() {
            setIdle()
        }

        fun runSafeOperation(throwError: Boolean) {
            launchOnMain {
                runSafely {
                    if (throwError) throw IllegalStateException("Boom")
                    setSuccess("Safe success")
                }
            }
        }
    }

    @Test
    fun `initial state is Idle`() = runTest {
        val viewModel = TestViewModel(dispatchers)
        assertEquals(UiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `state transitions through loading, success, error, and idle`() = runTest {
        val viewModel = TestViewModel(dispatchers)

        viewModel.uiState.test {
            assertEquals(UiState.Idle, awaitItem())

            viewModel.load()
            val loading = awaitItem()
            assertTrue(loading.isLoading)
            assertEquals("Fetching...", (loading as UiState.Loading).message)

            viewModel.succeed("Ready")
            val success = awaitItem()
            assertEquals("Ready", (success as UiState.Success).data)

            viewModel.fail("Failed!")
            val error = awaitItem()
            assertTrue(error.isError)

            viewModel.reset()
            assertEquals(UiState.Idle, awaitItem())
        }
    }

    @Test
    fun `runSafely catches exceptions and sets Error state`() = runTest {
        val viewModel = TestViewModel(dispatchers)

        viewModel.runSafeOperation(throwError = true)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Error)
        assertEquals("Boom", (state as UiState.Error).error.displayMessage)
    }

    @Test
    fun `runSafely sets success when no exception occurs`() = runTest {
        val viewModel = TestViewModel(dispatchers)

        viewModel.runSafeOperation(throwError = false)
        val state = viewModel.uiState.value
        assertTrue(state is UiState.Success)
        assertEquals("Safe success", (state as UiState.Success).data)
    }
}
