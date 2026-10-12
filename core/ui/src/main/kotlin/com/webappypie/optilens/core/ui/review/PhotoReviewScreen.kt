package com.webappypie.optilens.core.ui.review

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.webappypie.optilens.core.ui.R
import com.webappypie.optilens.core.ui.text.UiText

private val Accent = Color(0xFFFFB74D)
private val OnAccent = Color(0xFF2B1C00)
private val PanelColor = Color(0xFF121418)
private val ChipColor = Color(0xFF1E2128)
private val MutedText = Color(0xFFA9B0BD)
private val SuccessGreen = Color(0xFF34D399)

/**
 * Photo preview with one-tap on-device enhancement, Original / Enhanced / Compare views,
 * save-as-copy and (right after capture) Retake. Works offline; nothing is uploaded.
 */
@Composable
fun PhotoReviewScreen(
    photoUri: String,
    onNavigateBack: () -> Unit,
    onNavigateToAiTools: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    fromCapture: Boolean = false,
    viewModel: PhotoReviewViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmRetake by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(photoUri) {
        viewModel.loadPhoto(photoUri)
    }
    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it.resolve(context))
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(uiState.isDiscarded) {
        if (uiState.isDiscarded) onNavigateBack()
    }
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDeleteConsentResult(result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let {
            deleteConsentLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    val hasUnsavedEnhancement = uiState.isEnhanced && !uiState.isSaved
    BackHandler(enabled = hasUnsavedEnhancement || uiState.isEnhancing) {
        if (uiState.isEnhancing) viewModel.cancelEnhance() else confirmLeave = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val original = uiState.originalBitmap
        if (original != null) {
            BeforeAfterSplitViewer(
                originalBitmap = original,
                enhancedBitmap = uiState.enhancedBitmap,
                splitFraction = uiState.splitFraction,
                onSplitFractionChanged = viewModel::setSplitFraction,
                isHoldingToCompare = uiState.isHoldingToCompare,
                onHoldingToCompareChanged = viewModel::setHoldingToCompare,
                viewMode = uiState.viewMode,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (uiState.errorMessage != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MutedText, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(uiState.errorMessage!!.resolve(), color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(16.dp))
                        OutlinedButton(onClick = onNavigateBack) { Text(stringResource(R.string.review_go_back), color = Color.White) }
                    }
                } else {
                    CircularProgressIndicator(color = Accent, strokeWidth = 3.dp)
                }
            }
        }

        // Top bar over a soft scrim so controls stay legible on bright photos.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent))),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIconButton(
                    icon = if (fromCapture) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                    description = stringResource(if (fromCapture) R.string.review_close_preview else R.string.common_back),
                    onClick = {
                        when {
                            uiState.isEnhancing -> viewModel.cancelEnhance()
                            hasUnsavedEnhancement -> confirmLeave = true
                            else -> onNavigateBack()
                        }
                    },
                )
                Text(
                    text = stringResource(
                        when {
                            uiState.isEnhancing -> R.string.review_title_enhancing
                            uiState.isEnhanced -> R.string.review_title_enhanced
                            fromCapture -> R.string.review_title_preview
                            else -> R.string.review_title_photo
                        },
                    ),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                RoundIconButton(
                    icon = Icons.Default.Share,
                    description = stringResource(if (uiState.savedUri != null) R.string.review_share_enhanced else R.string.common_share_photo),
                    enabled = uiState.photoUri.isNotBlank() && original != null,
                    onClick = {
                        val shareUri = uiState.savedUri ?: uiState.photoUri
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(shareUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.common_share_photo)))
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
        ) {
            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.CenterHorizontally))

            AnimatedVisibility(
                visible = uiState.isEnhanced && !uiState.isEnhancing,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                ViewModeSelector(
                    selected = uiState.viewMode,
                    onSelect = viewModel::setViewMode,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(PanelColor.copy(alpha = 0.94f))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                uiState.errorMessage?.takeIf { original != null }?.let { error ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33F87171))
                            .clickable(onClickLabel = stringResource(R.string.common_dismiss)) { viewModel.clearError() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = Color(0xFFF87171), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(error.resolve(), color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_dismiss), tint = MutedText, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                val panel = when {
                    uiState.isEnhancing -> PanelState.PROCESSING
                    uiState.isEnhanced -> PanelState.RESULT
                    else -> PanelState.IDLE
                }
                AnimatedContent(
                    targetState = panel,
                    transitionSpec = {
                        (fadeIn() + slideInVertically { it / 6 }) togetherWith (fadeOut() + slideOutVertically { it / 6 })
                    },
                    label = "reviewPanel",
                ) { state ->
                    when (state) {
                        PanelState.PROCESSING -> ProcessingPanel(
                            progress = uiState.enhanceProgress,
                            onCancel = viewModel::cancelEnhance,
                        )
                        PanelState.RESULT -> ResultPanel(
                            notes = uiState.enhancementNotes,
                            isSaving = uiState.isSaving,
                            isSaved = uiState.isSaved,
                            fromCapture = fromCapture,
                            onRevert = viewModel::revert,
                            onSave = viewModel::saveEnhanced,
                            onDone = onNavigateBack,
                        )
                        PanelState.IDLE -> IdlePanel(
                            enabled = original != null && !uiState.isDiscarding,
                            fromCapture = fromCapture,
                            captureProcessing = uiState.captureProcessing,
                            onEnhance = viewModel::enhance,
                            onEditTools = { onNavigateToAiTools(photoUri) },
                            onRetake = { confirmRetake = true },
                        )
                    }
                }
            }
        }
    }

    if (confirmRetake) {
        AlertDialog(
            onDismissRequest = { confirmRetake = false },
            title = { Text(stringResource(R.string.review_retake_title)) },
            text = { Text(stringResource(R.string.review_retake_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRetake = false
                    viewModel.discardCapturedPhoto()
                }) { Text(stringResource(R.string.review_retake_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRetake = false }) { Text(stringResource(R.string.review_keep)) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.review_leave_title)) },
            text = { Text(stringResource(R.string.review_leave_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    viewModel.saveEnhanced()
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.review_discard)) }
            },
        )
    }
}

private enum class PanelState { IDLE, PROCESSING, RESULT }

private val CAPTURE_NOTE = Regex("^(Night|HDR|Moon) · (\\d+)-frame merge(, tone-mapped)?$")

/** Rebuilds the EXIF capture note ("Night · 4-frame merge, tone-mapped") in the user's language. */
@Composable
private fun localizedCaptureProcessing(raw: String): String {
    val match = CAPTURE_NOTE.matchEntire(raw) ?: return raw
    val mode = stringResource(
        when (match.groupValues[1]) {
            "Night" -> R.string.mode_night
            "HDR" -> R.string.mode_hdr
            else -> R.string.mode_moon
        },
    )
    val frames = match.groupValues[2].toInt()
    val toned = match.groupValues[3].isNotEmpty()
    return stringResource(if (toned) R.string.review_processing_merge_toned else R.string.review_processing_merge, mode, frames)
}

@Composable
private fun IdlePanel(
    enabled: Boolean,
    fromCapture: Boolean,
    captureProcessing: String?,
    onEnhance: () -> Unit,
    onEditTools: () -> Unit,
    onRetake: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (captureProcessing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.review_capture_processing, localizedCaptureProcessing(captureProcessing)),
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Button(
            onClick = onEnhance,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.review_enhance_photo), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (fromCapture) {
                SecondaryAction(Icons.Default.CameraAlt, stringResource(R.string.review_retake), enabled, onRetake, Modifier.weight(1f))
            }
            SecondaryAction(Icons.Default.Tune, stringResource(R.string.review_edit_tools), enabled, onEditTools, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = MutedText, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.review_on_device), color = MutedText, fontSize = 12.sp, modifier = Modifier.weight(1f))
        }
        ComingSoonRow()
    }
}

/** The future cloud feature, shown as clearly unavailable — not a button. */
@Composable
private fun ComingSoonRow() {
    val a11y = stringResource(R.string.review_ai_enhance_a11y)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ChipColor)
            .semantics { contentDescription = a11y }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(stringResource(R.string.review_ai_enhance), color = MutedText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            stringResource(R.string.review_coming_soon),
            color = MutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .border(1.dp, MutedText.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ProcessingPanel(progress: Float, onCancel: () -> Unit) {
    val animated by animateFloatAsState(targetValue = progress, label = "enhanceProgress")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.review_enhancing_title), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (progress < 0.15f) {
                        stringResource(R.string.review_enhancing_analysing)
                    } else {
                        stringResource(R.string.review_enhancing_progress, (progress * 100).toInt())
                    },
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel), color = Color.White) }
        }
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = Accent,
            trackColor = Color.White.copy(alpha = 0.15f),
            drawStopIndicator = {},
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultPanel(
    notes: List<UiText>,
    isSaving: Boolean,
    isSaved: Boolean,
    fromCapture: Boolean,
    onRevert: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.review_compare_hint),
            color = MutedText,
            fontSize = 12.sp,
        )
        if (notes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                notes.forEach { note ->
                    Text(
                        text = note.resolve(),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ChipColor)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (isSaved) {
                SecondaryAction(
                    icon = if (fromCapture) Icons.Default.CameraAlt else Icons.AutoMirrored.Filled.ArrowBack,
                    label = stringResource(if (fromCapture) R.string.review_camera else R.string.review_done),
                    enabled = true,
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                )
            } else {
                SecondaryAction(Icons.Default.Close, stringResource(R.string.review_revert), !isSaving, onRevert, Modifier.weight(0.75f))
            }
            Button(
                onClick = onSave,
                enabled = !isSaving && !isSaved,
                modifier = Modifier
                    .weight(1.25f)
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = if (isSaved) SuccessGreen.copy(alpha = 0.2f) else Accent.copy(alpha = 0.4f),
                    disabledContentColor = if (isSaved) SuccessGreen else OnAccent,
                ),
            ) {
                when {
                    isSaving -> CircularProgressIndicator(color = OnAccent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                    isSaved -> {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.review_saved), fontWeight = FontWeight.Bold)
                    }
                    else -> {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        // A new file is always written; the original is never replaced.
                        Text(stringResource(R.string.review_save_enhanced), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeSelector(
    selected: ReviewViewMode,
    onSelect: (ReviewViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(4.dp),
    ) {
        listOf(
            ReviewViewMode.ORIGINAL to stringResource(R.string.review_view_original),
            ReviewViewMode.ENHANCED to stringResource(R.string.review_view_enhanced),
            ReviewViewMode.COMPARE to stringResource(R.string.review_view_compare),
        ).forEach { (mode, label) ->
            val isSelected = mode == selected
            Text(
                text = label,
                color = if (isSelected) OnAccent else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) Accent else Color.Transparent)
                    .clickable(role = Role.Tab, onClickLabel = stringResource(R.string.review_show_view, label)) { onSelect(mode) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun SecondaryAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(26.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(icon, contentDescription = description, tint = if (enabled) Color.White else MutedText)
    }
}
