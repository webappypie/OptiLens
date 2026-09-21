package com.webappypie.optilens.core.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GrowthAnalyticsTest {

    private val loggedEvents = mutableListOf<String>()
    private lateinit var analytics: DefaultGrowthAnalytics

    private val testLogger = object : AppLogger {
        override fun v(tag: String, message: String) { loggedEvents.add("V:$tag:$message") }
        override fun d(tag: String, message: String) { loggedEvents.add("D:$tag:$message") }
        override fun i(tag: String, message: String) { loggedEvents.add("I:$tag:$message") }
        override fun w(tag: String, message: String, cause: Throwable?) { loggedEvents.add("W:$tag:$message") }
        override fun e(tag: String, message: String, cause: Throwable?) { loggedEvents.add("E:$tag:$message") }
        override fun perf(tag: String, event: String, millis: Long) { loggedEvents.add("P:$tag:$event:$millis") }
    }

    @Before
    fun setUp() {
        loggedEvents.clear()
        analytics = DefaultGrowthAnalytics(testLogger)
    }

    @Test
    fun `device cohort correctly buckets hardware without exposing identifiers`() {
        val cohortSnapdragon = DeviceCohortMetadata.current(
            apiLevel = 34,
            manufacturer = "Samsung",
            hardware = "qcom",
            totalRamGb = 12,
            hardwareLevel = "LEVEL_3",
            quirkCount = 2,
        )

        assertEquals("Snapdragon", cohortSnapdragon.socFamily)
        assertEquals("12GB", cohortSnapdragon.ramBucket)
        assertEquals("Samsung", cohortSnapdragon.manufacturerBucket)
        assertEquals("LEVEL_3", cohortSnapdragon.hardwareLevel)
        assertEquals(2, cohortSnapdragon.quirkCount)

        val cohortTensor = DeviceCohortMetadata.current(
            apiLevel = 34,
            manufacturer = "Google",
            hardware = "tensor_g3",
            totalRamGb = 8,
        )
        assertEquals("Tensor", cohortTensor.socFamily)
        assertEquals("Google", cohortTensor.manufacturerBucket)
        assertEquals("8GB", cohortTensor.ramBucket)
    }

    @Test
    fun `activation event logs non-sensitive cohort data`() {
        val cohort = DeviceCohortMetadata.current(
            apiLevel = 34,
            manufacturer = "Google",
            hardware = "tensor_g3",
            totalRamGb = 8,
            hardwareLevel = "LEVEL_3",
            quirkCount = 1,
        )

        analytics.trackAppActivated(isFirstLaunch = true, cohort = cohort)

        assertEquals(1, loggedEvents.size)
        assertTrue(loggedEvents[0].contains("app_activated"))
        assertTrue(loggedEvents[0].contains("firstLaunch=true"))
        assertTrue(loggedEvents[0].contains("soc=Tensor"))
        assertTrue(loggedEvents[0].contains("ram=8GB"))
    }

    @Test
    fun `capture success and camera ready log expected metrics`() {
        analytics.trackCameraReady(warmupMs = 180, mode = "AI_AUTO")
        analytics.trackCaptureSuccess(mode = "NIGHT", durationMs = 450, burstCount = 6)

        assertEquals(2, loggedEvents.size)
        assertTrue(loggedEvents[0].contains("camera_ready"))
        assertTrue(loggedEvents[0].contains("warmupMs=180"))
        assertTrue(loggedEvents[1].contains("capture_success"))
        assertTrue(loggedEvents[1].contains("burstCount=6"))
    }

    @Test
    fun `ai enhance decision tracks keep and discard without image data`() {
        analytics.trackAiEnhanceDecision(kept = true, sliderPosition = 0.5f, processingMs = 320)
        analytics.trackAiEnhanceDecision(kept = false, sliderPosition = 0.2f, processingMs = 280)

        assertEquals(2, loggedEvents.size)
        assertTrue(loggedEvents[0].contains("decision=KEEP"))
        assertTrue(loggedEvents[1].contains("decision=DISCARD"))

        // Guarantee zero image payload or file path in logs
        for (event in loggedEvents) {
            assertFalse(event.contains(".jpg"))
            assertFalse(event.contains(".dng"))
            assertFalse(event.contains("content://"))
        }
    }

    @Test
    fun `purchase funnel tracks stages without financial or personal credentials`() {
        analytics.trackPurchaseFunnel(PurchaseFunnelStage.PAYWALL_VIEWED, productId = "optilens_pro_lifetime")
        analytics.trackPurchaseFunnel(PurchaseFunnelStage.PURCHASE_COMPLETED, productId = "optilens_pro_lifetime")

        assertEquals(2, loggedEvents.size)
        assertTrue(loggedEvents[0].contains("PAYWALL_VIEWED"))
        assertTrue(loggedEvents[1].contains("PURCHASE_COMPLETED"))
    }

    @Test
    fun `retention session tracks milestone days`() {
        analytics.trackRetentionSession(sessionIndex = 5, daysSinceInstall = 3)

        assertEquals(1, loggedEvents.size)
        assertTrue(loggedEvents[0].contains("sessionIndex=5"))
        assertTrue(loggedEvents[0].contains("daysSinceInstall=3"))
    }
}
