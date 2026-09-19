package com.webappypie.optilens

import org.junit.Test
import org.junit.Assert.*

/**
 * Phase 00 unit test — validates that the JVM test runner is configured
 * and that basic assertions work.
 *
 * Real feature unit tests will be added in their respective phase.
 */
class ExampleUnitTest {

    @Test
    fun projectIsInitialized() {
        val projectName = "OptiLens"
        assertEquals("OptiLens", projectName)
    }

    @Test
    fun applicationIdIsCorrect() {
        val applicationId = "com.webappypie.optilens"
        assertTrue(
            "applicationId must match com.webappypie.optilens",
            applicationId == "com.webappypie.optilens"
        )
    }
}
