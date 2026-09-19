package com.webappypie.optilens.core.imaging.alignment

import com.webappypie.optilens.core.camera.burst.model.FramePacket

/**
 * Encapsulates a candidate frame that has undergone scoring and geometric alignment
 * to a chosen reference frame.
 *
 * @param sequenceIndex Position within the original burst sequence.
 * @param isReference True if this frame is the anchor reference for the stack.
 * @param score Frame scoring metrics (sharpness, exposure penalty, motion).
 * @param homography 3x3 projective/affine transform mapping candidate coordinates to reference coordinates.
 * @param alignmentConfidence Quality metric of alignment (0.0 to 1.0, derived from NCC).
 * @param isRejected True if the frame could not be reliably aligned or has severe motion blur.
 * @param rejectionReason Diagnostic string explaining why the frame was rejected.
 * @param ghostMask De-ghosting motion mask identifying non-rigid motion regions.
 * @param packet Underlying frame packet holding the image buffer bytes and Camera2 metadata.
 */
data class AlignedFrame(
    val sequenceIndex: Int,
    val isReference: Boolean,
    val score: FrameScore,
    val homography: HomographyMatrix,
    val alignmentConfidence: Float,
    val isRejected: Boolean,
    val rejectionReason: String? = null,
    val ghostMask: GhostMask? = null,
    val packet: FramePacket,
) : AutoCloseable {

    override fun close() {
        packet.close()
    }
}
