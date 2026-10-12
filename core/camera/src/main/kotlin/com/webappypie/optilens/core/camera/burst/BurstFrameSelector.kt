package com.webappypie.optilens.core.camera.burst

import com.webappypie.optilens.core.camera.burst.model.FramePacket

/**
 * Picks a single real captured frame from a burst to persist when no multi-frame
 * fusion is applied to that burst.
 *
 * Selection: among open frames whose bracketing EV offset is 0 (or all open frames when
 * none are at EV 0), choose the one with the lowest mean gyro angular speed recorded during
 * its exposure — i.e. the frame least likely to contain hand-shake blur. Ties keep the
 * earliest frame.
 */
object BurstFrameSelector {

    /**
     * @param packets Burst frames in capture order.
     * @param evOffsets The EV offsets used for the burst; frame `i` was captured at
     *   `evOffsets[i % evOffsets.size]`. Empty means every frame was at EV 0.
     * @return The selected frame, or null when no open frame is available.
     */
    fun selectSteadiest(packets: List<FramePacket>, evOffsets: List<Int> = emptyList()): FramePacket? {
        val open = packets.filterNot { it.isClosed }
        if (open.isEmpty()) return null

        val atBaseExposure = if (evOffsets.isEmpty()) {
            open
        } else {
            open.filter { evOffsets[it.sequenceIndex % evOffsets.size] == 0 }
        }
        val candidates = atBaseExposure.ifEmpty { open }

        return candidates.minWithOrNull(
            compareBy<FramePacket> { it.gyroWindow.meanAngularSpeed }.thenBy { it.sequenceIndex }
        )
    }
}
