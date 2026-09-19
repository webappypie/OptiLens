package com.webappypie.optilens.core.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import com.webappypie.optilens.core.common.result.OptiError
import com.webappypie.optilens.core.ui.state.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Base ViewModel providing:
 * - Typed [UiState<S>] management via [_uiState] / [uiState].
 * - Dispatcher-aware [launch] helpers.
 * - Structured error handling via [handleError].
 *
 * @param S The screen-specific UI state data type (the T in UiState<T>).
 */
abstract class BaseViewModel<S>(
    protected val dispatchers: AppDispatchers,
) : ViewModel() {

    private val _uiState: MutableStateFlow<UiState<S>> =
        MutableStateFlow(UiState.Idle)

    /** Observed by the composable screen. */
    val uiState: StateFlow<UiState<S>> = _uiState.asStateFlow()

    /** Convenience: current state value. */
    protected val currentState: UiState<S> get() = _uiState.value

    // ── State update helpers ──────────────────────────────────────────────

    protected fun setLoading(message: String? = null, progress: Float? = null) {
        _uiState.value = UiState.Loading(message, progress)
    }

    protected fun setSuccess(data: S) {
        _uiState.value = UiState.Success(data)
    }

    protected fun setError(error: OptiError, retry: (() -> Unit)? = null) {
        _uiState.value = UiState.Error(error, retry)
    }

    protected fun setIdle() {
        _uiState.value = UiState.Idle
    }

    // ── Coroutine launch helpers ──────────────────────────────────────────

    /** Launches on [AppDispatchers.main] within [viewModelScope]. */
    protected fun launchOnMain(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(dispatchers.main, block = block)

    /** Launches on [AppDispatchers.io] within [viewModelScope]. */
    protected fun launchOnIo(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(dispatchers.io, block = block)

    /** Launches on [AppDispatchers.default] within [viewModelScope]. */
    protected fun launchOnDefault(block: suspend CoroutineScope.() -> Unit) =
        viewModelScope.launch(dispatchers.default, block = block)

    // ── Error handling ────────────────────────────────────────────────────

    /**
     * Wraps [block] in a try-catch. On exception, transitions to [UiState.Error]
     * with the given [retry] lambda. Override to customize error handling.
     */
    protected suspend fun runSafely(
        retry: (() -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: Exception) {
            setError(
                OptiError.Unknown(cause = e, message = e.message ?: "Unexpected error"),
                retry = retry,
            )
        }
    }
}
