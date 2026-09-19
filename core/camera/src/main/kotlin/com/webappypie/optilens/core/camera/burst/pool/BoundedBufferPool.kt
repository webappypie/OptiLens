package com.webappypie.optilens.core.camera.burst.pool

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnostics and health metrics for [BoundedBufferPool].
 */
data class PoolStats(
    val maxCapacity: Int,
    val activeAllocations: Int,
    val availableBuffers: Int,
    val totalAcquisitionsCount: Long,
)

/**
 * Thread-safe, bounded buffer pool preventing out-of-memory errors and HAL buffer
 * queue exhaustion during high-speed multi-frame burst acquisitions.
 *
 * Enforces a strict upper bound on in-flight memory buffers and guarantees recycling.
 */
class BoundedBufferPool(
    val maxCapacity: Int = 12,
    private val defaultBufferSize: Int = 16 * 1024 * 1024, // 16 MB max frame size
) {
    private val availablePool = ConcurrentLinkedQueue<ByteArray>()
    private val activeCount = AtomicInteger(0)
    private var totalAcquisitions = 0L

    val activeAllocations: Int
        get() = activeCount.get()

    val availableBuffersCount: Int
        get() = availablePool.size

    /**
     * Acquires a pooled buffer with at least [requiredSize] capacity.
     *
     * @throws IllegalStateException if pool capacity is exhausted to protect device stability.
     */
    @Synchronized
    fun acquire(requiredSize: Int): PooledBuffer {
        val currentActive = activeCount.get()
        if (currentActive >= maxCapacity) {
            throw IllegalStateException("Buffer pool capacity exhausted ($currentActive/$maxCapacity active). Release buffers before acquiring.")
        }

        // Try to reuse an available pre-allocated buffer
        var buffer = availablePool.poll()
        if (buffer == null || buffer.size < requiredSize) {
            val allocSize = maxOf(defaultBufferSize, requiredSize)
            buffer = ByteArray(allocSize)
        }

        activeCount.incrementAndGet()
        totalAcquisitions++

        return PooledBufferImpl(
            backingArray = buffer,
            validLength = requiredSize,
            onRelease = { returnedArray ->
                availablePool.offer(returnedArray)
                activeCount.decrementAndGet()
            }
        )
    }

    fun getStats(): PoolStats = PoolStats(
        maxCapacity = maxCapacity,
        activeAllocations = activeCount.get(),
        availableBuffers = availablePool.size,
        totalAcquisitionsCount = totalAcquisitions,
    )

    fun clear() {
        availablePool.clear()
    }

    private class PooledBufferImpl(
        private val backingArray: ByteArray,
        private val validLength: Int,
        private val onRelease: (ByteArray) -> Unit,
    ) : PooledBuffer {
        private val isReleased = AtomicBoolean(false)

        override val data: ByteArray
            get() {
                check(!isReleased.get()) { "Attempted to access released PooledBuffer" }
                return backingArray
            }

        override val length: Int
            get() = validLength

        override fun release() {
            if (isReleased.compareAndSet(false, true)) {
                onRelease(backingArray)
            }
        }
    }
}
