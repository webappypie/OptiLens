package com.webappypie.optilens.core.camera.tracking

import com.webappypie.optilens.core.camera.analysis.PreprocessedFrameData
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState
import com.webappypie.optilens.core.camera.thermal.ThermalDegradationPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RealtimeObjectTrackerTest {

    private lateinit var tracker: RealtimeObjectTracker

    @Before
    fun setUp() {
        tracker = RealtimeObjectTracker()
    }

    private fun createFrameWithTarget(
        targetX: Int,
        targetY: Int,
        targetW: Int = 10,
        targetH: Int = 10,
        gridW: Int = 80,
        gridH: Int = 60,
        targetLuma: Int = 220,
        backgroundLuma: Int = 40,
        timestampMs: Long = System.currentTimeMillis(),
    ): PreprocessedFrameData {
        val yGrid = IntArray(gridW * gridH) { backgroundLuma }

        for (y in targetY until (targetY + targetH).coerceAtMost(gridH)) {
            val row = y * gridW
            for (x in targetX until (targetX + targetW).coerceAtMost(gridW)) {
                yGrid[row + x] = targetLuma
            }
        }

        return PreprocessedFrameData(
            gridWidth = gridW,
            gridHeight = gridH,
            yGrid = yGrid,
            histogramBins = FloatArray(64),
            meanLuminance = 50f,
            centerLuminance = 50f,
            peripheryLuminance = 40f,
            highlightClippingPercent = 1f,
            shadowClippingPercent = 10f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0f,
            plantScore = 0f,
            warmScore = 0f,
            timestampMs = timestampMs,
        )
    }

    @Test
    fun `startTracking initializes bounds centered at tap coordinate`() {
        tracker.startTracking(0.5f, 0.4f, boxSizeFraction = 0.20f)

        val state = tracker.getCurrentState()
        assertEquals(TrackingStatus.INITIALIZING, state.status)
        val bounds = state.bounds
        assertTrue(bounds != null)
        assertEquals(0.5f, bounds!!.centerX, 0.02f)
        assertEquals(0.4f, bounds.centerY, 0.02f)
    }

    @Test
    fun `tracker locks on target and tracks translation across frames`() {
        val gridW = 80
        val gridH = 60

        // 1. Initial Frame: Target at (30, 20) -> tap at (35/80 = 0.4375, 25/60 = 0.4167)
        val f1 = createFrameWithTarget(targetX = 30, targetY = 20, gridW = gridW, gridH = gridH, timestampMs = 1000L)
        tracker.startTracking(normTapX = 35f / gridW.toFloat(), normTapY = 25f / gridH.toFloat(), boxSizeFraction = 0.15f)

        val s1 = tracker.update(f1)
        assertEquals(TrackingStatus.TRACKING, s1.status)
        assertTrue("Confidence must be 1.0 at initialization", s1.confidence >= 0.95f)

        // 2. Second Frame: Target translates slightly to (32, 21) at 1066ms (+66ms ~15fps)
        val f2 = createFrameWithTarget(targetX = 32, targetY = 21, gridW = gridW, gridH = gridH, timestampMs = 1066L)
        val s2 = tracker.update(f2)

        assertEquals(TrackingStatus.TRACKING, s2.status)
        assertTrue("Must track confident match", s2.confidence >= 0.70f)
        assertTrue("X center must advance rightward", (s2.bounds?.centerX ?: 0f) >= (s1.bounds?.centerX ?: 0f))
    }

    @Test
    fun `short occlusion transitions to OCCLUDED and recovers when target reappears`() {
        val gridW = 80
        val gridH = 60

        // Init
        val f1 = createFrameWithTarget(targetX = 30, targetY = 20, gridW = gridW, gridH = gridH, timestampMs = 1000L)
        tracker.startTracking(35f / gridW.toFloat(), 25f / gridH.toFloat(), 0.15f)
        tracker.update(f1)

        // Frame 2: Target occluded (completely dark background) at 1066ms
        val fOccluded = PreprocessedFrameData(
            gridWidth = gridW,
            gridHeight = gridH,
            yGrid = IntArray(gridW * gridH) { 40 },
            histogramBins = FloatArray(64),
            meanLuminance = 40f,
            centerLuminance = 40f,
            peripheryLuminance = 40f,
            highlightClippingPercent = 0f,
            shadowClippingPercent = 50f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0f,
            plantScore = 0f,
            warmScore = 0f,
            timestampMs = 1066L,
        )
        val sOccluded = tracker.update(fOccluded)
        assertEquals(TrackingStatus.OCCLUDED, sOccluded.status)
        assertTrue(sOccluded.status.isVisualActive)

        // Frame 3: Target reappears at (31, 20) at 1200ms (within 1200ms timeout)
        val fRecovered = createFrameWithTarget(targetX = 31, targetY = 20, gridW = gridW, gridH = gridH, timestampMs = 1200L)
        val sRecovered = tracker.update(fRecovered)

        assertEquals(TrackingStatus.TRACKING, sRecovered.status)
        assertTrue("Re-acquired target must restore high confidence", sRecovered.confidence >= 0.70f)
        assertEquals(0L, sRecovered.occlusionDurationMs)
    }

    @Test
    fun `long occlusion exceeding timeout transitions to LOST`() {
        val gridW = 80
        val gridH = 60

        // Init
        val f1 = createFrameWithTarget(targetX = 30, targetY = 20, gridW = gridW, gridH = gridH, timestampMs = 1000L)
        tracker.startTracking(35f / gridW.toFloat(), 25f / gridH.toFloat(), 0.15f)
        tracker.update(f1)

        // Empty frame after 1500ms (> 1200ms timeout)
        val fLost = PreprocessedFrameData(
            gridWidth = gridW,
            gridHeight = gridH,
            yGrid = IntArray(gridW * gridH) { 40 },
            histogramBins = FloatArray(64),
            meanLuminance = 40f,
            centerLuminance = 40f,
            peripheryLuminance = 40f,
            highlightClippingPercent = 0f,
            shadowClippingPercent = 50f,
            averageU = 128f,
            averageV = 128f,
            skyScore = 0f,
            plantScore = 0f,
            warmScore = 0f,
            timestampMs = 2500L,
        )
        val sLost = tracker.update(fLost)
        assertEquals(TrackingStatus.LOST, sLost.status)
        assertFalse(sLost.status.isVisualActive)
    }

    @Test
    fun `severe or critical thermal state gracefully disables tracking`() {
        tracker.startTracking(0.5f, 0.5f)
        val f1 = createFrameWithTarget(targetX = 35, targetY = 25, timestampMs = 1000L)

        val severePolicy = ThermalDegradationPolicy.forThermalState(DeviceThermalState.SEVERE)
        val state = tracker.update(f1, severePolicy)

        assertEquals(TrackingStatus.DISABLED_THERMAL, state.status)
        assertEquals(0.0f, state.confidence, 0.001f)
    }

    @Test
    fun `stopTracking resets tracker to INACTIVE`() {
        tracker.startTracking(0.5f, 0.5f)
        tracker.stopTracking()

        val state = tracker.getCurrentState()
        assertEquals(TrackingStatus.INACTIVE, state.status)
    }
}
