package com.webappypie.optilens.core.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webappypie.optilens.core.camera.model.CameraDeviceProfile
import com.webappypie.optilens.core.camera.model.CameraHardwareLevel
import com.webappypie.optilens.core.camera.model.PerformanceTier
import com.webappypie.optilens.core.ui.components.OptiButton
import com.webappypie.optilens.core.ui.components.OptiErrorState
import com.webappypie.optilens.core.ui.components.OptiLoadingState
import com.webappypie.optilens.core.ui.components.OptiTopBar
import com.webappypie.optilens.core.ui.state.UiState
import com.webappypie.optilens.core.ui.theme.OptiLensTheme
import kotlinx.coroutines.launch

@Composable
fun CameraDiagnosticsScreen(
    viewModel: CameraDiagnosticsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            OptiTopBar(
                title = "Hardware Diagnostics",
                onBackClick = onNavigateBack,
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh hardware discovery",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> {
                OptiLoadingState(
                    message = state.message ?: "Inspecting camera hardware...",
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            is UiState.Error -> {
                OptiErrorState(
                    title = "Hardware Inspection Failed",
                    message = state.error.displayMessage,
                    onRetry = state.retry ?: { viewModel.refresh() },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            is UiState.Success -> {
                val data = state.data
                DiagnosticsContent(
                    state = data,
                    onSelectCamera = { viewModel.selectCamera(it) },
                    onCopyJson = {
                        clipboardManager.setText(AnnotatedString(data.jsonExport))
                        scope.launch {
                            snackbarHostState.showSnackbar("Diagnostics JSON copied to clipboard")
                        }
                    },
                    onShareReport = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, data.markdownExport)
                            putExtra(Intent.EXTRA_SUBJECT, "OptiLens Camera Hardware Diagnostics")
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Share Hardware Diagnostics")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
            UiState.Idle -> Unit
        }
    }
}

@Composable
private fun DiagnosticsContent(
    state: CameraDiagnosticsUiState,
    onSelectCamera: (Int) -> Unit,
    onCopyJson: () -> Unit,
    onShareReport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile
    val selectedCamera = state.selectedCamera

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(OptiLensTheme.spacing.l),
        verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.l),
    ) {
        // ── Device Overview Card ──────────────────────────────────────
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(
                modifier = Modifier.padding(OptiLensTheme.spacing.l),
                verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.s),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${profile.deviceManufacturer} ${profile.deviceModel}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Android ${profile.androidApiLevel} • ${String.format("%.1f", profile.totalRamGb)} GB RAM • ${profile.cpuCores} Cores",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Performance Tier Pill
                    TierBadge(tier = profile.performanceTier, score = profile.performanceScore)
                }

                if (profile.detectedQuirks.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = OptiLensTheme.spacing.xs))
                    Text(
                        text = "ACTIVE HARDWARE QUIRKS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.error,
                    )
                    for (quirk in profile.detectedQuirks) {
                        Text(
                            text = "• $quirk",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        // ── Camera Selector Tabs ──────────────────────────────────────
        if (profile.cameras.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = state.selectedCameraIndex.coerceIn(0, profile.cameras.size - 1),
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                profile.cameras.forEachIndexed { index, cam ->
                    Tab(
                        selected = index == state.selectedCameraIndex,
                        onClick = { onSelectCamera(index) },
                        text = {
                            Text("Cam [${cam.id}] ${cam.lensFacing}")
                        },
                    )
                }
            }
        }

        // ── Selected Camera Details ───────────────────────────────────
        if (selectedCamera != null) {
            CameraDetailsSection(camera = selectedCamera)
        }

        // ── Export & Share Buttons ────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.m),
        ) {
            OptiButton(
                text = "Copy JSON",
                leadingIcon = Icons.Default.ContentCopy,
                onClick = onCopyJson,
                modifier = Modifier.weight(1f),
            )
            OptiButton(
                text = "Share Report",
                leadingIcon = Icons.Default.Share,
                onClick = onShareReport,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(OptiLensTheme.spacing.giant))
    }
}

@Composable
private fun TierBadge(tier: PerformanceTier, score: Int) {
    val (containerColor, contentColor) = when (tier) {
        PerformanceTier.FLAGSHIP -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        PerformanceTier.HIGH_PERFORMANCE -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        PerformanceTier.MID_RANGE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        PerformanceTier.ENTRY_LEVEL -> MaterialTheme.colorScheme.outlineVariant to MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .background(containerColor, shape = MaterialTheme.shapes.small)
            .padding(horizontal = OptiLensTheme.spacing.m, vertical = OptiLensTheme.spacing.xs),
    ) {
        Text(
            text = "$tier ($score)",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = contentColor,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CameraDetailsSection(camera: CameraDeviceProfile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(OptiLensTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.m),
        ) {
            Text(
                text = "OPTICAL & SENSOR CHARACTERISTICS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )

            SpecRow("Hardware Level", camera.hardwareLevel.name)
            SpecRow(
                "Focal Lengths",
                if (camera.focalLengthsMm.isEmpty()) "N/A" else camera.focalLengthsMm.joinToString(", ") { "${it}mm" }
            )
            SpecRow("Active Sensor Array", "${camera.sensorInfo.activeArrayWidth} x ${camera.sensorInfo.activeArrayHeight}")
            SpecRow("Pixel Array", "${camera.sensorInfo.pixelArrayWidth} x ${camera.sensorInfo.pixelArrayHeight}")
            SpecRow("Physical Sensor Size", "${camera.sensorInfo.physicalWidthMm} x ${camera.sensorInfo.physicalHeightMm} mm")
            SpecRow("Sensor Orientation", "${camera.sensorInfo.orientationDegrees}° (${camera.sensorInfo.timestampSource})")
            SpecRow("Optical Zoom Multipliers", camera.computeAvailableZoomRatios().joinToString("x, ") + "x")
            SpecRow("Zoom Ratio Range", "${camera.minZoom}x to ${camera.maxZoom}x")

            HorizontalDivider()

            Text(
                text = "STREAM FORMATS & RESOLUTION",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
            SpecRow("Max JPEG", camera.resolutions.maxJpegSize?.toString() ?: "None")
            SpecRow("Max RAW", camera.resolutions.maxRawSize?.toString() ?: "Unsupported")
            SpecRow("Max YUV", camera.resolutions.maxYuvSize?.toString() ?: "Unsupported")
            SpecRow("Max Private", camera.resolutions.maxPrivateSize?.toString() ?: "Unsupported")
            if (camera.resolutions.ultraHighResolutionSizes.isNotEmpty()) {
                SpecRow("Ultra High-Res", camera.resolutions.ultraHighResolutionSizes.first().toString())
            }

            HorizontalDivider()

            Text(
                text = "HARDWARE CAPABILITIES",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.xs),
            ) {
                CapabilityChip("RAW Sensor", camera.streamCapabilities.supportsRaw)
                CapabilityChip("Burst Capture", camera.streamCapabilities.supportsBurstCapture)
                CapabilityChip("YUV Reprocessing", camera.streamCapabilities.supportsYuvReprocessing)
                CapabilityChip("Private Reprocess", camera.streamCapabilities.supportsPrivateReprocessing)
                CapabilityChip("Manual Sensor", camera.streamCapabilities.supportsManualSensor)
                CapabilityChip("Multi-Camera", camera.streamCapabilities.isLogicalMultiCamera)
                CapabilityChip("Optical Stabilization (OIS)", camera.stabilization.opticalImageStabilization)
                CapabilityChip("Video Stabilization (EIS)", camera.stabilization.electronicVideoStabilization)
                CapabilityChip("10-bit HDR", camera.streamCapabilities.supportsTenBitHdr)
                CapabilityChip("Flash Available", camera.controls.hasFlash)
            }

            HorizontalDivider()

            Text(
                text = "CAMERAX EXTENSIONS",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.xs),
            ) {
                CapabilityChip("Bokeh", camera.extensions.bokeh)
                CapabilityChip("HDR", camera.extensions.hdr)
                CapabilityChip("Night", camera.extensions.night)
                CapabilityChip("Face Retouch", camera.extensions.faceRetouch)
                CapabilityChip("Auto", camera.extensions.auto)
                CapabilityChip("Low-Light Boost", camera.extensions.lowLightBoost)
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

@Composable
private fun CapabilityChip(label: String, supported: Boolean) {
    FilterChip(
        selected = supported,
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = {
            Icon(
                imageVector = if (supported) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (supported) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        },
    )
}
