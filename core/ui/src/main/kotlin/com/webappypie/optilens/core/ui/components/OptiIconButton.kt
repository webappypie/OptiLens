package com.webappypie.optilens.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

enum class OptiIconButtonVariant {
    STANDARD,
    OVERLAY,
    OVERLAY_ACTIVE,
    FILLED,
}

/**
 * Accessible, tactile icon button with enforced minimum 48dp touch target.
 *
 * @param icon The vector icon to display.
 * @param contentDescription Required accessibility description for screen readers.
 * @param onClick Callback when tapped.
 * @param variant Visual style (Standard, Camera Overlay, Overlay Active, Filled).
 * @param enabled Whether interaction is enabled.
 * @param size Visual circle size (defaults to 40dp inside a 48dp minimum touch container).
 */
@Composable
fun OptiIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OptiIconButtonVariant = OptiIconButtonVariant.STANDARD,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = OptiLensTheme.iconSizes.standard,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val minTouchTarget = OptiLensTheme.iconSizes.minTouchTarget

    val backgroundColor = when (variant) {
        OptiIconButtonVariant.STANDARD        -> Color.Transparent
        OptiIconButtonVariant.OVERLAY         -> overlayColors.controlSurface
        OptiIconButtonVariant.OVERLAY_ACTIVE  -> overlayColors.controlSurfaceActive
        OptiIconButtonVariant.FILLED          -> MaterialTheme.colorScheme.surfaceContainer
    }

    val contentColor = when (variant) {
        OptiIconButtonVariant.STANDARD        -> MaterialTheme.colorScheme.onSurface
        OptiIconButtonVariant.OVERLAY         -> overlayColors.controlOnSurface
        OptiIconButtonVariant.OVERLAY_ACTIVE  -> overlayColors.controlOnSurfaceActive
        OptiIconButtonVariant.FILLED          -> MaterialTheme.colorScheme.onSurface
    }

    val borderColor = when (variant) {
        OptiIconButtonVariant.OVERLAY        -> overlayColors.controlBorder
        OptiIconButtonVariant.OVERLAY_ACTIVE -> overlayColors.controlSurfaceActive
        else                                 -> Color.Transparent
    }

    val alpha = if (enabled) 1.0f else 0.38f

    // Enforce 48dp+ touch target via outer container
    Box(
        modifier = modifier
            .size(minTouchTarget.coerceAtLeast(size))
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                this.role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(backgroundColor.copy(alpha = backgroundColor.alpha * alpha))
                .border(
                    width = if (borderColor != Color.Transparent) 1.dp else 0.dp,
                    color = borderColor.copy(alpha = borderColor.alpha * alpha),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = size / 2),
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // Handled by outer semantics
                tint = contentColor.copy(alpha = contentColor.alpha * alpha),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
