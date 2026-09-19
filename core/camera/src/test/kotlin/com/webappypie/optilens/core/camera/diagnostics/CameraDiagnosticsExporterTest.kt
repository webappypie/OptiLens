package com.webappypie.optilens.core.camera.diagnostics

import com.webappypie.optilens.core.camera.fixtures.CameraCapabilityFixtures
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CameraDiagnosticsExporterTest {

    private lateinit var exporter: CameraDiagnosticsExporter

    @Before
    fun setUp() {
        exporter = CameraDiagnosticsExporter()
    }

    @Test
    fun `exportToJson outputs valid json with expected keys`() {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        val json = exporter.exportToJson(profile)

        assertTrue(json.contains("\"deviceModel\": \"Pixel 7 Pro\""))
        assertTrue(json.contains("\"performanceTier\": \"FLAGSHIP\""))
        assertTrue(json.contains("\"hardwareLevel\": \"LEVEL_3\""))
        assertTrue(json.contains("\"supportsRaw\": true"))
    }

    @Test
    fun `exportToMarkdown outputs formatted markdown report`() {
        val profile = CameraCapabilityFixtures.createFlagshipProfile()
        val markdown = exporter.exportToMarkdown(profile)

        assertTrue(markdown.contains("# OptiLens Camera Hardware Diagnostics"))
        assertTrue(markdown.contains("## Device Information"))
        assertTrue(markdown.contains("Pixel 7 Pro"))
        assertTrue(markdown.contains("**FLAGSHIP**"))
        assertTrue(markdown.contains("## Camera Units"))
        assertTrue(markdown.contains("`LEVEL_3`"))
        assertTrue(markdown.contains("Optical (OIS): true"))
    }
}
