package com.webappypie.optilens.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Standardized Top Bar for OptiLens.
 *
 * Supports:
 * - Solid surface styling (Settings, Gallery, AI Tools).
 * - Semi-transparent scrim overlay styling (Camera viewfinder).
 * - Safe status bar insets.
 * - Back navigation icon.
 * - Flexible trailing action buttons.
 */
@Composable
fun OptiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    isOverlay: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val overlayColors = OptiLensTheme.overlayColors

    val backgroundColor = if (isOverlay) {
        overlayColors.scrimBackground
    } else {
        MaterialTheme.colorScheme.surface
    }

    val contentColor = if (isOverlay) {
        overlayColors.controlOnSurface
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(top = topInset),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = OptiLensTheme.spacing.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                if (onBackClick != null) {
                    OptiIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBackClick,
                        variant = if (isOverlay) OptiIconButtonVariant.OVERLAY else OptiIconButtonVariant.STANDARD,
                    )
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = contentColor,
                    maxLines = 1,
                    modifier = Modifier.padding(
                        start = if (onBackClick != null) OptiLensTheme.spacing.xs else OptiLensTheme.spacing.m,
                        end = OptiLensTheme.spacing.s,
                    ),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                content = actions,
            )
        }
    }
}
