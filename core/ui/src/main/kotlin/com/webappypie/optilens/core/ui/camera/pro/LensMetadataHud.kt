package com.webappypie.optilens.core.ui.camera.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.model.LensMetadata
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Minimalist optical and exposure readout strip for the Pro photography viewfinder.
 */
@Composable
fun LensMetadataHud(
    metadata: LensMetadata,
    modifier: Modifier = Modifier,
    isRawActive: Boolean = false,
) {
    val summary = metadata.readoutSummary
    if (summary.isEmpty()) return

    val overlayColors = OptiLensTheme.overlayColors
    val shape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(overlayColors.scrimBackground)
            .border(0.5.dp, overlayColors.controlBorder.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val fullText = if (isRawActive) "$summary · RAW" else summary
            Text(
                text = fullText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = overlayColors.controlOnSurface,
                letterSpacing = 0.5.sp,
            )
        }
    }
}
