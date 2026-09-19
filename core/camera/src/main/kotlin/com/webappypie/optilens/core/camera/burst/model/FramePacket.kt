package com.webappypie.optilens.core.camera.burst.model

import com.webappypie.optilens.core.camera.burst.pool.PooledBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Encapsulates a synchronized frame captured during a multi-frame burst sequence,
 * pairing copied image buffer bytes with Camera2 hardware metadata and continuous
 * exposure gyroscope samples.
 *
 * Implements [AutoCloseable] to deterministically return its [buffer] to the
 * pool when frame scoring (Phase 08) or fusion (Phase 09) completes.
 *
 * @param sequenceIndex Zero-based index of this frame within the burst sequence.
 * @param totalSequenceCount Total number of frames requested in the sequence.
 * @param buffer Recycled memory buffer holding frame bytes.
 * @param metadata Optical and sensor exposure metadata from Camera2.
 * @param gyroWindow Angular velocity samples recorded during frame acquisition.
 * @param width Pixel width.
 * @param height Pixel height.
 * @param format Image format (e.g. ImageFormat.JPEG).
 */
class FramePacket(
    val sequenceIndex: Int,
    val totalSequenceCount: Int,
    val buffer: PooledBuffer,
    val metadata: FrameMetadata,
    val gyroWindow: GyroWindow = GyroWindow.EMPTY,
    val width: Int = 4032,
    val height: Int = 3024,
    val format: Int = android.graphics.ImageFormat.JPEG,
) : AutoCloseable {

    private val _isClosed = AtomicBoolean(false)

    val isClosed: Boolean
        get() = _isClosed.get()

    /**
     * Releases the internal [buffer] back to the bounded buffer pool.
     * Safe to invoke multiple times idempotently.
     */
    override fun close() {
        if (_isClosed.compareAndSet(false, true)) {
            buffer.release()
        }
    }
}
