package com.webappypie.optilens.core.camera

import com.webappypie.optilens.core.common.coroutines.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher

@OptIn(ExperimentalCoroutinesApi::class)
class CameraTestDispatchers(
    testDispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : AppDispatchers {
    override val default: CoroutineDispatcher       = testDispatcher
    override val io: CoroutineDispatcher            = testDispatcher
    override val main: CoroutineDispatcher          = testDispatcher
    override val mainImmediate: CoroutineDispatcher = testDispatcher
}
