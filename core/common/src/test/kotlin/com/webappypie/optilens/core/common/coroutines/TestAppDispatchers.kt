package com.webappypie.optilens.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Test implementation of [AppDispatchers] that uses [UnconfinedTestDispatcher]
 * for all dispatchers, enabling synchronous coroutine execution in unit tests.
 *
 * Usage:
 * ```kotlin
 * private val dispatchers = TestAppDispatchers()
 * // inject into ViewModel / UseCase under test
 * ```
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestAppDispatchers(
    testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : AppDispatchers {
    override val default: CoroutineDispatcher       = testDispatcher
    override val io: CoroutineDispatcher            = testDispatcher
    override val main: CoroutineDispatcher          = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
}
