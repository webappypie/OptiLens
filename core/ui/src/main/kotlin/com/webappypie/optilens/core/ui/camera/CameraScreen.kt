package com.webappypie.optilens.core.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.HdrAuto
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import com.webappypie.optilens.core.camera.night.StabilityAssessment
import com.webappypie.optilens.core.camera.night.StabilityClassification
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webappypie.optilens.core.camera.model.FlashMode
import com.webappypie.optilens.core.camera.model.ZoomStop
import com.webappypie.optilens.core.ui.camera.histogram.HistogramOverlay
import com.webappypie.optilens.core.ui.camera.overlay.FaceBoundingBoxOverlay
import com.webappypie.optilens.core.ui.camera.overlay.SceneHintPill as IntelligentSceneHintPill
import com.webappypie.optilens.core.ui.camera.pro.ProControlsBar
import com.webappypie.optilens.core.ui.camera.sensor.HorizonSensor
import com.webappypie.optilens.core.ui.components.OptiCameraModeChip
import com.webappypie.optilens.core.ui.components.OptiIconButton
import com.webappypie.optilens.core.ui.components.OptiIconButtonVariant
import com.webappypie.optilens.core.camera.portrait.PortraitAperture
import com.webappypie.optilens.core.ui.components.OptiPermissionState
import com.webappypie.optilens.core.ui.theme.OptiLensCameraTypography
import com.webappypie.optilens.core.ui.theme.OptiLensTheme
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

enum class CameraMode(val label: String) {
    PHOTO("Photo"),
    NIGHT("Night"),
    PORTRAIT("Portrait"),
    PRO("Pro"),
    VIDEO("Video"),
}

enum class HdrState { AUTO, ON, OFF }

/**
 * Production Camera Screen for OptiLens.
 *
 * Implements:
 * - Real CameraX viewfinder with [PreviewView].
 * - Real hardware-derived truthful zoom stops (optical vs digital crop).
 * - Smooth continuous pinch zoom + optical stop haptics.
 * - Non-rebinding animated Aspect Ratio framing (4:3, 16:9, 1:1).
 * - Sensor-driven horizon level balance line via [HorizonSensor].
 * - Timer with animated countdown overlay (0s, 3s, 10s).
 * - Hardware volume-key shutter trigger support.
 * - Pro manual mode controls (ISO, Shutter, Focus, WB, EV, AUTO Reset).
 * - Real-time 64-bin luminance histogram overlay.
 * - Camera permission verification and fallback via [OptiPermissionState].
 * - Tap-to-focus with animated reticle indicator.
 * - Shutter button with tactile animation, capture lock, and blink feedback.
 * - Camera flip and recent capture gallery shortcut thumbnail.
 */
@Composable
fun CameraScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onNavigateToPhotoReview: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    onShutterClick: () -> Unit = {},
    showGrid: Boolean = true,
    showLevel: Boolean = true,
    externalShutterTrigger: Flow<Unit>? = null,
    viewModel: CameraViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val horizonSensor = remember { HorizonSensor(context) }
    val rollDegrees by horizonSensor.rollDegrees.collectAsStateWithLifecycle()

    var currentMode by remember { mutableStateOf(CameraMode.PHOTO) }
    var hdrState by remember { mutableStateOf(HdrState.AUTO) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Start/stop horizon sensor with lifecycle & visibility
    DisposableEffect(showLevel) {
        if (showLevel) horizonSensor.start()
        onDispose { horizonSensor.stop() }
    }

    // Haptic feedback when crossing physical optical zoom stops
    LaunchedEffect(Unit) {
        viewModel.opticalHapticFlow.collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // External shutter trigger (e.g. Hardware Volume Keys)
    externalShutterTrigger?.let { trigger ->
        LaunchedEffect(trigger) {
            trigger.collect {
                onShutterClick()
                if (currentMode == CameraMode.NIGHT) {
                    viewModel.takeNightPhoto(targetRotation = Surface.ROTATION_0)
                } else if (currentMode == CameraMode.PORTRAIT) {
                    viewModel.takePortraitPhoto(targetRotation = Surface.ROTATION_0)
                } else {
                    viewModel.takePhotoWithTimer(targetRotation = Surface.ROTATION_0)
                }
            }
        }
    }

    // Camera permission check and launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
        if (!granted) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!uiState.hasCameraPermission) {
        OptiPermissionState(
            title = "Camera Access Required",
            description = "OptiLens requires camera access to preview and capture photos. No gallery or media permissions are requested.",
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
                context.startActivity(intent)
            },
            modifier = modifier,
        )
        return
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val overlayColors = OptiLensTheme.overlayColors

    // Smooth non-rebinding aspect ratio transition
    val targetAspect = uiState.aspectRatio.ratio
    val animatedAspect by animateFloatAsState(
        targetValue = targetAspect,
        animationSpec = tween(durationMillis = 250),
        label = "viewfinderAspect",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── 1. Viewfinder Surface ──────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(uiState.zoomState) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1.0f) {
                            val current = uiState.zoomState.currentZoom
                            val minZ = if (uiState.zoomState.minZoom > 0f) uiState.zoomState.minZoom else 0.5f
                            val maxZ = if (uiState.zoomState.maxZoom > 0f) uiState.zoomState.maxZoom else 10.0f
                            val target = (current * zoom).coerceIn(minZ, maxZ)
                            viewModel.onZoomRatioChanged(target)
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val factory = previewViewRef?.meteringPointFactory
                        val point = factory?.createPoint(tapOffset.x, tapOffset.y)
                            ?: SurfaceOrientedMeteringPointFactory(1f, 1f).createPoint(0.5f, 0.5f)
                        viewModel.onTapToFocus(tapOffset, point)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Viewfinder aspect frame with animated transition
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(animatedAspect)
                    .clip(RoundedCornerShape(if (uiState.aspectRatio == CameraAspectRatio.RATIO_1_1) 12.dp else 0.dp))
                    .background(Color(0xFF0A0C10)),
            ) {
                // Live CameraX Preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            previewViewRef = this
                            viewModel.bindPreview(lifecycleOwner, this.surfaceProvider)
                        }
                    },
                    update = { view ->
                        previewViewRef = view
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                // Rule-of-thirds grid
                if (showGrid) {
                    ViewfinderGridOverlay(color = overlayColors.gridLine)
                }

                // Sensor-driven level / horizon indicator line
                if (showLevel) {
                    HorizonLevelIndicator(
                        tiltAngleDegrees = rollDegrees,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Tap-to-focus animated reticle
                uiState.focusTarget?.let { target ->
                    FocusReticle(offset = target)
                }

                // Real-time Face & Landmark Detection Overlay
                FaceBoundingBoxOverlay(
                    faces = uiState.detectedFaces,
                )

                // Timer Countdown Large Visual Overlay
                if (uiState.timerCountdown != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${uiState.timerCountdown}",
                            fontSize = 84.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            style = MaterialTheme.typography.displayLarge,
                        )
                    }
                }

                // Hold Steady Night Mode Countdown Overlay
                if (uiState.holdSteadyRemainingSec != null) {
                    HoldSteadyIndicator(
                        remainingSec = uiState.holdSteadyRemainingSec,
                        stability = uiState.stabilityAssessment,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Processing Night Shot Indicator
                if (uiState.isProcessingNightShot) {
                    NightProcessingIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                // Shutter blink visual feedback
                AnimatedVisibility(
                    visible = uiState.isShutterBlinking,
                    enter = fadeIn(tween(20)),
                    exit = fadeOut(tween(80)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.85f)),
                    )
                }
            }

            // Real-Time Intelligent Scene & Acquisition Hint Pill
            IntelligentSceneHintPill(
                scene = uiState.sceneClassification,
                strategy = uiState.captureStrategy,
                motion = uiState.motionState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topInset + 64.dp),
            )

            // Preview Low-Light Boost framing aid pill (shown when Night mode is active)
            if (currentMode == CameraMode.NIGHT) {
                PreviewBoostBadge(
                    isActive = uiState.isPreviewBoostActive,
                    onClick = { viewModel.togglePreviewLowLightBoost() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = topInset + 64.dp, start = 16.dp),
                )
            }

            // Portrait Mode Status & Depth Badge (shown when Portrait mode is active)
            if (currentMode == CameraMode.PORTRAIT) {
                PortraitStatusBadge(
                    aperture = uiState.portraitAperture,
                    isProcessing = uiState.isProcessingPortraitShot,
                    faceCount = uiState.detectedFaces.size,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = topInset + 64.dp, start = 16.dp),
                )
            }

            // Live Luminance Histogram Overlay (in Pro Mode or when toggled)
            if (uiState.isHistogramVisible || currentMode == CameraMode.PRO) {
                HistogramOverlay(
                    data = uiState.histogramData,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = topInset + 56.dp, end = 16.dp),
                )
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
                    horizontal = OptiLensTheme.spacing.m,
                    vertical = OptiLensTheme.spacing.xs,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Flash Toggle
                OptiIconButton(
                    icon = when (uiState.flashMode) {
                        FlashMode.AUTO -> Icons.Default.FlashAuto
                        FlashMode.ON   -> Icons.Default.FlashOn
                        FlashMode.OFF  -> Icons.Default.FlashOff
                        FlashMode.TORCH -> Icons.Default.FlashOn
                    },
                    contentDescription = "Flash mode: ${uiState.flashMode.name}",
                    variant = if (uiState.flashMode != FlashMode.OFF) OptiIconButtonVariant.OVERLAY_ACTIVE else OptiIconButtonVariant.OVERLAY,
                    onClick = { viewModel.toggleFlashMode() },
                )

                // Aspect Ratio Toggle (4:3, 16:9, 1:1)
                OptiIconButton(
                    icon = Icons.Default.AspectRatio,
                    contentDescription = "Aspect ratio: ${uiState.aspectRatio.label}",
                    variant = OptiIconButtonVariant.OVERLAY,
                    onClick = { viewModel.toggleAspectRatio() },
                )

                // Timer Toggle (Off, 3s, 10s)
                OptiIconButton(
                    icon = when (uiState.timerState) {
                        TimerState.OFF    -> Icons.Default.Timer
                        TimerState.SEC_3  -> Icons.Default.Timer3
                        TimerState.SEC_10 -> Icons.Default.Timer10
                    },
                    contentDescription = "Timer: ${uiState.timerState.seconds}s",
                    variant = if (uiState.timerState != TimerState.OFF) OptiIconButtonVariant.OVERLAY_ACTIVE else OptiIconButtonVariant.OVERLAY,
                    onClick = { viewModel.toggleTimer() },
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

                // Live Histogram Toggle
                OptiIconButton(
                    icon = Icons.Default.Leaderboard,
                    contentDescription = "Toggle Histogram",
                    variant = if (uiState.isHistogramVisible) OptiIconButtonVariant.OVERLAY_ACTIVE else OptiIconButtonVariant.OVERLAY,
                    onClick = { viewModel.toggleHistogram() },
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

        // ── 3. Bottom Scrim & Controls ─────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(overlayColors.scrimBackground)
                .padding(bottom = bottomInset.coerceAtLeast(OptiLensTheme.spacing.l))
                .padding(top = OptiLensTheme.spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Pro Mode Manual Control Bar (Shown when PRO mode is selected)
            if (currentMode == CameraMode.PRO) {
                ProControlsBar(
                    proState = uiState.proState,
                    onIsoChanged = { viewModel.setIso(it) },
                    onShutterSpeedChanged = { viewModel.setShutterSpeed(it) },
                    onFocusDistanceChanged = { viewModel.setFocusDistance(it) },
                    onWhiteBalanceChanged = { viewModel.setWhiteBalance(it) },
                    onEvChanged = { viewModel.onExposureCompensationChanged(it) },
                    onResetToAuto = { viewModel.resetProToAuto() },
                    modifier = Modifier.padding(bottom = OptiLensTheme.spacing.xs),
                )
            }

            // Portrait Mode Aperture Bar (Shown when PORTRAIT mode is selected)
            if (currentMode == CameraMode.PORTRAIT) {
                PortraitApertureSelector(
                    currentAperture = uiState.portraitAperture,
                    onApertureSelected = { viewModel.setPortraitAperture(it) },
                    modifier = Modifier.padding(bottom = OptiLensTheme.spacing.xs),
                )
            }

            // Truthful Zoom Selector Bar (derives optical vs digital crop)
            val activeStops = if (uiState.zoomStops.isNotEmpty()) {
                uiState.zoomStops
            } else {
                listOf(
                    ZoomStop(ratio = 0.6f, label = "0.6x", isOptical = true),
                    ZoomStop(ratio = 1.0f, label = "1x", isOptical = true),
                    ZoomStop(ratio = 2.0f, label = "2x", isOptical = false),
                    ZoomStop(ratio = 5.0f, label = "5x", isOptical = true),
                )
            }

            TruthfulZoomSelector(
                currentZoom = uiState.zoomState.currentZoom,
                onZoomSelected = { ratio -> viewModel.onZoomRatioChanged(ratio) },
                zoomStops = activeStops,
                modifier = Modifier.padding(bottom = OptiLensTheme.spacing.xs),
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

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.s))

            // Main Shutter Row: Gallery Shortcut | Shutter Button | Camera Flip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OptiLensTheme.spacing.xxl),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gallery Shortcut Button with Recent Thumbnail
                Box(
                    modifier = Modifier
                        .size(OptiLensTheme.iconSizes.minTouchTarget)
                        .clip(CircleShape)
                        .background(overlayColors.controlSurface)
                        .border(1.dp, overlayColors.controlBorder, CircleShape)
                        .clickable(
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                val lastUri = uiState.lastCapturedPhoto?.uri
                                if (lastUri != null && onNavigateToPhotoReview != null) {
                                    onNavigateToPhotoReview(lastUri)
                                } else {
                                    onNavigateToGallery()
                                }
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = uiState.lastCapturedPhoto?.thumbnail
                    if (thumb != null) {
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Open Gallery with recent photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Open Gallery",
                            tint = overlayColors.controlOnSurface,
                            modifier = Modifier.size(OptiLensTheme.iconSizes.standard),
                        )
                    }
                }

                // Tactile Shutter Button with timer countdown trigger, night mode, or portrait mode
                CameraShutterButton(
                    onClick = {
                        onShutterClick()
                        if (currentMode == CameraMode.NIGHT) {
                            viewModel.takeNightPhoto(targetRotation = Surface.ROTATION_0)
                        } else if (currentMode == CameraMode.PORTRAIT) {
                            viewModel.takePortraitPhoto(targetRotation = Surface.ROTATION_0)
                        } else {
                            viewModel.takePhotoWithTimer(targetRotation = Surface.ROTATION_0)
                        }
                    },
                    isVideo = currentMode == CameraMode.VIDEO,
                    enabled = !uiState.isCapturing && !uiState.isBurstCapturing && !uiState.isProcessingNightShot && !uiState.isProcessingPortraitShot && uiState.timerCountdown == null,
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
                            onClick = { viewModel.flipCamera() },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = "Flip camera to ${if (uiState.isFrontCamera) "rear" else "front"}",
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
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val overlayColors = OptiLensTheme.overlayColors

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.90f else 1.0f,
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
                enabled = enabled,
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
                .size(if (isPressed && enabled) 52.dp else 60.dp)
                .clip(coreShape)
                .background(if (enabled) coreColor else coreColor.copy(alpha = 0.5f)),
        )
    }
}

/**
 * Animated reticle displayed on tap-to-focus position.
 */
@Composable
fun FocusReticle(
    offset: Offset,
    modifier: Modifier = Modifier,
) {
    val scale = remember { Animatable(1.3f) }
    val alpha = remember { Animatable(0.4f) }

    LaunchedEffect(offset) {
        scale.snapTo(1.3f)
        alpha.snapTo(0.4f)
        scale.animateTo(1.0f, tween(durationMillis = 200))
        alpha.animateTo(1.0f, tween(durationMillis = 150))
    }

    val reticleSize = 64.dp
    val overlayColors = OptiLensTheme.overlayColors

    Box(
        modifier = modifier
            .offset {
                IntOffset(
                    x = (offset.x - reticleSize.toPx() / 2).roundToInt(),
                    y = (offset.y - reticleSize.toPx() / 2).roundToInt(),
                )
            }
            .size(reticleSize)
            .scale(scale.value)
            .border(1.5.dp, overlayColors.focusSuccess.copy(alpha = alpha.value), CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .background(overlayColors.focusSuccess.copy(alpha = alpha.value), CircleShape),
        )
    }
}

/**
 * Truthful zoom pill selector derived from actual hardware.
 * Never mislabels a digital crop as optical.
 */
@Composable
fun TruthfulZoomSelector(
    currentZoom: Float,
    onZoomSelected: (Float) -> Unit,
    zoomStops: List<ZoomStop>,
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
        zoomStops.forEach { stop ->
            val isSelected = (kotlin.math.abs(currentZoom - stop.ratio) < 0.15f)

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) overlayColors.controlSurfaceActive else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true, radius = 18.dp),
                        onClick = { onZoomSelected(stop.ratio) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stop.label,
                        style = OptiLensCameraTypography.zoomReadout,
                        color = if (isSelected) overlayColors.controlOnSurfaceActive else overlayColors.controlOnSurface,
                    )
                    // Visual indicator for dedicated optical lens
                    if (stop.isOptical && stop.ratio != 1.0f) {
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .background(if (isSelected) overlayColors.activeAccent else overlayColors.controlOnSurface.copy(alpha = 0.5f), CircleShape)
                        )
                    }
                }
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
 * Horizon tilt balance indicator line driven by hardware sensors.
 * Rotates with device tilt and turns green when level within ±1.0 degree.
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
            .rotate(-tiltAngleDegrees)
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

/**
 * Hold steady countdown overlay for Night mode long-exposure/burst capture.
 */
@Composable
fun HoldSteadyIndicator(
    remainingSec: Float?,
    stability: StabilityAssessment?,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val isUnsteady = stability?.classification == StabilityClassification.UNSTEADY
    val isTripod = stability?.classification == StabilityClassification.TRIPOD

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(
                width = 1.dp,
                color = if (isUnsteady) overlayColors.horizonWarning else overlayColors.controlBorder,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = if (isUnsteady) overlayColors.horizonWarning else overlayColors.activeAccent,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isTripod) "Tripod Night Exposure" else "Hold Still",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
            )
            remainingSec?.let { sec ->
                Text(
                    text = "${String.format(java.util.Locale.US, "%.1f", sec)}s remaining",
                    style = MaterialTheme.typography.bodyMedium,
                    color = overlayColors.controlOnSurface,
                )
            }
            if (isUnsteady) {
                Text(
                    text = "High movement detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = overlayColors.horizonWarning,
                )
            }
        }
    }
}

/**
 * Processing indicator shown while post-processing multi-frame night captures.
 */
@Composable
fun NightProcessingIndicator(
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.80f))
            .border(1.dp, overlayColors.controlBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = overlayColors.activeAccent,
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Enhancing Low-Light...",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}

/**
 * Preview low-light boost badge indicating preview is boosted for framing aid only.
 */
@Composable
fun PreviewBoostBadge(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (isActive) overlayColors.activeAccent.copy(alpha = 0.25f)
                else overlayColors.scrimBackground
            )
            .border(
                width = 1.dp,
                color = if (isActive) overlayColors.activeAccent else overlayColors.controlBorder,
                shape = CircleShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = if (isActive) overlayColors.activeAccent else overlayColors.controlOnSurface,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (isActive) "Boost: On (Framing)" else "Boost: Off",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                ),
                color = if (isActive) overlayColors.activeAccent else overlayColors.controlOnSurface,
            )
        }
    }
}

@Composable
private fun PortraitApertureSelector(
    currentAperture: PortraitAperture,
    onApertureSelected: (PortraitAperture) -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = OptiLensTheme.spacing.m),
        horizontalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(PortraitAperture.entries) { aperture ->
            val isSelected = aperture == currentAperture
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) overlayColors.activeAccent else overlayColors.controlSurface)
                    .border(
                        width = 1.dp,
                        color = if (isSelected) overlayColors.activeAccent else overlayColors.controlBorder,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onApertureSelected(aperture) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = aperture.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp,
                    ),
                    color = if (isSelected) Color.Black else overlayColors.controlOnSurface,
                )
            }
        }
    }
}

@Composable
private fun PortraitStatusBadge(
    aperture: PortraitAperture,
    isProcessing: Boolean,
    faceCount: Int,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(overlayColors.scrimBackground)
            .border(width = 1.dp, color = overlayColors.controlBorder, shape = CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isProcessing) "Refining portrait..."
                   else if (faceCount > 0) "Portrait (${faceCount} face${if (faceCount > 1) "s" else ""})"
                   else "Portrait ${aperture.label}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = overlayColors.controlOnSurface,
        )
    }
}
