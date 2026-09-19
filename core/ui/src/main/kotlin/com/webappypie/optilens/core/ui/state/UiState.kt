package com.webappypie.optilens.core.ui.state

import com.webappypie.optilens.core.common.result.OptiError

/**
 * Immutable UI state pattern.
 *
 * ViewModels expose a [UiState<T>] as a StateFlow. The UI observes it
 * and renders based on the current variant — never mutable side-effects.
 *
 * @param T The data type for the [Success] case.
 */
sealed class UiState<out T> {

    /** No operation in progress; screen is idle. */
    data object Idle : UiState<Nothing>()

    /** An operation is in progress. [message] is optional user-visible text. */
    data class Loading(
        val message: String? = null,
        val progress: Float? = null,   // 0.0–1.0 if deterministic
    ) : UiState<Nothing>()

    /** Data is available and the screen should render it. */
    data class Success<T>(val data: T) : UiState<T>()

    /** An error occurred. [error] provides structured detail; [retry] is optional. */
    data class Error(
        val error: OptiError,
        val retry: (() -> Unit)? = null,
    ) : UiState<Nothing>()
}

/** True when the UI should show a loading indicator. */
val UiState<*>.isLoading: Boolean get() = this is UiState.Loading

/** True when a terminal error is present. */
val UiState<*>.isError: Boolean get() = this is UiState.Error

/** Returns data if in [UiState.Success], otherwise null. */
fun <T> UiState<T>.getOrNull(): T? = (this as? UiState.Success)?.data

/** Maps the success data type; other states pass through. */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Loading -> this
    is UiState.Error   -> this
    is UiState.Idle    -> this
}
