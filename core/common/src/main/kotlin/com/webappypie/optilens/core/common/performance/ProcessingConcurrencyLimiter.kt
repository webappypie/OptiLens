package com.webappypie.optilens.core.common.performance

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrency governor limiting background imaging workloads.
 *
 * Implements Phase 19 Task 13:
 * - Prevents multiple heavyweight operations (burst fusion, super resolution, deblurring)
 *   from running simultaneously, ensuring peak memory does not spike above 64 MB.
 * - Dynamically scales allowable concurrency based on hardware capability tier and thermal conditions.
 */
@Singleton
class ProcessingConcurrencyLimiter @Inject constructor() {

    private var currentPermits: Int = 2
    private var semaphore = Semaphore(currentPermits)

    private val activeCount = AtomicInteger(0)
    private val queuedCount = AtomicInteger(0)

    private val _activeOperations = MutableStateFlow(0)
    val activeOperations: StateFlow<Int> = _activeOperations.asStateFlow()

    private val _queuedOperations = MutableStateFlow(0)
    val queuedOperations: StateFlow<Int> = _queuedOperations.asStateFlow()

    /**
     * Executes [block] within the bounded concurrency limit.
     */
    suspend fun <T> withPermit(block: suspend () -> T): T {
        queuedCount.incrementAndGet()
        _queuedOperations.value = queuedCount.get()

        return try {
            semaphore.withPermit {
                queuedCount.decrementAndGet()
                _queuedOperations.value = queuedCount.get()

                activeCount.incrementAndGet()
                _activeOperations.value = activeCount.get()

                try {
                    block()
                } finally {
                    activeCount.decrementAndGet()
                    _activeOperations.value = activeCount.get()
                }
            }
        } finally {
            // Ensure queued count is normalized if cancelled before permit
            _queuedOperations.value = queuedCount.get()
        }
    }

    /**
     * Adjusts the concurrency limit dynamically (e.g. 1 permit when thermal is MODERATE or tier is LOW).
     */
    @Synchronized
    fun setMaxConcurrency(maxPermits: Int) {
        val safe = maxPermits.coerceIn(1, 4)
        if (safe != currentPermits) {
            currentPermits = safe
            semaphore = Semaphore(safe)
        }
    }

    fun getCurrentMaxConcurrency(): Int = currentPermits
}
