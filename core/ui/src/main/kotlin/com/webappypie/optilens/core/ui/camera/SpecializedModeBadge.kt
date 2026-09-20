package com.webappypie.optilens.core.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.document.DocumentColorMode
import com.webappypie.optilens.core.ui.theme.OptiLensCameraTypography
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Viewfinder indicator pill for specialized shooting modes (Best Shot, Pet, Food).
 */
@Composable
fun SpecializedModeBadge(
    text: String,
    isProcessing: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accentColor: Color = Color(0xFFFFD54F),
) {
    val overlayColors = OptiLensTheme.overlayColors

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(overlayColors.controlSurface)
            .border(1.dp, overlayColors.controlBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isProcessing) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = accentColor,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = text,
            style = OptiLensCameraTypography.readout.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            ),
        )
    }
}

/**
 * Viewfinder controls for Document Mode featuring Color / Grayscale / B&W mode selector chips.
 */
@Composable
fun DocumentModeControls(
    currentColorMode: DocumentColorMode,
    onSelectColorMode: (DocumentColorMode) -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(overlayColors.controlSurface)
                .border(1.dp, overlayColors.controlBorder, RoundedCornerShape(20.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocumentColorMode.entries.forEach { mode ->
                val isSelected = mode == currentColorMode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color(0xFF1976D2) else Color.Transparent)
                        .clickable(
                            indication = ripple(bounded = true),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { onSelectColorMode(mode) },
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = mode.label,
                        style = OptiLensCameraTypography.readout.copy(
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xCCFFFFFF),
                        ),
                    )
                }
            }
        }
    }
}
