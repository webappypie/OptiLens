package com.webappypie.optilens.core.ui.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.thermal.DeviceThermalState

/**
 * Renders real-time and post-session quality, latency, memory, and thermal telemetry.
 */
@Composable
fun PerformanceDashboardCard(
    data: PerformanceDashboardData,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Dashboard Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "System Performance & Health",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Real-time engine telemetry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Health status badge
                val isCritical = data.thermalState == DeviceThermalState.CRITICAL || data.memorySnapshot.isUnderPressure
                val isWarning = data.thermalState == DeviceThermalState.MODERATE || data.thermalState == DeviceThermalState.SEVERE
                val badgeColor = when {
                    isCritical -> Color(0xFFE53935)
                    isWarning -> Color(0xFFFFA000)
                    else -> Color(0xFF43A047)
                }
                val badgeText = when {
                    isCritical -> "THROTTLED"
                    isWarning -> "ELEVATED"
                    else -> "OPTIMAL"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(badgeColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(badgeColor),
                    )
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // User thermal warning alert if present
            data.userThermalWarning?.let { warningMsg ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFD32F2F).copy(alpha = 0.15f))
                        .padding(10.dp),
                ) {
                    Text(
                        text = warningMsg,
                        color = Color(0xFFFF5252),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 1. Startup & Latency Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "STARTUP & READINESS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val startup = data.startupMetrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem(
                        label = "Cold Start Draw",
                        value = startup?.let { "${it.appStartupLatencyMs} ms" } ?: "--",
                    )
                    MetricItem(
                        label = "Camera Ready",
                        value = startup?.let { "${it.cameraReadyLatencyMs} ms" } ?: "--",
                    )
                    MetricItem(
                        label = "App Init",
                        value = startup?.let { "${it.appOnCreateTimeMs} ms" } ?: "--",
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 2. Jank & Frame Render Loop
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "FRAME RENDERING & JANK",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val frames = data.frameMetrics
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem(
                        label = "Jank Rate",
                        value = "%.1f%%".format(frames.jankRatePercent),
                    )
                    MetricItem(
                        label = "Severe Jank (>33ms)",
                        value = "${frames.severeJankFrames}",
                    )
                    MetricItem(
                        label = "p90 Frame Time",
                        value = "%.1f ms".format(frames.p90FrameDurationMs),
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 3. Memory & Native Allocations
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "MEMORY ALLOCATION & PRESSURE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                val mem = data.memorySnapshot
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem(
                        label = "JVM Heap",
                        value = "%.1f / %.0f MB".format(mem.javaHeapAllocatedMb, mem.javaHeapMaxMb),
                    )
                    MetricItem(
                        label = "Native Heap",
                        value = "%.1f MB".format(mem.nativeHeapAllocatedMb),
                    )
                    MetricItem(
                        label = "Peak Heap",
                        value = "%.1f MB".format(mem.peakHeapObservedMb),
                    )
                }
                val utilization = if (mem.javaHeapMaxMb > 0f) mem.javaHeapAllocatedMb / mem.javaHeapMaxMb else 0f
                LinearProgressIndicator(
                    progress = { utilization.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (mem.isUnderPressure) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // 4. Thermal & Buffer Pool Section
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "THERMAL & BUFFER REUSE POOL",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MetricItem(
                        label = "Thermal State",
                        value = data.thermalState.name,
                    )
                    MetricItem(
                        label = "Burst Cap",
                        value = "${data.maxBurstFrames} frames",
                    )
                    MetricItem(
                        label = "Pool Hit Rate",
                        value = "%.1f%%".format(data.poolHitRate * 100f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
