package com.webappypie.optilens.core.ui.camera.pro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.model.ProCameraState
import com.webappypie.optilens.core.camera.model.WhiteBalanceMode
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

enum class ProControlTab {
    NONE, ISO, SHUTTER, EV, FOCUS, WB
}

/**
 * Professional camera parameter adjustment toolbar for manual photography controls.
 *
 * Implements:
 * - Direct ISO, Shutter, Focus, WB, and EV adjustments.
 * - Hardware capability detection: unsupported controls are explicitly disabled with visual badges.
 * - One-tap AUTO Reset button to immediately restore full 3A automation.
 */
@Composable
fun ProControlsBar(
    proState: ProCameraState,
    onIsoChanged: (Int?) -> Unit,
    onShutterSpeedChanged: (Long?) -> Unit,
    onFocusDistanceChanged: (Float?) -> Unit,
    onWhiteBalanceChanged: (WhiteBalanceMode) -> Unit,
    onEvChanged: (Int) -> Unit,
    onResetToAuto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(ProControlTab.NONE) }
    val overlayColors = OptiLensTheme.overlayColors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(overlayColors.scrimBackground)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── 1. Secondary Drawer / Slider Controls ──────────────────────
        AnimatedVisibility(
            visible = activeTab != ProControlTab.NONE,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(overlayColors.controlSurface)
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                when (activeTab) {
                    ProControlTab.ISO -> IsoControlSlider(
                        currentIso = proState.iso,
                        range = proState.isoRange ?: 50..3200,
                        onIsoSelected = onIsoChanged,
                    )
                    ProControlTab.SHUTTER -> ShutterControlPicker(
                        currentNanos = proState.shutterSpeedNanos,
                        onShutterSelected = onShutterSpeedChanged,
                    )
                    ProControlTab.EV -> EvControlSlider(
                        currentEv = proState.evIndex,
                        range = proState.evRange,
                        step = proState.evStep,
                        onEvSelected = onEvChanged,
                    )
                    ProControlTab.FOCUS -> FocusControlSlider(
                        currentFocus = proState.focusDistanceDiopters,
                        onFocusSelected = onFocusDistanceChanged,
                    )
                    ProControlTab.WB -> WhiteBalanceModeRow(
                        currentMode = proState.whiteBalanceMode,
                        onModeSelected = onWhiteBalanceChanged,
                    )
                    ProControlTab.NONE -> Unit
                }
            }
        }

        // ── 2. Primary Control Tabs Row ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ISO Tab
            ProTabChip(
                label = "ISO",
                value = proState.iso?.toString() ?: "AUTO",
                isActive = activeTab == ProControlTab.ISO,
                isManual = proState.iso != null,
                isEnabled = proState.isIsoManualSupported,
                onClick = {
                    activeTab = if (activeTab == ProControlTab.ISO) ProControlTab.NONE else ProControlTab.ISO
                },
            )

            // Shutter Tab
            ProTabChip(
                label = "S",
                value = ProCameraState.formatShutterSpeed(proState.shutterSpeedNanos),
                isActive = activeTab == ProControlTab.SHUTTER,
                isManual = proState.shutterSpeedNanos != null,
                isEnabled = proState.isShutterManualSupported,
                onClick = {
                    activeTab = if (activeTab == ProControlTab.SHUTTER) ProControlTab.NONE else ProControlTab.SHUTTER
                },
            )

            // EV Tab
            val evFormatted = if (proState.evIndex == 0) "0.0" else String.format(java.util.Locale.US, "%+.1f", proState.evIndex * proState.evStep)
            ProTabChip(
                label = "EV",
                value = evFormatted,
                isActive = activeTab == ProControlTab.EV,
                isManual = proState.evIndex != 0,
                isEnabled = true,
                onClick = {
                    activeTab = if (activeTab == ProControlTab.EV) ProControlTab.NONE else ProControlTab.EV
                },
            )

            // Focus Tab
            ProTabChip(
                label = "FOCUS",
                value = if (proState.focusDistanceDiopters != null) String.format(java.util.Locale.US, "%.1f", proState.focusDistanceDiopters) else "AF",
                isActive = activeTab == ProControlTab.FOCUS,
                isManual = proState.focusDistanceDiopters != null,
                isEnabled = proState.isFocusManualSupported,
                onClick = {
                    activeTab = if (activeTab == ProControlTab.FOCUS) ProControlTab.NONE else ProControlTab.FOCUS
                },
            )

            // WB Tab
            ProTabChip(
                label = "WB",
                value = proState.whiteBalanceMode.label,
                isActive = activeTab == ProControlTab.WB,
                isManual = proState.whiteBalanceMode != WhiteBalanceMode.AUTO,
                isEnabled = proState.isWhiteBalanceSupported,
                onClick = {
                    activeTab = if (activeTab == ProControlTab.WB) ProControlTab.NONE else ProControlTab.WB
                },
            )

            // AUTO Reset Button
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (proState.isAnyManualActive) overlayColors.activeAccent.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable(onClick = {
                        activeTab = ProControlTab.NONE
                        onResetToAuto()
                    })
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset Pro controls to Auto",
                        tint = if (proState.isAnyManualActive) overlayColors.activeAccent else overlayColors.controlOnSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "RESET",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (proState.isAnyManualActive) overlayColors.activeAccent else overlayColors.controlOnSurface.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProTabChip(
    label: String,
    value: String,
    isActive: Boolean,
    isManual: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val chipColor = when {
        !isEnabled -> Color.Transparent
        isActive -> overlayColors.controlSurfaceActive
        isManual -> overlayColors.activeAccent.copy(alpha = 0.15f)
        else -> Color.Transparent
    }

    val textColor = when {
        !isEnabled -> overlayColors.controlOnSurface.copy(alpha = 0.3f)
        isActive -> overlayColors.controlOnSurfaceActive
        isManual -> overlayColors.activeAccent
        else -> overlayColors.controlOnSurface
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(chipColor)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = textColor.copy(alpha = 0.7f),
        )
        Text(
            text = if (isEnabled) value else "N/A",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = textColor,
        )
    }
}

@Composable
private fun IsoControlSlider(
    currentIso: Int?,
    range: ClosedRange<Int>,
    onIsoSelected: (Int?) -> Unit,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val displayVal = currentIso ?: range.start

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "AUTO",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (currentIso == null) overlayColors.activeAccent else overlayColors.controlOnSurface,
            modifier = Modifier
                .clickable { onIsoSelected(null) }
                .padding(4.dp),
        )

        Slider(
            value = displayVal.toFloat(),
            onValueChange = { onIsoSelected(it.toInt()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = overlayColors.activeAccent,
                activeTrackColor = overlayColors.activeAccent,
            ),
        )

        Text(
            text = displayVal.toString(),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = overlayColors.controlOnSurface,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun ShutterControlPicker(
    currentNanos: Long?,
    onShutterSelected: (Long?) -> Unit,
) {
    val overlayColors = OptiLensTheme.overlayColors
    // Standard stepped shutter speeds (nanoseconds)
    val shutterSteps = listOf(
        null, // AUTO
        250_000L,       // 1/4000
        500_000L,       // 1/2000
        1_000_000L,     // 1/1000
        2_000_000L,     // 1/500
        4_000_000L,     // 1/250
        8_000_000L,     // 1/125
        16_666_666L,    // 1/60
        33_333_333L,    // 1/30
        66_666_666L,    // 1/15
        125_000_000L,   // 1/8
        250_000_000L,   // 1/4
        500_000_000L,   // 1/2
        1_000_000_000L, // 1s
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shutterSteps.forEach { stepNanos ->
            val isSelected = currentNanos == stepNanos
            val label = ProCameraState.formatShutterSpeed(stepNanos)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) overlayColors.controlSurfaceActive else Color.Transparent)
                    .border(0.5.dp, if (isSelected) overlayColors.activeAccent else overlayColors.controlBorder, RoundedCornerShape(4.dp))
                .clickable { onShutterSelected(stepNanos) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isSelected) overlayColors.controlOnSurfaceActive else overlayColors.controlOnSurface,
                )
            }
        }
    }
}

@Composable
private fun EvControlSlider(
    currentEv: Int,
    range: ClosedRange<Int>,
    step: Float,
    onEvSelected: (Int) -> Unit,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val minVal = if (range.start != range.endInclusive) range.start.toFloat() else -12f
    val maxVal = if (range.start != range.endInclusive) range.endInclusive.toFloat() else 12f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "EV 0",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (currentEv == 0) overlayColors.activeAccent else overlayColors.controlOnSurface,
            modifier = Modifier
                .clickable { onEvSelected(0) }
                .padding(4.dp),
        )

        Slider(
            value = currentEv.toFloat(),
            onValueChange = { onEvSelected(it.toInt()) },
            valueRange = minVal..maxVal,
            steps = ((maxVal - minVal) - 1).toInt().coerceAtLeast(0),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = overlayColors.activeAccent,
                activeTrackColor = overlayColors.activeAccent,
            ),
        )

        val evFormatted = if (currentEv == 0) "0.0" else String.format(java.util.Locale.US, "%+.1f", currentEv * (if (step > 0f) step else 0.333f))
        Text(
            text = evFormatted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = overlayColors.controlOnSurface,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun FocusControlSlider(
    currentFocus: Float?,
    onFocusSelected: (Float?) -> Unit,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val displayVal = currentFocus ?: 0f

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "AF",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (currentFocus == null) overlayColors.activeAccent else overlayColors.controlOnSurface,
            modifier = Modifier
                .clickable { onFocusSelected(null) }
                .padding(4.dp),
        )

        Slider(
            value = displayVal,
            onValueChange = { onFocusSelected(it) },
            valueRange = 0f..10f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = overlayColors.activeAccent,
                activeTrackColor = overlayColors.activeAccent,
            ),
        )

        Text(
            text = if (currentFocus != null) String.format(java.util.Locale.US, "%.1f", displayVal) else "INF",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = overlayColors.controlOnSurface,
            modifier = Modifier.width(36.dp),
        )
    }
}

@Composable
private fun WhiteBalanceModeRow(
    currentMode: WhiteBalanceMode,
    onModeSelected: (WhiteBalanceMode) -> Unit,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WhiteBalanceMode.entries.forEach { mode ->
            val isSelected = currentMode == mode

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) overlayColors.controlSurfaceActive else Color.Transparent)
                    .border(0.5.dp, if (isSelected) overlayColors.activeAccent else overlayColors.controlBorder, RoundedCornerShape(4.dp))
                    .clickable { onModeSelected(mode) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = mode.label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) overlayColors.controlOnSurfaceActive else overlayColors.controlOnSurface,
                )
            }
        }
    }
}
