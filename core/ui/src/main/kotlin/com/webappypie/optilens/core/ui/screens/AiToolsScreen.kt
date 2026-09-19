package com.webappypie.optilens.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.components.OptiTopBar
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

data class AiToolItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val badge: String? = null,
)

private val DefaultAiTools = listOf(
    AiToolItem(
        title = "Magic Eraser",
        description = "Remove photobombers and unwanted background objects seamlessly.",
        icon = Icons.Outlined.AutoAwesome,
        badge = "Popular",
    ),
    AiToolItem(
        title = "AI Super-Resolution",
        description = "Reconstruct high-frequency texture and details on zoomed photos.",
        icon = Icons.Outlined.Search,
    ),
    AiToolItem(
        title = "Studio Portrait Relight",
        description = "Simulate professional studio lighting directions and adjustable bokeh depth.",
        icon = Icons.Outlined.Face,
        badge = "Pro",
    ),
    AiToolItem(
        title = "Night Clarity Fusion",
        description = "De-noise and de-blur handheld low-light shots with zero tripod blur.",
        icon = Icons.Outlined.Nightlight,
    ),
    AiToolItem(
        title = "Document & Whiteboard Scanner",
        description = "Correct perspective keystoning and boost text legibility on printed notes.",
        icon = Icons.Outlined.Tune,
    ),
)

/**
 * AI Tools Screen for OptiLens.
 *
 * Showcases the on-device computational photography tool suite.
 */
@Composable
fun AiToolsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onToolSelected: (AiToolItem) -> Unit = {},
) {
    Scaffold(
        topBar = {
            OptiTopBar(
                title = "AI Tools",
                onBackClick = onNavigateBack,
            )
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(OptiLensTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.m),
        ) {
            items(DefaultAiTools) { tool ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToolSelected(tool) },
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(OptiLensTheme.spacing.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(OptiLensTheme.iconSizes.standard),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(OptiLensTheme.spacing.l))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )

                                if (tool.badge != null) {
                                    Spacer(modifier = Modifier.width(OptiLensTheme.spacing.s))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            text = tool.badge,
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxs))

                            Text(
                                text = tool.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Select ${tool.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
