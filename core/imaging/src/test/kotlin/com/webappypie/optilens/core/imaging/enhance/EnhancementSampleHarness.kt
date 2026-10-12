package com.webappypie.optilens.core.imaging.enhance

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Developer harness for visual QA on real photos (skipped unless configured):
 * `OPTILENS_ENHANCE_SAMPLES=<dir with .jpg>` and optionally `OPTILENS_ENHANCE_OUT=<dir>`.
 * Writes `<name>_compare.jpg` (original | enhanced, downscaled) and prints timings.
 * Host-JVM timings are not device timings.
 */
class EnhancementSampleHarness {

    @Test
    fun enhanceSamples() {
        val dir = System.getenv("OPTILENS_ENHANCE_SAMPLES")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        val outDir = System.getenv("OPTILENS_ENHANCE_OUT")?.let(::File) ?: File(dir, "out")
        outDir.mkdirs()
        dir!!.listFiles { f -> f.extension.equals("jpg", true) }!!.sorted().forEach { file ->
            val img = ImageIO.read(file)
            val w = img.width
            val h = img.height
            val src = ArgbImage(w, h, img.getRGB(0, 0, w, h, null, 0, w))
            val out = ArgbImage(w, h)
            val report = runBlocking {
                withContext(Dispatchers.Default) { LocalEnhancementPipeline().process(src, out, 4) }
            }
            println("${file.name}: $report")
            val enhanced = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).apply { setRGB(0, 0, w, h, out.pixels, 0, w) }
            ImageIO.write(enhanced, "jpg", File(outDir, "${file.nameWithoutExtension}_enhanced.jpg"))
            val scale = 900.0 / maxOf(w, h)
            val sw = (w * scale).toInt()
            val sh = (h * scale).toInt()
            val cmp = BufferedImage(sw * 2 + 8, sh, BufferedImage.TYPE_INT_RGB)
            val g = cmp.createGraphics()
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(img, 0, 0, sw, sh, null)
            g.drawImage(enhanced, sw + 8, 0, sw, sh, null)
            g.dispose()
            ImageIO.write(cmp, "jpg", File(outDir, "${file.nameWithoutExtension}_compare.jpg"))
        }
    }
}
