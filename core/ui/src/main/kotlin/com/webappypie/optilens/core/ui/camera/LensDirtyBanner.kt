package com.webappypie.optilens.core.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.ui.theme.OptiLensCameraTypography
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Non-intrusive dismissible banner warning the user of optical lens contamination or smudges.
 */
@Composable
fun LensDirtyBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        val overlayColors = OptiLensTheme.overlayColors

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xEE1E1C16))
                .border(1.dp, Color(0x66FFB74D), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFB74D),
                modifier = Modifier.size(18.dp),
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Lens appears dirty. Clean camera for clearer shots.",
                style = OptiLensCameraTypography.readout.copy(fontSize = 12.sp, color = Color.White),
                maxLines = 1,
            )

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0x33FFFFFF))
                    .clickable(
                        indication = ripple(bounded = true),
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss warning",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
