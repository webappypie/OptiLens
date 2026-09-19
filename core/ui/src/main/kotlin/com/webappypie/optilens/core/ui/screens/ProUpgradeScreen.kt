package com.webappypie.optilens.core.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.webappypie.optilens.core.ui.components.OptiButton
import com.webappypie.optilens.core.ui.components.OptiTopBar
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

private val ProFeatures = listOf(
    "16-bit RAW DNG capture with embedded color matrices",
    "On-device AI Super-Resolution Zoom up to 30x",
    "Studio Portrait Relighting and adjustable optical bokeh",
    "Full Manual Controls: Shutter Speed, ISO, Manual Focus & WB",
    "Handheld Astro & Night Vision burst synthesis",
    "100% on-device private processing — zero cloud uploads",
)

/**
 * Premium / Pro Upgrade screen for OptiLens.
 */
@Composable
fun ProUpgradeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSubscribeClick: (planIndex: Int) -> Unit = {},
) {
    var selectedPlanIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            OptiTopBar(
                title = "OptiLens Pro",
                onBackClick = onNavigateBack,
            )
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OptiLensTheme.spacing.l),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.l))

            // Diamond Badge
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Diamond,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OptiLensTheme.iconSizes.large),
                    )
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))

            Text(
                text = "Unleash Full Sensor Power",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = "Pro manual shooting, 16-bit RAW, and real-time AI tools.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = OptiLensTheme.spacing.xs),
            )

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xl))

            // Features Checklist
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.m),
            ) {
                ProFeatures.forEach { feature ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(22.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(OptiLensTheme.spacing.m))

                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxl))

            // Subscription Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.m),
            ) {
                // Annual Plan Card
                val isAnnualSelected = selectedPlanIndex == 0
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isAnnualSelected) 2.dp else 1.dp,
                            color = if (isAnnualSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { selectedPlanIndex = 0 },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isAnnualSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(OptiLensTheme.spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                text = "BEST VALUE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }

                        Spacer(modifier = Modifier.height(OptiLensTheme.spacing.s))

                        Text(
                            text = "Annual",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "$29.99 / yr",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "7-day free trial",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Lifetime Card
                val isLifetimeSelected = selectedPlanIndex == 1
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .border(
                            width = if (isLifetimeSelected) 2.dp else 1.dp,
                            color = if (isLifetimeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { selectedPlanIndex = 1 },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isLifetimeSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(OptiLensTheme.spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "Lifetime",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "$69.99",
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Pay once, yours forever",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxl))

            // Primary CTA
            OptiButton(
                text = if (selectedPlanIndex == 0) "Start 7-Day Free Trial" else "Purchase Lifetime Access",
                onClick = { onSubscribeClick(selectedPlanIndex) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))

            // Secondary links
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { /* Phase 17 In-App Billing */ }) {
                    Text(
                        text = "Restore Purchases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Text(
                    text = "•",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = OptiLensTheme.spacing.xs),
                )

                TextButton(onClick = { /* Terms */ }) {
                    Text(
                        text = "Privacy & Terms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xl))
        }
    }
}
