package com.webappypie.optilens

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 00 instrumented test — validates that the app launches on device/emulator
 * and the package name is correct.
 *
 * Real instrumented tests will be added as features are implemented.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun usesCorrectApplicationId() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.webappypie.optilens.debug", appContext.packageName)
    }
}
