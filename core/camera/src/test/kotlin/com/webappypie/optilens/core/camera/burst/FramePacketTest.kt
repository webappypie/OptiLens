package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.BurstResult
import com.webappypie.optilens.core.camera.burst.model.FrameMetadata
import com.webappypie.optilens.core.camera.burst.model.FramePacket
import com.webappypie.optilens.core.camera.burst.model.GyroSample
import com.webappypie.optilens.core.camera.burst.model.GyroWindow
import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacketTest {

    private val pool = BoundedBufferPool(maxCapacity = 4, defaultBufferSize = 1024)

    @Test
    fun `FramePacket holds valid metadata, buffer, and gyro window`() {
        val buffer = pool.acquire(256)
        val metadata = FrameMetadata(
            exposureTimeNs = 16_666_666L, // ~1/60s
            iso = 400,
            focusDistanceDiopters = 2.5f,
            orientationDegrees = 90,
        )
        val gyroWindow = GyroWindow(
            samples = listOf(
                GyroSample(100L, 0.01f, 0.02f, 0.01f),
                GyroSample(200L, 0.02f, 0.01f, 0.02f),
            ),
            startTimestampNs = 100L,
            endTimestampNs = 200L,
        )

        val packet = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 4,
            buffer = buffer,
            metadata = metadata,
            gyroWindow = gyroWindow,
            width = 1920,
            height = 1080,
        )

        assertEquals(0, packet.sequenceIndex)
        assertEquals(4, packet.totalSequenceCount)
        assertEquals(1920, packet.width)
        assertEquals(1080, packet.height)
        assertEquals(400, packet.metadata.iso)
        assertEquals("1/60s", packet.metadata.formattedExposureTime)
        assertFalse(packet.isClosed)
        assertEquals(1, pool.activeAllocations)

        packet.close()
        assertTrue(packet.isClosed)
        assertEquals(0, pool.activeAllocations)
    }

    @Test
    fun `multiple close calls on FramePacket are idempotent`() {
        val buffer = pool.acquire(100)
        val packet = FramePacket(
            sequenceIndex = 0,
            totalSequenceCount = 1,
            buffer = buffer,
            metadata = FrameMetadata(),
        )

        packet.close()
        assertTrue(packet.isClosed)
        assertEquals(0, pool.activeAllocations)

        packet.close()
        assertTrue(packet.isClosed)
        assertEquals(0, pool.activeAllocations)
    }

    @Test
    fun `BurstResult auto-close cascades to all packets`() {
        val b1 = pool.acquire(100)
        val b2 = pool.acquire(100)
        val b3 = pool.acquire(100)
        assertEquals(3, pool.activeAllocations)

        val p1 = FramePacket(0, 3, b1, FrameMetadata())
        val p2 = FramePacket(1, 3, b2, FrameMetadata())
        val p3 = FramePacket(2, 3, b3, FrameMetadata())

        val burstResult = BurstResult(packets = listOf(p1, p2, p3))
        assertEquals(3, burstResult.frameCount)

        burstResult.close()
        assertTrue(p1.isClosed)
        assertTrue(p2.isClosed)
        assertTrue(p3.isClosed)
        assertEquals(0, pool.activeAllocations)
    }

    @Test
    fun `GyroWindow computes correct mean and max angular speed and stability`() {
        val samples = listOf(
            GyroSample(10L, 0.03f, 0.04f, 0f), // speed = 0.05
            GyroSample(20L, 0f, 0.06f, 0.08f), // speed = 0.10
        )
        val window = GyroWindow(samples = samples)
        assertEquals(0.075f, window.meanAngularSpeed, 0.001f)
        assertEquals(0.10f, window.maxAngularSpeed, 0.001f)
        assertTrue("mean 0.075 < 0.08 is stable", window.isStable)

        val unstableWindow = GyroWindow(
            samples = listOf(GyroSample(10L, 0.1f, 0.1f, 0.1f)) // speed ~0.173
        )
        assertFalse(unstableWindow.isStable)
    }

    @Test
    fun `FrameMetadata computes correct photographic formatted exposure time`() {
        val fast = FrameMetadata(exposureTimeNs = 1_000_000L) // 1/1000s
        assertEquals("1/1000s", fast.formattedExposureTime)

        val normal = FrameMetadata(exposureTimeNs = 16_666_666L) // ~1/60s
        assertEquals("1/60s", normal.formattedExposureTime)

        val longExp = FrameMetadata(exposureTimeNs = 2_000_000_000L) // 2.0s
        assertEquals("2.0s", longExp.formattedExposureTime)

        val zero = FrameMetadata(exposureTimeNs = 0L)
        assertEquals("0s", zero.formattedExposureTime)
    }
}
