package com.webappypie.optilens.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.theme.OptiLensShapes
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

enum class OptiButtonVariant {
    PRIMARY,
    SECONDARY,
    OUTLINED,
}

/**
 * Primary call-to-action button for OptiLens.
 *
 * Enforces:
 * - Minimum 48dp height for touch accessibility.
 * - Loading indicator state without shifting layout.
 * - Optional leading icon.
 */
@Composable
fun OptiButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: OptiButtonVariant = OptiButtonVariant.PRIMARY,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    leadingIcon: ImageVector? = null,
) {
    val buttonColors = when (variant) {
        OptiButtonVariant.PRIMARY -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor   = MaterialTheme.colorScheme.onPrimary,
        )
        OptiButtonVariant.SECONDARY -> ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor   = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        OptiButtonVariant.OUTLINED -> ButtonDefaults.outlinedButtonColors(
            contentColor   = MaterialTheme.colorScheme.primary,
        )
    }

    val contentPadding = PaddingValues(
        horizontal = OptiLensTheme.spacing.xxl,
        vertical   = OptiLensTheme.spacing.m,
    )

    val buttonModifier = modifier
        .height(48.dp)
        .semantics { this.role = Role.Button }

    val buttonContent: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(OptiLensTheme.iconSizes.medium),
                        )
                        Spacer(modifier = Modifier.width(OptiLensTheme.spacing.s))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    )
                }
            }
        }
    }

    if (variant == OptiButtonVariant.OUTLINED) {
        OutlinedButton(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !isLoading,
            shape = OptiLensShapes.medium,
            colors = buttonColors,
            contentPadding = contentPadding,
        ) { buttonContent() }
    } else {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            enabled = enabled && !isLoading,
            shape = OptiLensShapes.medium,
            colors = buttonColors,
            contentPadding = contentPadding,
        ) { buttonContent() }
    }
}
