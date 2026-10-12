package com.webappypie.optilens.core.camera.storage

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStoreOrientationTest {

    @Test
    fun `front camera mirror flips the buffer horizontally for upright and upside-down frames`() {
        assertEquals(-1f to 1f, MediaStoreSaver.mirrorScaleForOrientation(0))
        assertEquals(-1f to 1f, MediaStoreSaver.mirrorScaleForOrientation(180))
    }

    @Test
    fun `front camera mirror flips the buffer vertically for sideways sensor frames`() {
        // Front sensors are typically mounted at 270 degrees; the displayed image's horizontal
        // axis is the buffer's vertical axis, so a horizontal buffer flip would turn selfies
        // upside down instead of mirroring them.
        assertEquals(1f to -1f, MediaStoreSaver.mirrorScaleForOrientation(90))
        assertEquals(1f to -1f, MediaStoreSaver.mirrorScaleForOrientation(270))
        assertEquals(1f to -1f, MediaStoreSaver.mirrorScaleForOrientation(-90))
    }

    @Test
    fun `exif orientation maps to clockwise rotation degrees`() {
        assertEquals(0, MediaStoreSaver.exifOrientationToDegrees(ExifInterface.ORIENTATION_NORMAL))
        assertEquals(0, MediaStoreSaver.exifOrientationToDegrees(ExifInterface.ORIENTATION_UNDEFINED))
        assertEquals(90, MediaStoreSaver.exifOrientationToDegrees(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(180, MediaStoreSaver.exifOrientationToDegrees(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(270, MediaStoreSaver.exifOrientationToDegrees(ExifInterface.ORIENTATION_ROTATE_270))
    }
}
