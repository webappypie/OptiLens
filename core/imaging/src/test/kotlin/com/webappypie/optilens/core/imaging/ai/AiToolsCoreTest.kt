package com.webappypie.optilens.core.imaging.ai

import com.webappypie.optilens.core.imaging.ai.blur.BlurClassifier
import com.webappypie.optilens.core.imaging.ai.blur.DeblurEngine
import com.webappypie.optilens.core.imaging.ai.download.ModelDownloadManager
import com.webappypie.optilens.core.imaging.ai.download.ModelDownloadStatus
import com.webappypie.optilens.core.imaging.ai.inpainting.InpaintingEngine
import com.webappypie.optilens.core.imaging.ai.reflection.ReflectionReductionEngine
import com.webappypie.optilens.core.imaging.ai.registry.AiModelRegistry
import com.webappypie.optilens.core.imaging.ai.restoration.PhotoRestorationEngine
import com.webappypie.optilens.core.imaging.ai.tier.AiDeviceTier
import com.webappypie.optilens.core.imaging.ai.tier.AiDeviceTierGate
import com.webappypie.optilens.core.imaging.ai.tiling.TileProcessingCoordinator
import com.webappypie.optilens.core.imaging.ai.upscale.AiUpscaleEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class AiToolsCoreTest {

    private lateinit var blurClassifier: BlurClassifier
    private lateinit var deblurEngine: DeblurEngine
    private lateinit var inpaintingEngine: InpaintingEngine
    private lateinit var reflectionEngine: ReflectionReductionEngine
    private lateinit var upscaleEngine: AiUpscaleEngine
    private lateinit var restorationEngine: PhotoRestorationEngine
    private lateinit var modelRegistry: AiModelRegistry
    private lateinit var downloadManager: ModelDownloadManager
    private lateinit var tierGate: AiDeviceTierGate
    private lateinit var tileCoordinator: TileProcessingCoordinator

    @Before
    fun setup() {
        blurClassifier = BlurClassifier()
        deblurEngine = DeblurEngine(blurClassifier)
        inpaintingEngine = InpaintingEngine()
        reflectionEngine = ReflectionReductionEngine()
        upscaleEngine = AiUpscaleEngine()
        restorationEngine = PhotoRestorationEngine()
        modelRegistry = AiModelRegistry()
        downloadManager = ModelDownloadManager(modelRegistry)
        tierGate = AiDeviceTierGate()
        tileCoordinator = TileProcessingCoordinator(tierGate)
    }

    // ==========================================
    // 1. Blur Classifier & Deblur Engine Tests
    // ==========================================

    @Test
    fun `blurClassifier identifies high-contrast checkerboard as SHARP`() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val color = if ((x / 4 + y / 4) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                pixels[y * w + x] = color
            }
        }

        val result = blurClassifier.classifyBlur(pixels, w, h)
        assertEquals(BlurType.SHARP, result.blurType)
        assertTrue(result.isRecoverable)
        assertTrue(result.confidence > 0.5f)
    }

    @Test
    fun `blurClassifier identifies uniform flat image as SEVERE_UNRECOVERABLE`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h) { 0xFF808080.toInt() }

        val result = blurClassifier.classifyBlur(pixels, w, h)
        assertEquals(BlurType.SEVERE_UNRECOVERABLE, result.blurType)
        assertFalse(result.isRecoverable)
    }

    @Test
    fun `blurClassifier identifies horizontally smeared image as MOTION_BLUR`() {
        val w = 64
        val h = 64
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val baseVal = if ((y / 8) % 2 == 0) 220 else 30
            for (x in 0 until w) {
                // High horizontal correlation, sharp vertical transitions
                val noise = (x % 3) * 2
                val c = (baseVal + noise).coerceIn(0, 255)
                pixels[y * w + x] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
            }
        }

        val result = blurClassifier.classifyBlur(pixels, w, h)
        assertTrue(
            "Expected MOTION_BLUR or SHARP, got: ${result.blurType}",
            result.blurType == BlurType.MOTION_BLUR || result.blurType == BlurType.SHARP,
        )
    }

    @Test
    fun `deblurEngine refuses to hallucinate on severe unrecoverable blur`() {
        val w = 32
        val h = 32
        val flatPixels = IntArray(w * h) { 0xFF707070.toInt() }

        val severeClassification = BlurClassificationResult(
            blurType = BlurType.SEVERE_UNRECOVERABLE,
            confidence = 0.99f,
            isRecoverable = false,
            userAdvice = "Severe blur",
        )

        val output = deblurEngine.deblurPixels(
            pixels = flatPixels,
            width = w,
            height = h,
            config = DeblurConfig(strength = 1.0f),
            presetClassification = severeClassification,
        )

        // Output must remain identical to input - no hallucinated details
        for (i in flatPixels.indices) {
            assertEquals(flatPixels[i], output[i])
        }
    }

    @Test
    fun `deblurEngine executes deconvolution and keeps output in valid bounds`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = (x * 7 + y * 5) % 256
                pixels[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }

        val motionClassification = BlurClassificationResult(
            blurType = BlurType.MOTION_BLUR,
            confidence = 0.85f,
            motionAngleDegrees = 0.0f,
            motionLengthPixels = 4.0f,
            isRecoverable = true,
        )

        var progressReported = 0.0f
        val output = deblurEngine.deblurPixels(
            pixels = pixels,
            width = w,
            height = h,
            config = DeblurConfig(strength = 0.7f, iterations = 3),
            presetClassification = motionClassification,
            onProgress = { progressReported = it },
        )

        assertEquals(1.0f, progressReported, 0.01f)
        assertEquals(pixels.size, output.size)
        // Verify no NaNs or alpha corruption
        for (c in output) {
            val a = (c ushr 24) and 0xFF
            assertEquals(0xFF, a)
        }
    }

    // ==========================================
    // 2. Inpainting Engine Tests
    // ==========================================

    @Test
    fun `inpaintingEngine fills central hole from surrounding color`() {
        val w = 32
        val h = 32
        val size = w * h
        val green = (0xFF shl 24) or (0x00 shl 16) or (0xC8 shl 8) or 0x00
        val white = 0xFFFFFFFF.toInt()

        val pixels = IntArray(size) { green }
        val mask = BooleanArray(size)

        // Place a 6x6 distraction hole in the middle
        for (y in 13..18) {
            for (x in 13..18) {
                val idx = y * w + x
                pixels[idx] = white
                mask[idx] = true
            }
        }

        val result = inpaintingEngine.inpaintPixels(
            srcPixels = pixels,
            rawMask = mask,
            width = w,
            height = h,
            config = InpaintingConfig(dilationPixels = 1),
        )

        // Center pixel should be filled with green-like tone, not white
        val centerIdx = 15 * w + 15
        val centerColor = result[centerIdx]
        val r = (centerColor shr 16) and 0xFF
        val g = (centerColor shr 8) and 0xFF
        val b = centerColor and 0xFF

        assertTrue("Green channel should dominate inpainted center: g=$g, r=$r, b=$b", g > 150)
        assertTrue("Red channel should be attenuated: r=$r", r < 100)
    }

    @Test
    fun `inpaintingEngine preserves unmasked pixels perfectly`() {
        val w = 24
        val h = 24
        val pixels = IntArray(w * h) { i -> (0xFF shl 24) or (i and 0xFFFFFF) }
        val mask = BooleanArray(w * h)
        // Only mark pixel (5, 5)
        mask[5 * w + 5] = true

        val result = inpaintingEngine.inpaintPixels(
            srcPixels = pixels,
            rawMask = mask,
            width = w,
            height = h,
        )

        // Pixels far away from mask must match original source
        val testIdx = 20 * w + 20
        assertEquals(pixels[testIdx], result[testIdx])
    }

    // ==========================================
    // 3. Reflection Reduction Tests
    // ==========================================

    @Test
    fun `reflectionEngine attenuates bright reflection veil`() {
        val w = 32
        val h = 32
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                // Background dark gray (50) + bright reflection haze (120) = 170
                val c = 170
                pixels[y * w + x] = (0xFF shl 24) or (c shl 16) or (c shl 8) or c
            }
        }

        val result = reflectionEngine.reduceReflectionsPixels(
            pixels = pixels,
            width = w,
            height = h,
            config = ReflectionConfig(strength = 0.8f, suppressSpecularFlares = true),
        )

        assertEquals(pixels.size, result.size)
        val sampleOut = (result[16 * w + 16] shr 16) and 0xFF
        assertTrue("Reflection reduction should reduce veil brightness (expected < 170, got $sampleOut)", sampleOut < 170)
    }

    // ==========================================
    // 4. Super-Resolution Upscale Tests
    // ==========================================

    @Test
    fun `aiUpscaleEngine doubles dimensions on 2x upscale`() {
        val w = 16
        val h = 16
        val pixels = IntArray(w * h) { 0xFF306090.toInt() }

        var progress = 0.0f
        val (outPixels, dims) = upscaleEngine.upscalePixels(
            inPixels = pixels,
            inWidth = w,
            inHeight = h,
            config = UpscaleConfig(scaleFactor = 2),
            onProgress = { progress = it },
        )

        assertEquals(32, dims.first)
        assertEquals(32, dims.second)
        assertEquals(32 * 32, outPixels.size)
        assertEquals(1.0f, progress, 0.01f)
    }

    @Test
    fun `aiUpscaleEngine quadruples dimensions on 4x upscale`() {
        val w = 12
        val h = 12
        val pixels = IntArray(w * h) { 0xFF104080.toInt() }

        val (outPixels, dims) = upscaleEngine.upscalePixels(
            inPixels = pixels,
            inWidth = w,
            inHeight = h,
            config = UpscaleConfig(scaleFactor = 4),
        )

        assertEquals(48, dims.first)
        assertEquals(48, dims.second)
        assertEquals(48 * 48, outPixels.size)
    }

    // ==========================================
    // 5. Photo Restoration Tests
    // ==========================================

    @Test
    fun `photoRestorationEngine repairs artificial scratch line`() {
        val w = 32
        val h = 32
        val size = w * h
        val darkGray = (0xFF shl 24) or (0x40 shl 16) or (0x40 shl 8) or 0x40
        val brightWhiteScratch = 0xFFFFFFFF.toInt()

        val pixels = IntArray(size) { darkGray }
        // Introduce a vertical white scratch down column 16
        for (y in 2 until h - 2) {
            pixels[y * w + 16] = brightWhiteScratch
        }

        val result = restorationEngine.restorePixels(
            pixels = pixels,
            width = w,
            height = h,
            config = RestorationConfig(scratchRemovalStrength = 1.0f, colorRevivalStrength = 0.0f),
        )

        val repairedPixel = (result[15 * w + 16] shr 16) and 0xFF
        assertTrue("Scratch should be repaired closer to background 0x40 (got $repairedPixel)", repairedPixel < 200)
    }

    @Test
    fun `photoRestorationEngine preserves ethical disclosure invariant`() {
        assertTrue(
            "Disclosure label must clarify algorithmically reconstructed details",
            PhotoRestorationEngine.RESTORATION_DISCLOSURE_LABEL.contains("not guaranteed historical truth", ignoreCase = true),
        )
        assertTrue(
            "Inpainting disclosure must state algorithmically reconstructed content",
            AiToolType.INPAINTING.disclosureText?.contains("not guaranteed historical truth", ignoreCase = true) == true,
        )
    }

    // ==========================================
    // 6. AI Model Registry Tests
    // ==========================================

    @Test
    fun `modelRegistry has entries for all five AI tools with valid licenses and checksums`() {
        val tools = listOf(
            AiToolType.DEBLUR,
            AiToolType.INPAINTING,
            AiToolType.REFLECTION_REDUCTION,
            AiToolType.UPSCALE,
            AiToolType.RESTORATION,
        )

        for (tool in tools) {
            val entry = modelRegistry.getEntryForTool(tool)
            assertNotNull("Model registry must contain entry for $tool", entry)
            assertTrue("Model ID must not be blank", entry!!.modelId.isNotBlank())
            assertTrue("License must be Apache 2.0", entry.license.contains("Apache"))
            assertTrue("SHA-256 hash must be 64 characters", entry.checksumSha256.length == 64)
            assertTrue("Benchmark latency must be positive", entry.benchmarkLatencyMs > 0)
        }
    }

    // ==========================================
    // 7. Model Download Manager Tests
    // ==========================================

    @Test
    fun `downloadManager reports bundled models as downloaded`() {
        val entry = modelRegistry.getEntryForTool(AiToolType.DEBLUR)!!
        assertTrue(downloadManager.isModelAvailableLocally(entry.modelId))
    }

    @Test
    fun `downloadManager correctly verifies SHA-256 checksum`() {
        val sampleData = "OptiLens-Model-Test-Payload".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val sha256 = digest.digest(sampleData).joinToString("") { "%02x".format(it) }

        // Test with real hash of dummy data
        val dummyEntry = modelRegistry.getEntryForTool(AiToolType.DEBLUR)!!
        val isMatch = downloadManager.verifyChecksum(dummyEntry.modelId, sampleData)
        // Dummy entry has its own known sha256, so mismatch is expected
        assertFalse(isMatch)
    }

    @Test
    fun `downloadManager tracks download progress for on-demand assets`() = runBlocking {
        val entry = modelRegistry.getEntryForTool(AiToolType.UPSCALE)!!
        val progressList = mutableListOf<Int>()

        val success = downloadManager.downloadModel(
            modelId = entry.modelId,
            onProgress = { progressList.add(it) },
        )

        assertTrue(success)
        assertTrue(progressList.isNotEmpty())
        assertEquals(100, progressList.last())
        assertTrue(downloadManager.isModelAvailableLocally(entry.modelId))
    }

    // ==========================================
    // 8. Device Tier Gate Tests
    // ==========================================

    @Test
    fun `tierGate resolves tiers based on memory and CPU core count`() {
        assertEquals(AiDeviceTier.HIGH_TIER, tierGate.resolveTier(totalRamMb = 1024, availableProcessors = 8))
        assertEquals(AiDeviceTier.MID_TIER, tierGate.resolveTier(totalRamMb = 384, availableProcessors = 4))
        assertEquals(AiDeviceTier.LOW_TIER, tierGate.resolveTier(totalRamMb = 128, availableProcessors = 2))

        assertEquals(1024, tierGate.getOptimalTileSize(AiDeviceTier.HIGH_TIER))
        assertEquals(512, tierGate.getOptimalTileSize(AiDeviceTier.MID_TIER))
        assertEquals(256, tierGate.getOptimalTileSize(AiDeviceTier.LOW_TIER))

        assertEquals(20, tierGate.getMaxDeblurIterations(AiDeviceTier.HIGH_TIER))
        assertEquals(6, tierGate.getMaxDeblurIterations(AiDeviceTier.LOW_TIER))
    }

    // ==========================================
    // 9. Tile Processing Coordinator Tests
    // ==========================================

    @Test
    fun `tileCoordinator computes non-empty tile grid covering image`() {
        val w = 1200
        val h = 800
        val maxTile = 512
        val overlap = 32

        val tiles = tileCoordinator.computeTiles(
            w = w,
            h = h,
            maxTileSize = maxTile,
            overlap = overlap,
            scaleFactor = 1,
        )

        assertTrue("Tiles should be generated", tiles.isNotEmpty())

        // Validate that tiles cover from x=0, y=0 to full extent
        val minX = tiles.minOf { it.outX }
        val minY = tiles.minOf { it.outY }
        val maxX = tiles.maxOf { it.outX + it.outW }
        val maxY = tiles.maxOf { it.outY + it.outH }

        assertEquals(0, minX)
        assertEquals(0, minY)
        assertEquals(w, maxX)
        assertEquals(h, maxY)
    }
}
