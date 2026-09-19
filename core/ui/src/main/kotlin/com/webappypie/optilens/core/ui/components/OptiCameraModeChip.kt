package com.webappypie.optilens.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Camera shooting mode chip for the viewfinder bottom mode selector carousel.
 *
 * Enforces 48dp touch target height and bold active contrast.
 */
@Composable
fun OptiCameraModeChip(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors

    val textColor by animateColorAsState(
        targetValue = if (isSelected) overlayColors.activeAccent else overlayColors.controlOnSurfaceMuted,
        animationSpec = tween(durationMillis = 180),
        label = "modeTextColor",
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .widthIn(min = 56.dp)
            .padding(horizontal = OptiLensTheme.spacing.m)
            .semantics {
                this.role = Role.Tab
                this.selected = isSelected
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
