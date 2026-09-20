package com.webappypie.optilens.core.ui.aitools

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webappypie.optilens.core.imaging.ai.AiExecutionStage
import com.webappypie.optilens.core.imaging.ai.AiToolType
import com.webappypie.optilens.core.ui.review.BeforeAfterSplitViewer

@Composable
fun AiToolsScreen(
    photoUri: String,
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AiToolsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val inpaintingStrokes = remember { mutableStateListOf<BrushStroke>() }
    val canvasSize = remember { androidx.compose.runtime.mutableStateOf(IntSize.Zero) }

    LaunchedEffect(photoUri) {
        viewModel.loadPhoto(photoUri)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // 1. Center Image / BeforeAfter Split Viewer
        if (uiState.originalBitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize.value = it }
            ) {
                BeforeAfterSplitViewer(
                    originalBitmap = uiState.originalBitmap!!,
                    enhancedBitmap = uiState.currentBitmap,
                    splitFraction = uiState.splitFraction,
                    onSplitFractionChanged = viewModel::setSplitFraction,
                    isHoldingToCompare = uiState.isHoldingToCompare,
                    onHoldingToCompareChanged = viewModel::setHoldingToCompare,
                    modifier = Modifier.fillMaxSize(),
                )

                // When Inpainting tool is active, overlay interactive drawing canvas
                if (uiState.selectedTool == AiToolType.INPAINTING) {
                    InpaintingMaskCanvas(
                        brushSize = uiState.inpaintingBrushSize,
                        strokes = inpaintingStrokes,
                        modifier = Modifier.fillMaxSize(),
                        enabled = !uiState.isProcessing,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                } else {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 2. Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }

            Text(
                text = "Advanced AI Tools",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Undo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = uiState.canUndo,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (uiState.canUndo) 0.5f else 0.2f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (uiState.canUndo) Color.White else Color.Gray,
                    )
                }

                // Redo
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = uiState.canRedo,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = if (uiState.canRedo) 0.5f else 0.2f)),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (uiState.canRedo) Color.White else Color.Gray,
                    )
                }

                // Revert All
                IconButton(
                    onClick = {
                        inpaintingStrokes.clear()
                        viewModel.revertAll()
                    },
                    enabled = uiState.currentBitmap != uiState.originalBitmap,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Revert All",
                        tint = if (uiState.currentBitmap != uiState.originalBitmap) Color.White else Color.Gray,
                    )
                }

                // Share
                if (uiState.isSaved && uiState.savedUri != null) {
                    IconButton(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/jpeg"
                                putExtra(Intent.EXTRA_STREAM, Uri.parse(uiState.savedUri))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share AI Enhanced Photo"))
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // 3. AI Fidelity Disclosure Banner
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp)
        ) {
            AiFidelityDisclosureBanner(
                message = uiState.disclosureMessage,
                visible = uiState.disclosureMessage != null,
            )
        }

        // 4. Processing Overlay
        if (uiState.isProcessing) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.85f),
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = uiState.currentStage?.displayName ?: "Processing...",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    val progress = (uiState.currentStage?.stageIndex ?: 1).toFloat() / 5f
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .width(180.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
        }

        // 5. Bottom Control Sheet
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF141416).copy(alpha = 0.92f),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tool Category Tabs
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AiToolType.entries.forEach { tool ->
                        val isSelected = uiState.selectedTool == tool
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectTool(tool) },
                            label = { Text(tool.displayName, fontSize = 12.sp) },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White,
                            ),
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contextual Tool Parameter Controls
                when (uiState.selectedTool) {
                    AiToolType.DEBLUR -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Strength: ${(uiState.deblurStrength * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                            )
                            uiState.blurClassification?.let {
                                Text(
                                    text = it.blurType.displayName,
                                    color = Color(0xFFFFB74D),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Slider(
                            value = uiState.deblurStrength,
                            onValueChange = viewModel::updateDeblurStrength,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AiToolType.INPAINTING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Brush: ${uiState.inpaintingBrushSize.toInt()} px",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "Clear Brush",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { inpaintingStrokes.clear() },
                            )
                        }
                        Slider(
                            value = uiState.inpaintingBrushSize,
                            onValueChange = viewModel::updateBrushSize,
                            valueRange = 10f..100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AiToolType.REFLECTION_REDUCTION -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Reflection Suppression: ${(uiState.reflectionStrength * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                            )
                        }
                        Slider(
                            value = uiState.reflectionStrength,
                            onValueChange = viewModel::updateReflectionStrength,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    AiToolType.UPSCALE -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Target Magnification:",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = uiState.upscaleFactor == 2,
                                    onClick = { viewModel.updateUpscaleFactor(2) },
                                    label = { Text("2x Upscale") },
                                    shape = RoundedCornerShape(12.dp),
                                )
                                FilterChip(
                                    selected = uiState.upscaleFactor == 4,
                                    onClick = { viewModel.updateUpscaleFactor(4) },
                                    label = { Text("4x Upscale") },
                                    shape = RoundedCornerShape(12.dp),
                                )
                            }
                        }
                    }
                    AiToolType.RESTORATION -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Restoration Level: ${(uiState.restorationStrength * 100).toInt()}%",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                            )
                        }
                        Slider(
                            value = uiState.restorationStrength,
                            onValueChange = viewModel::updateRestorationStrength,
                            valueRange = 0.1f..1.0f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons Row
                if (uiState.isGated) {
                    Button(
                        onClick = onNavigateToUpgrade,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.Black,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlock AI Tools Pack",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = {
                                if (uiState.selectedTool == AiToolType.INPAINTING) {
                                    val bmp = uiState.currentBitmap
                                    if (bmp != null && inpaintingStrokes.isNotEmpty()) {
                                        val maskBmp = generateMaskBitmapFromStrokes(
                                            strokes = inpaintingStrokes,
                                            targetWidth = bmp.width,
                                            targetHeight = bmp.height,
                                            canvasWidth = canvasSize.value.width.toFloat().coerceAtLeast(1f),
                                            canvasHeight = canvasSize.value.height.toFloat().coerceAtLeast(1f),
                                        )
                                        viewModel.applyCurrentTool(maskBmp)
                                        inpaintingStrokes.clear()
                                    } else {
                                        viewModel.applyCurrentTool(null)
                                    }
                                } else {
                                    viewModel.applyCurrentTool(null)
                                }
                            },
                            enabled = !uiState.isProcessing,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Black,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.selectedTool == AiToolType.INPAINTING) "Erase Selection" else "Apply ${uiState.selectedTool.displayName}",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Button(
                            onClick = { viewModel.saveCopy() },
                            enabled = uiState.currentBitmap != null && !uiState.isSaved && !uiState.isProcessing,
                            modifier = Modifier
                                .weight(0.8f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isSaved) Color(0xFF2E7D32) else Color.White.copy(alpha = 0.15f),
                            ),
                        ) {
                            Text(
                                text = if (uiState.isSaved) "Saved ✓" else "Save Copy",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }
}
