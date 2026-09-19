package com.webappypie.optilens.core.common.result

/**
 * Typed result wrapper used throughout OptiLens to propagate success,
 * loading, and failure states without throwing exceptions across
 * architectural layers.
 *
 * Usage:
 * ```kotlin
 * val result: OptiResult<Photo> = repository.capture()
 * result
 *     .onSuccess { photo -> showPreview(photo) }
 *     .onError   { e    -> showError(e.message) }
 * ```
 */
sealed class OptiResult<out T> {

    /** Operation completed and produced a value. */
    data class Success<T>(val data: T) : OptiResult<T>()

    /** Operation is in progress. Carries optional progress [fraction] 0.0–1.0. */
    data class Loading(val fraction: Float? = null) : OptiResult<Nothing>()

    /** Operation failed with a structured [error]. */
    data class Error(val error: OptiError) : OptiResult<Nothing>()
}

/** Runs [block] if this is [OptiResult.Success]. Returns `this` for chaining. */
inline fun <T> OptiResult<T>.onSuccess(block: (T) -> Unit): OptiResult<T> {
    if (this is OptiResult.Success) block(data)
    return this
}

/** Runs [block] if this is [OptiResult.Error]. Returns `this` for chaining. */
inline fun <T> OptiResult<T>.onError(block: (OptiError) -> Unit): OptiResult<T> {
    if (this is OptiResult.Error) block(error)
    return this
}

/** Runs [block] if this is [OptiResult.Loading]. Returns `this` for chaining. */
inline fun <T> OptiResult<T>.onLoading(block: (Float?) -> Unit): OptiResult<T> {
    if (this is OptiResult.Loading) block(fraction)
    return this
}

/** Maps the success value; error/loading pass through unchanged. */
inline fun <T, R> OptiResult<T>.map(transform: (T) -> R): OptiResult<R> = when (this) {
    is OptiResult.Success -> OptiResult.Success(transform(data))
    is OptiResult.Loading -> this
    is OptiResult.Error   -> this
}

/** Returns the success value or null. */
fun <T> OptiResult<T>.getOrNull(): T? = (this as? OptiResult.Success)?.data

/** Returns true when this result represents a terminal failure. */
val OptiResult<*>.isError: Boolean get() = this is OptiResult.Error

/** Returns true when work is still in progress. */
val OptiResult<*>.isLoading: Boolean get() = this is OptiResult.Loading
