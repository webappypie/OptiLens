package com.webappypie.optilens.core.ui.camera.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HdrOn
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.webappypie.optilens.core.ui.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.model.MotionState
import com.webappypie.optilens.core.camera.model.SceneClassification
import com.webappypie.optilens.core.camera.model.SceneType
import com.webappypie.optilens.core.camera.strategy.CaptureStrategy
import com.webappypie.optilens.core.camera.strategy.CaptureUiHint

/** What tapping a hint pill does, when the camera screen offers it. */
enum class SceneHintAction(@androidx.annotation.StringRes val labelRes: Int) {
    ENABLE_HDR(R.string.hint_tap_hdr),
    SWITCH_TO_NIGHT(R.string.hint_tap_night),
}

/**
 * Non-intrusive floating pill badge rendering subtle contextual scene indicators
 * or acquisition hints (e.g. "Night suggested", "High contrast", "Hold steady", "Document").
 * A hint becomes tappable only when its action is in [availableActions].
 */
@Composable
fun SceneHintPill(
    scene: SceneClassification,
    strategy: CaptureStrategy,
    motion: MotionState,
    modifier: Modifier = Modifier,
    availableActions: Set<SceneHintAction> = emptySet(),
    onAction: (SceneHintAction) -> Unit = {},
) {
    val displayInfo = resolveHintDisplay(scene, strategy, motion)
    val action = displayInfo?.action?.takeIf { it in availableActions }
    val text = displayInfo?.let { stringResource(it.textRes) }.orEmpty()
    val actionLabel = action?.let { stringResource(it.labelRes) }

    AnimatedVisibility(
        visible = displayInfo != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        if (displayInfo != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xDD18181A))
                    .border(0.5.dp, displayInfo.accentColor.copy(alpha = if (action != null) 0.8f else 0.35f), CircleShape)
                    .then(
                        if (action != null) {
                            Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = actionLabel) {
                                onAction(action)
                            }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = if (action != null) 10.dp else 6.dp),
            ) {
                Icon(
                    imageVector = displayInfo.icon,
                    contentDescription = text,
                    tint = displayInfo.accentColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = if (actionLabel != null) stringResource(R.string.hint_with_action, text, actionLabel) else text,
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

private data class HintDisplay(
    val icon: ImageVector,
    @androidx.annotation.StringRes val textRes: Int,
    val accentColor: Color = Color(0xFFE5C07B),
    val action: SceneHintAction? = null,
)

private fun resolveHintDisplay(
    scene: SceneClassification,
    strategy: CaptureStrategy,
    motion: MotionState,
): HintDisplay? {
    // 1. High priority warnings (Camera Shake)
    if (strategy.uiHint == CaptureUiHint.HOLD_STEADY) {
        return HintDisplay(
            icon = Icons.Default.PanTool,
            textRes = R.string.hint_hold_steady,
            accentColor = Color(0xFFE06C75),
        )
    }

    // 2. Acquisition mode suggestions
    if (strategy.uiHint == CaptureUiHint.NIGHT_SUGGESTED) {
        return HintDisplay(
            icon = Icons.Default.DarkMode,
            textRes = R.string.hint_night,
            accentColor = Color(0xFF61AFEF),
            action = SceneHintAction.SWITCH_TO_NIGHT,
        )
    }

    if (strategy.uiHint == CaptureUiHint.HDR_SUGGESTED || strategy.uiHint == CaptureUiHint.BACKLIGHT_DETECTED) {
        return HintDisplay(
            icon = Icons.Default.HdrOn,
            // Describes the scene only: there is no HDR capture mode to suggest yet.
            textRes = if (strategy.uiHint == CaptureUiHint.BACKLIGHT_DETECTED) R.string.hint_backlight else R.string.hint_high_contrast,
            accentColor = Color(0xFFE5C07B),
            action = SceneHintAction.ENABLE_HDR,
        )
    }

    if (strategy.uiHint == CaptureUiHint.DOCUMENT_DETECTED || scene.primaryScene == SceneType.DOCUMENT) {
        return HintDisplay(
            icon = Icons.Default.Description,
            textRes = R.string.hint_document,
            accentColor = Color(0xFF98C379),
        )
    }

    // 3. Scene chips only for signals the app actually measures: detected faces (ML Kit) and
    // low light (luminance). Colour-only guesses (plant, sky, food, animals) are not shown.
    if (scene.primaryScene != SceneType.GENERAL && scene.confidence >= 0.60f && scene.stabilityScore >= 0.50f) {
        return when (scene.primaryScene) {
            SceneType.PORTRAIT -> HintDisplay(Icons.Default.AutoAwesome, R.string.mode_portrait, Color(0xFFE5C07B))
            SceneType.LOW_LIGHT -> HintDisplay(Icons.Default.DarkMode, R.string.hint_low_light, Color(0xFF61AFEF))
            else -> null
        }
    }

    return null
}
