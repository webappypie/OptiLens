package com.webappypie.optilens.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

/**
 * Abstraction over coroutine dispatchers.
 *
 * Injected throughout the app so that tests can substitute
 * [TestAppDispatchers] / [UnconfinedTestDispatcher] without touching
 * production code.
 */
interface AppDispatchers {
    /** CPU-bound work (image processing, sorting, encoding). */
    val default: CoroutineDispatcher

    /** I/O-bound work (DataStore reads/writes, file I/O). */
    val io: CoroutineDispatcher

    /** Compose / UI thread. */
    val main: CoroutineDispatcher

    /** Main thread, but immediate if already on main — safe for Compose state updates. */
    val mainImmediate: CoroutineDispatcher
}

/**
 * Production implementation backed by [Dispatchers].
 */
class DefaultAppDispatchers @Inject constructor() : AppDispatchers {
    override val default: CoroutineDispatcher        = Dispatchers.Default
    override val io: CoroutineDispatcher             = Dispatchers.IO
    override val main: CoroutineDispatcher           = Dispatchers.Main
    override val mainImmediate: CoroutineDispatcher  = Dispatchers.Main.immediate
}
