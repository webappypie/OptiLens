package com.webappypie.optilens.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Clean permission prompt screen shown when camera or media permissions
 * are required to proceed.
 *
 * @param title Prominent heading explaining the required access.
 * @param description User-friendly explanation of why the permission is needed.
 * @param onRequestPermission Callback when the primary "Allow Access" CTA is clicked.
 * @param onOpenSettings Optional callback for "Open Settings" when permission is permanently denied.
 */
@Composable
fun OptiPermissionState(
    title: String,
    description: String,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.PhotoCamera,
    onOpenSettings: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(OptiLensTheme.spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(OptiLensTheme.iconSizes.extraLarge),
            )
        }

        Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxl))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))

        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = OptiLensTheme.spacing.m),
        )

        Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxxl))

        OptiButton(
            text = "Grant Permission",
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth(),
        )

        if (onOpenSettings != null) {
            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))
            OptiButton(
                text = "Open App Settings",
                onClick = onOpenSettings,
                variant = OptiButtonVariant.OUTLINED,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
