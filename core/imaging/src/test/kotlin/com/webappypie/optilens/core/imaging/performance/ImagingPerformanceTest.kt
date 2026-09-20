package com.webappypie.optilens.core.imaging.performance

import android.graphics.Bitmap
import com.webappypie.optilens.core.logging.NoOpLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ImagingPerformanceTest {

    private lateinit var bitmapPool: BitmapPool
    private lateinit var delegateBenchmark: InferenceDelegateBenchmark

    companion object {
        fun createDummyBitmap(): Bitmap {
            val field = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
            field.isAccessible = true
            val unsafe = field.get(null) as sun.misc.Unsafe
            return unsafe.allocateInstance(Bitmap::class.java) as Bitmap
        }
    }

    @Before
    fun setUp() {
        bitmapPool = BitmapPool(logger = NoOpLogger())
        delegateBenchmark = InferenceDelegateBenchmark(logger = NoOpLogger())
    }

    // ==========================================
    // 1. BitmapPool Tests
    // ==========================================

    @Test
    fun `acquire on empty pool registers miss and returns null`() {
        val bitmap = bitmapPool.acquire(512, 512)
        assertNull(bitmap)

        val metrics = bitmapPool.getMetrics()
        assertEquals(0L, metrics.hitCount)
        assertEquals(1L, metrics.missCount)
        assertEquals(0.0f, metrics.hitRatePercent, 0.01f)
    }

    @Test
    fun `release and acquire returns pooled bitmap and tracks hit`() {
        val dummy = createDummyBitmap()
        bitmapPool.release(dummy, explicitWidth = 512, explicitHeight = 512)

        val metricsAfterRelease = bitmapPool.getMetrics()
        assertEquals(1, metricsAfterRelease.pooledCount)
        assertTrue(metricsAfterRelease.currentSizeBytes > 0L)

        val retrieved = bitmapPool.acquire(512, 512)
        assertNotNull(retrieved)
        assertEquals(dummy, retrieved)

        val metricsAfterAcquire = bitmapPool.getMetrics()
        assertEquals(1L, metricsAfterAcquire.hitCount)
        assertEquals(0L, metricsAfterAcquire.missCount)
        assertEquals(100.0f, metricsAfterAcquire.hitRatePercent, 0.01f)
        assertEquals(0, metricsAfterAcquire.pooledCount)
    }

    @Test
    fun `pool trims and evicts on memory pressure`() {
        // Set max pool capacity to 5 MB
        bitmapPool.setMaxSize(5L * 1024L * 1024L)

        // 1024x1024 ARGB_8888 is 4 MB
        val bmp1 = createDummyBitmap()
        val bmp2 = createDummyBitmap()

        bitmapPool.release(bmp1, explicitWidth = 1024, explicitHeight = 1024)
        assertEquals(1, bitmapPool.getMetrics().pooledCount)

        // Releasing second 4MB bitmap exceeds 5MB, so oldest should be evicted
        bitmapPool.release(bmp2, explicitWidth = 1024, explicitHeight = 1024)
        val metrics = bitmapPool.getMetrics()
        assertEquals(1L, metrics.evictionCount)
        assertEquals(1, metrics.pooledCount)

        // Test trim(15) flushes all
        bitmapPool.trim(15)
        assertEquals(0, bitmapPool.getMetrics().pooledCount)
        assertEquals(0L, bitmapPool.getMetrics().currentSizeBytes)
    }

    // ==========================================
    // 2. InferenceDelegateBenchmark Tests
    // ==========================================

    @Test
    fun `benchmarkAll measures CPU GPU and NNAPI delegates`() {
        val results = delegateBenchmark.benchmarkAll(isNpuAvailable = true, isGpuAvailable = true)
        assertEquals(3, results.size)

        val cpu = results.find { it.delegate == InferenceDelegate.CPU_MULTITHREAD }
        val gpu = results.find { it.delegate == InferenceDelegate.GPU_COMPUTE }
        val npu = results.find { it.delegate == InferenceDelegate.NNAPI }

        assertNotNull(cpu)
        assertNotNull(gpu)
        assertNotNull(npu)

        assertTrue(cpu!!.latencyMs >= 0L)
        assertTrue(gpu!!.latencyMs >= 0L)
        assertTrue(npu!!.latencyMs >= 0L)
    }

    @Test
    fun `selectOptimalDelegate prioritizes thermal efficiency when device is hot`() {
        val results = listOf(
            DelegateBenchmarkResult(InferenceDelegate.CPU_MULTITHREAD, latencyMs = 50L, throughputMpxPerSec = 5f, thermalImpactScore = 1.2f, isSupportedOnDevice = true),
            DelegateBenchmarkResult(InferenceDelegate.GPU_COMPUTE, latencyMs = 20L, throughputMpxPerSec = 12f, thermalImpactScore = 2.0f, isSupportedOnDevice = true),
            DelegateBenchmarkResult(InferenceDelegate.NNAPI, latencyMs = 25L, throughputMpxPerSec = 10f, thermalImpactScore = 0.8f, isSupportedOnDevice = true),
        )

        // Under normal thermal state: GPU has lowest latency (20ms)
        val normalOptimal = delegateBenchmark.selectOptimalDelegate(results, isDeviceHot = false)
        assertEquals(InferenceDelegate.GPU_COMPUTE, normalOptimal)

        // Under hot thermal state: NNAPI has lowest thermal impact score (0.8f)
        val hotOptimal = delegateBenchmark.selectOptimalDelegate(results, isDeviceHot = true)
        assertEquals(InferenceDelegate.NNAPI, hotOptimal)
    }
}
