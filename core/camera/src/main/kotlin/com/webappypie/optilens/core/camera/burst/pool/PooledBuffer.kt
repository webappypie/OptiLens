package com.webappypie.optilens.core.camera.burst.pool

/**
 * Handle to a memory buffer allocated from [BoundedBufferPool].
 *
 * Implements [AutoCloseable] to guarantee buffer recycling when multi-frame
 * processing is finished.
 */
interface PooledBuffer : AutoCloseable {

    /** The underlying byte array holding copied frame data. */
    val data: ByteArray

    /** Number of valid bytes in [data]. */
    val length: Int

    /** Returns this buffer back to the originating pool. */
    fun release()

    override fun close() = release()
}
