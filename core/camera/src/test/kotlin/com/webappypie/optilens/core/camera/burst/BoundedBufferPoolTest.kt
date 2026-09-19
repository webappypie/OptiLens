package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.pool.BoundedBufferPool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BoundedBufferPoolTest {

    @Test
    fun `acquire allocates buffer and updates activeAllocations`() {
        val pool = BoundedBufferPool(maxCapacity = 4, defaultBufferSize = 1024)
        assertEquals(0, pool.activeAllocations)

        val buffer = pool.acquire(512)
        assertNotNull(buffer)
        assertEquals(512, buffer.length)
        assertTrue(buffer.data.size >= 512)
        assertEquals(1, pool.activeAllocations)
    }

    @Test
    fun `release returns buffer to pool and decrements activeAllocations`() {
        val pool = BoundedBufferPool(maxCapacity = 4, defaultBufferSize = 1024)
        val buffer = pool.acquire(256)
        assertEquals(1, pool.activeAllocations)

        buffer.release()
        assertEquals(0, pool.activeAllocations)
        assertEquals(1, pool.availableBuffersCount)
    }

    @Test
    fun `re-acquiring reuses released buffer`() {
        val pool = BoundedBufferPool(maxCapacity = 4, defaultBufferSize = 1024)
        val buffer1 = pool.acquire(1024)
        buffer1.data[0] = 42
        buffer1.release()

        val buffer2 = pool.acquire(1024)
        assertEquals(1, pool.activeAllocations)
        assertEquals(0, pool.availableBuffersCount)
        assertEquals(42.toByte(), buffer2.data[0]) // Same backing array reused
        buffer2.release()
        assertEquals(0, pool.activeAllocations)
    }

    @Test
    fun `capacity limit throws IllegalStateException when exhausted`() {
        val pool = BoundedBufferPool(maxCapacity = 3, defaultBufferSize = 1024)
        val b1 = pool.acquire(100)
        val b2 = pool.acquire(100)
        val b3 = pool.acquire(100)
        assertEquals(3, pool.activeAllocations)

        assertThrows(IllegalStateException::class.java) {
            pool.acquire(100)
        }

        b1.release()
        assertEquals(2, pool.activeAllocations)

        val b4 = pool.acquire(100)
        assertEquals(3, pool.activeAllocations)

        b2.release()
        b3.release()
        b4.release()
        assertEquals(0, pool.activeAllocations)
    }

    @Test
    fun `double release is idempotent`() {
        val pool = BoundedBufferPool(maxCapacity = 2, defaultBufferSize = 1024)
        val b = pool.acquire(100)
        assertEquals(1, pool.activeAllocations)

        b.release()
        assertEquals(0, pool.activeAllocations)

        b.release()
        assertEquals(0, pool.activeAllocations)
    }

    @Test
    fun `accessing data after release throws IllegalStateException`() {
        val pool = BoundedBufferPool(maxCapacity = 2, defaultBufferSize = 1024)
        val b = pool.acquire(100)
        b.release()

        assertThrows(IllegalStateException::class.java) {
            b.data[0]
        }
    }

    @Test
    fun `concurrent acquire and release is thread safe`() {
        val pool = BoundedBufferPool(maxCapacity = 16, defaultBufferSize = 1024)
        val threadCount = 8
        val iterations = 50
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until iterations) {
                        val buf = pool.acquire(512)
                        buf.data[0] = (i % 128).toByte()
                        Thread.sleep(1)
                        buf.release()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        val completed = latch.await(5, TimeUnit.SECONDS)
        executor.shutdown()
        assertTrue("Concurrent operations should complete within 5s", completed)
        assertEquals("Active allocations must be 0 after all releases", 0, pool.activeAllocations)
        assertEquals((threadCount * iterations).toLong(), pool.getStats().totalAcquisitionsCount)
    }
}
