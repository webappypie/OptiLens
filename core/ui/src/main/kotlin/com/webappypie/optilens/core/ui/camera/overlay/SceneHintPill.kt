package com.webappypie.optilens.core.ui.camera.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * Non-intrusive floating pill badge rendering subtle contextual scene indicators
 * or acquisition hints (e.g. "Night", "HDR", "Hold steady", "Document").
 */
@Composable
fun SceneHintPill(
    scene: SceneClassification,
    strategy: CaptureStrategy,
    motion: MotionState,
    modifier: Modifier = Modifier,
) {
    val displayInfo = resolveHintDisplay(scene, strategy, motion)

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
                    .border(0.5.dp, displayInfo.accentColor.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = displayInfo.icon,
                    contentDescription = displayInfo.text,
                    tint = displayInfo.accentColor,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = displayInfo.text,
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
    val text: String,
    val accentColor: Color = Color(0xFFE5C07B),
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
            text = "Hold steady",
            accentColor = Color(0xFFE06C75),
        )
    }

    // 2. Acquisition mode suggestions
    if (strategy.uiHint == CaptureUiHint.NIGHT_SUGGESTED) {
        return HintDisplay(
            icon = Icons.Default.DarkMode,
            text = "Night suggested",
            accentColor = Color(0xFF61AFEF),
        )
    }

    if (strategy.uiHint == CaptureUiHint.HDR_SUGGESTED || strategy.uiHint == CaptureUiHint.BACKLIGHT_DETECTED) {
        return HintDisplay(
            icon = Icons.Default.HdrOn,
            text = if (strategy.uiHint == CaptureUiHint.BACKLIGHT_DETECTED) "Backlight" else "HDR",
            accentColor = Color(0xFFE5C07B),
        )
    }

    if (strategy.uiHint == CaptureUiHint.DOCUMENT_DETECTED || scene.primaryScene == SceneType.DOCUMENT) {
        return HintDisplay(
            icon = Icons.Default.Description,
            text = "Document",
            accentColor = Color(0xFF98C379),
        )
    }

    // 3. Subtle Scene Chips (only if confidence and temporal stability meet thresholds)
    if (scene.primaryScene != SceneType.GENERAL && scene.confidence >= 0.60f && scene.stabilityScore >= 0.50f) {
        val (icon, color) = when (scene.primaryScene) {
            SceneType.PORTRAIT -> Icons.Default.AutoAwesome to Color(0xFFE5C07B)
            SceneType.FOOD -> Icons.Default.Restaurant to Color(0xFFD19A66)
            SceneType.NATURE, SceneType.PLANT -> Icons.Default.Park to Color(0xFF98C379)
            SceneType.SKY -> Icons.Default.WbSunny to Color(0xFF61AFEF)
            SceneType.PET, SceneType.WILDLIFE -> Icons.Default.Pets to Color(0xFFC678DD)
            SceneType.LOW_LIGHT -> Icons.Default.DarkMode to Color(0xFF61AFEF)
            else -> return null
        }

        return HintDisplay(
            icon = icon,
            text = scene.primaryScene.displayName,
            accentColor = color,
        )
    }

    return null
}
