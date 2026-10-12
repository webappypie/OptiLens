package com.webappypie.optilens.core.ui.review

import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoDecodeMathTest {

    @Test
    fun `typical 12MP photo is decoded at full resolution`() {
        assertEquals(1, PhotoDecodeMath.inSampleSize(4000, 3000))
        assertEquals(1f, PhotoDecodeMath.residualScale(4000, 3000), 0f)
    }

    @Test
    fun `very large photos are sampled down so the long edge stays bounded`() {
        // 108 MP (12000 x 9000): sample 2 -> 6000 x 4500, then exact scale to 4096.
        val sample = PhotoDecodeMath.inSampleSize(12000, 9000)
        assertEquals(2, sample)
        val scale = PhotoDecodeMath.residualScale(12000 / sample, 9000 / sample)
        assertEquals(4096, Math.round(6000 * scale))
        assertTrue(Math.round(4500 * scale) <= 4096)
    }

    @Test
    fun `portrait orientation photos are bounded on their long edge`() {
        val sample = PhotoDecodeMath.inSampleSize(6000, 8000)
        val scale = PhotoDecodeMath.residualScale(6000 / sample, 8000 / sample)
        assertEquals(4096, Math.round(8000 / sample * scale))
    }

    @Test
    fun `exif rotations map to upright transforms`() {
        assertTrue(PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_NORMAL).isIdentity)
        assertTrue(PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_UNDEFINED).isIdentity)
        assertEquals(PhotoDecodeMath.Transform(90, false), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(PhotoDecodeMath.Transform(180, false), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(PhotoDecodeMath.Transform(270, false), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun `exif mirrored orientations include a horizontal flip`() {
        assertEquals(PhotoDecodeMath.Transform(0, true), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
        assertEquals(PhotoDecodeMath.Transform(180, true), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_FLIP_VERTICAL))
        assertEquals(PhotoDecodeMath.Transform(90, true), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_TRANSPOSE))
        assertEquals(PhotoDecodeMath.Transform(270, true), PhotoDecodeMath.exifTransform(ExifInterface.ORIENTATION_TRANSVERSE))
    }
}
