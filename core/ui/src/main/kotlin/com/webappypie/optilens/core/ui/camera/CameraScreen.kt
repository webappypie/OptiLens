package com.webappypie.optilens.core.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.ui.components.OptiCameraModeChip
import com.webappypie.optilens.core.ui.components.OptiIconButton
import com.webappypie.optilens.core.ui.components.OptiIconButtonVariant
import com.webappypie.optilens.core.ui.theme.OptiLensCameraTypography
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

enum class CameraMode(val label: String) {
    PHOTO("Photo"),
    NIGHT("Night"),
    PORTRAIT("Portrait"),
    PRO("Pro"),
    VIDEO("Video"),
}

enum class FlashState { AUTO, ON, OFF }
enum class HdrState { AUTO, ON, OFF }
enum class TimerState(val seconds: Int) { OFF(0), SEC_3(3), SEC_10(10) }

/**
 * Camera Screen Layout for OptiLens.
 *
 * Employs a neutral fake preview surface for layout verification in Phase 02,
 * complete with:
 * - Edge-to-edge safe insets.
 * - Viewfinder with rule-of-thirds grid overlay and horizon level line.
 * - Top chrome: Flash, HDR, Timer, Settings.
 * - Scene hint pill indicator.
 * - Zoom selector: 0.6x, 1x, 2x, 5x.
 * - Shooting mode carousel (Photo, Night, Portrait, Pro, Video).
 * - Tactile shutter button with outer contrast ring.
 * - Gallery shortcut and camera flip buttons with 48dp+ touch targets.
 */
@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier,
    onShutterClick: () -> Unit = {},
    showGrid: Boolean = true,
    showLevel: Boolean = true,
) {
    var currentMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var zoomRatio by remember { mutableFloatStateOf(1.0f) }
    var flashState by remember { mutableStateOf(FlashState.AUTO) }
    var hdrState by remember { mutableStateOf(HdrState.AUTO) }
    var timerState by remember { mutableStateOf(TimerState.OFF) }
    var isFrontCamera by remember { mutableStateOf(false) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val overlayColors = OptiLensTheme.overlayColors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── 1. Viewfinder Surface (Neutral Fake Preview) ───────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0E1117))
                .pointerInput(Unit) {
                    detectTapGestures { /* Tap to focus in Phase 04 */ }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Viewfinder aspect frame (4:3)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .background(Color(0xFF0A0C10)),
            ) {
                // Rule-of-thirds grid
                if (showGrid) {
                    ViewfinderGridOverlay(color = overlayColors.gridLine)
                }

                // Level / horizon indicator line
                if (showLevel) {
                    HorizonLevelIndicator(
                        tiltAngleDegrees = 0f,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }

            // Floating Scene Hint Pill
            AnimatedVisibility(
                visible = currentMode == CameraMode.NIGHT,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 64.dp),
            ) {
                SceneHintPill(text = "Low Light • Handheld Night Active")
            }
        }

        // ── 2. Top Scrim & Controls ────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(overlayColors.scrimBackground)
                .padding(top = topInset)
                .padding(
                    horizontal = OptiLensTheme.spacing.l,
                    vertical = OptiLensTheme.spacing.s,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Flash Toggle
                OptiIconButton(
                    icon = when (flashState) {
                        FlashState.AUTO -> Icons.Default.FlashAuto
                        FlashState.ON   -> Icons.Default.FlashOn
                        FlashState.OFF  -> Icons.Default.FlashOff
                    },
                    contentDescription = "Flash mode: ${flashState.name}",
                    variant = if (flashState == FlashState.ON) OptiIconButtonVariant.OVERLAY_ACTIVE else OptiIconButtonVariant.OVERLAY,
                    onClick = {
                        flashState = when (flashState) {
                            FlashState.AUTO -> FlashState.ON
                            FlashState.ON   -> FlashState.OFF
                            FlashState.OFF  -> FlashState.AUTO
                        }
                    },
                )

                // HDR Toggle
                OptiIconButton(
                    icon = when (hdrState) {
                        HdrState.AUTO -> Icons.Default.HdrAuto
                        HdrState.ON   -> Icons.Default.HdrOn
                        HdrState.OFF  -> Icons.Default.HdrOff
                    },
                    contentDescription = "HDR mode: ${hdrState.name}",
                    variant = if (hdrState == HdrState.ON) OptiIconButtonVariant.OVERLAY_ACTIVE else OptiIconButtonVariant.OVERLAY,
                    onClick = {
                        hdrState = when (hdrState) {
                            HdrState.AUTO -> HdrState.ON
                            HdrState.ON   -> HdrState.OFF
                            HdrState.OFF  -> HdrState.AUTO
                        }
                    },
                )

                // Timer Toggle
                OptiIconButton(
                    icon = when (timerState) {
                        TimerState.OFF    -> Icons.Default.Timer
                        TimerState.SEC_3  -> Icons.Default.Timer3
                        TimerState.SEC_10 -> Icons.Default.Timer10
                    },
                    contentDescription = "Timer: ${timerState.seconds}s",
                    variant = if (timerState != TimerState.OFF) OptiIconButtonVariant.OVERLAY_ACTIVE else OptiIconButtonVariant.OVERLAY,
                    onClick = {
                        timerState = when (timerState) {
                            TimerState.OFF    -> TimerState.SEC_3
                            TimerState.SEC_3  -> TimerState.SEC_10
                            TimerState.SEC_10 -> TimerState.OFF
                        }
                    },
                )

                // Settings Navigation Button
                OptiIconButton(
                    icon = Icons.Default.Settings,
                    contentDescription = "Open Settings",
                    variant = OptiIconButtonVariant.OVERLAY,
                    onClick = onNavigateToSettings,
                )
            }
        }

        // ── 3. Bottom Scrim & Capture Controls ─────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(overlayColors.scrimBackground)
                .padding(bottom = bottomInset.coerceAtLeast(OptiLensTheme.spacing.l))
                .padding(top = OptiLensTheme.spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Zoom Selector Bar
            ZoomSelector(
                currentZoom = zoomRatio,
                onZoomSelected = { zoomRatio = it },
                availableRatios = listOf(0.6f, 1.0f, 2.0f, 5.0f),
                modifier = Modifier.padding(bottom = OptiLensTheme.spacing.s),
            )

            // Shooting Mode Carousel
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = OptiLensTheme.spacing.xs),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(CameraMode.entries) { mode ->
                    OptiCameraModeChip(
                        title = mode.label,
                        isSelected = currentMode == mode,
                        onClick = { currentMode = mode },
                    )
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))

            // Main Shutter Row: Gallery Shortcut | Shutter Button | Camera Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OptiLensTheme.spacing.xxl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gallery Shortcut Button (48dp touch target)
                Box(
                    modifier = Modifier
                        .size(OptiLensTheme.iconSizes.minTouchTarget)
                        .clip(CircleShape)
                        .background(overlayColors.controlSurface)
                        .border(1.dp, overlayColors.controlBorder, CircleShape)
                        .clickable(
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onNavigateToGallery,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Open Gallery",
                        tint = overlayColors.controlOnSurface,
                        modifier = Modifier.size(OptiLensTheme.iconSizes.standard),
                    )
                }

                // Tactile Shutter Button
                CameraShutterButton(
                    onClick = onShutterClick,
                    isVideo = currentMode == CameraMode.VIDEO,
                )

                // Camera Flip Button (48dp touch target)
                Box(
                    modifier = Modifier
                        .size(OptiLensTheme.iconSizes.minTouchTarget)
                        .clip(CircleShape)
                        .background(overlayColors.controlSurface)
                        .border(1.dp, overlayColors.controlBorder, CircleShape)
                        .clickable(
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { isFrontCamera = !isFrontCamera },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip camera to ${if (isFrontCamera) "rear" else "front"}",
                        tint = overlayColors.controlOnSurface,
                        modifier = Modifier.size(OptiLensTheme.iconSizes.standard),
                    )
                }
            }
        }
    }
}

/**
 * High-precision circular shutter button with contrast outer ring
 * and tactile press scale animation.
 */
@Composable
fun CameraShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVideo: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val overlayColors = OptiLensTheme.overlayColors

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = tween(durationMillis = 100),
        label = "shutterScale",
    )

    Box(
        modifier = modifier
            .size(76.dp)
            .scale(scale)
            .semantics {
                this.role = Role.Button
                this.contentDescription = if (isVideo) "Record video" else "Take photo"
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Outer contrast ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(4.dp, overlayColors.shutterOuterRing, CircleShape),
        )

        // Inner shutter core
        val coreColor = if (isVideo) overlayColors.shutterRecordingCore else overlayColors.shutterInnerCore
        val coreShape = if (isVideo && isPressed) RoundedCornerShape(8.dp) else CircleShape

        Box(
            modifier = Modifier
                .size(if (isPressed) 52.dp else 60.dp)
                .clip(coreShape)
                .background(coreColor),
        )
    }
}

/**
 * Discrete zoom pill selector with monospace numeric readouts.
 */
@Composable
fun ZoomSelector(
    currentZoom: Float,
    onZoomSelected: (Float) -> Unit,
    availableRatios: List<Float>,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(overlayColors.controlSurface)
            .border(1.dp, overlayColors.controlBorder, CircleShape)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availableRatios.forEach { ratio ->
            val isSelected = (currentZoom == ratio)
            val label = if (ratio < 1.0f) "${ratio}x" else "${ratio.toInt()}x"

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) overlayColors.controlSurfaceActive else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 17.dp),
                        onClick = { onZoomSelected(ratio) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = OptiLensCameraTypography.zoomReadout,
                    color = if (isSelected) overlayColors.controlOnSurfaceActive else overlayColors.controlOnSurface,
                )
            }
        }
    }
}

/**
 * 3x3 Rule-of-thirds viewfinder grid overlay.
 */
@Composable
fun ViewfinderGridOverlay(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val strokeWidth = 1.dp.toPx()
        val w = size.width
        val h = size.height

        // Vertical grid lines
        drawLine(color, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth)
        drawLine(color, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth)

        // Horizontal grid lines
        drawLine(color, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth)
        drawLine(color, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth)
    }
}

/**
 * Horizon tilt balance indicator line.
 */
@Composable
fun HorizonLevelIndicator(
    tiltAngleDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val isBalanced = kotlin.math.abs(tiltAngleDegrees) < 1.0f
    val overlayColors = OptiLensTheme.overlayColors
    val lineColor = if (isBalanced) overlayColors.focusSuccess else overlayColors.horizonWarning

    Box(
        modifier = modifier
            .width(80.dp)
            .height(2.dp)
            .background(lineColor),
    )
}

/**
 * Floating scene detection pill.
 */
@Composable
fun SceneHintPill(
    text: String,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(overlayColors.scrimBackground)
            .border(1.dp, overlayColors.controlBorder, CircleShape)
            .padding(horizontal = OptiLensTheme.spacing.l, vertical = OptiLensTheme.spacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = overlayColors.activeAccent,
        )
    }
}
