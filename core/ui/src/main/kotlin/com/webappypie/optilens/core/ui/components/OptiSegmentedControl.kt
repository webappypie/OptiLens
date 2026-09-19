package com.webappypie.optilens.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Accessible, tactile segmented control with animated pill selection.
 *
 * @param items List of options to display.
 * @param selectedIndex The currently selected index.
 * @param onItemSelected Callback when an item is selected.
 * @param isOverlay Whether this control is displayed on the camera viewfinder overlay.
 */
@Composable
fun <T> OptiSegmentedControl(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    isOverlay: Boolean = false,
    itemLabel: (T) -> String = { it.toString() },
) {
    val overlayColors = OptiLensTheme.overlayColors

    val containerBackground = if (isOverlay) {
        overlayColors.controlSurface
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val containerBorder = if (isOverlay) {
        overlayColors.controlBorder
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(containerBackground)
            .border(1.dp, containerBorder, CircleShape)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex

                val segmentBg by animateColorAsState(
                    targetValue = when {
                        isSelected && isOverlay -> overlayColors.controlSurfaceActive
                        isSelected              -> MaterialTheme.colorScheme.surface
                        else                    -> Color.Transparent
                    },
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "segmentBg",
                )

                val contentColor by animateColorAsState(
                    targetValue = when {
                        isSelected && isOverlay -> overlayColors.controlOnSurfaceActive
                        isSelected              -> MaterialTheme.colorScheme.primary
                        isOverlay               -> overlayColors.controlOnSurfaceMuted
                        else                    -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "segmentText",
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(segmentBg)
                        .semantics {
                            this.role = Role.Tab
                            this.selected = isSelected
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { onItemSelected(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = itemLabel(item),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        ),
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
