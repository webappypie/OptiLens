package com.webappypie.optilens.core.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposurePriorityTest {

    // Typical indoor auto-exposure without digital boost: ISO 400 at 1/30 s.
    private val indoor = ExposurePriority.AutoExposure(iso = 400, exposureTimeNs = 33_333_333L)

    @Test
    fun `fast shutter raises ISO to keep the metered exposure`() {
        // 1/250 s is ~8.3x shorter, so gain must rise ~8.3x.
        val m = ExposurePriority.forShutter(indoor, targetExposureNs = 4_000_000L, isoRange = 50..6400, boostRange = 100..3199)
        assertEquals(3333, m.iso)
        assertEquals(100, m.postRawBoost)
        assertEquals(4_000_000L, m.exposureTimeNs)
    }

    @Test
    fun `gain beyond the analog maximum goes into post-raw boost`() {
        // Device-observed low light: ISO 130 at 1/50 s with a large auto digital boost.
        val dim = ExposurePriority.AutoExposure(iso = 130, exposureTimeNs = 20_000_000L, postRawBoost = 1600)
        // Needed gain at 1/250 s: 130 * 16 * 5 = 10400 -> ISO 2000 (sensor max) + 5.2x boost.
        val m = ExposurePriority.forShutter(dim, targetExposureNs = 4_000_000L, isoRange = 50..2000, boostRange = 100..3199)
        assertEquals(2000, m.iso)
        assertEquals(520, m.postRawBoost)
    }

    @Test
    fun `scene modes lengthen the shutter instead of exceeding the gain cap`() {
        // Dim room: auto metered ~2560 total gain at 1/50 s; 1/250 s would need ~12800 (> cap 8000).
        val dark = ExposurePriority.AutoExposure(iso = 120, exposureTimeNs = 20_000_000L, postRawBoost = 2133)
        val m = ExposurePriority.forShutter(
            dark, targetExposureNs = 4_000_000L, isoRange = 50..2000, boostRange = 100..3199, maxTotalGain = 8000.0,
        )
        // Shutter lengthened to the fastest the cap allows (~1/156 s), still faster than auto.
        assertEquals(6_399_000L, m.exposureTimeNs)

        // Device-observed: preview AE at 1/14 s in a dim room. Without a bound the cap would pick
        // a shutter slower than plain Photo mode; the 1/60 s bound keeps motion freeze.
        val slowPreview = ExposurePriority.AutoExposure(iso = 2000, exposureTimeNs = 70_000_000L, postRawBoost = 300)
        val m3 = ExposurePriority.forShutter(
            slowPreview, targetExposureNs = 4_000_000L, isoRange = 50..2000, boostRange = 100..3199,
            maxTotalGain = 8000.0, slowestShutterNs = 16_666_667L,
        )
        assertEquals(16_666_667L, m3.exposureTimeNs)
        assertEquals(2000, m3.iso)
        assertEquals(1260, m3.postRawBoost) // gain above the cap is allowed once the shutter bound is hit

        // Moderate light: cap allows a shutter between target and auto.
        val moderate = ExposurePriority.AutoExposure(iso = 400, exposureTimeNs = 33_333_333L)
        val m2 = ExposurePriority.forShutter(
            moderate, targetExposureNs = 1_000_000L, isoRange = 50..2000, boostRange = 100..3199, maxTotalGain = 8000.0,
        )
        assertEquals(1_666_667L, m2.exposureTimeNs) // 400 * 33.33 ms / 8000
        assertEquals(2000, m2.iso)
        assertEquals(400, m2.postRawBoost)
    }

    @Test
    fun `boost is clamped and omitted when unsupported`() {
        val veryDim = ExposurePriority.AutoExposure(iso = 2000, exposureTimeNs = 100_000_000L, postRawBoost = 3000)
        val clamped = ExposurePriority.forShutter(veryDim, 1_000_000L, isoRange = 50..2000, boostRange = 100..3199)
        assertEquals(3199, clamped.postRawBoost)
        val unsupported = ExposurePriority.forShutter(indoor, 1_000_000L, isoRange = 50..3200, boostRange = null)
        assertEquals(3200, unsupported.iso)
        assertEquals(100, unsupported.postRawBoost)
    }

    @Test
    fun `negative EV compensation darkens (moon spot exposure)`() {
        val scale = ExposurePriority.evScale(evIndex = -6, evStep = 1f / 3f) // -2 EV
        assertEquals(0.25, scale, 1e-6)
        val m = ExposurePriority.forShutter(indoor, 33_333_333L, isoRange = 50..6400, boostRange = 100..3199, evScale = scale)
        assertEquals(100, m.iso)
    }

    @Test
    fun `manual ISO derives the matching shutter time including metered boost`() {
        val m = ExposurePriority.forIso(indoor, targetIso = 100, exposureRangeNs = 10_000L..1_000_000_000L)
        assertEquals(133_333_332L, m.exposureTimeNs)
        assertEquals(100, m.postRawBoost)

        val boosted = ExposurePriority.AutoExposure(iso = 100, exposureTimeNs = 10_000_000L, postRawBoost = 400)
        assertEquals(40_000_000L, ExposurePriority.forIso(boosted, 100, null).exposureTimeNs)
    }

    @Test
    fun `shutter time is clamped to the sensor range`() {
        val m = ExposurePriority.forIso(indoor, targetIso = 50, exposureRangeNs = 10_000L..100_000_000L)
        assertEquals(100_000_000L, m.exposureTimeNs)
    }
}
