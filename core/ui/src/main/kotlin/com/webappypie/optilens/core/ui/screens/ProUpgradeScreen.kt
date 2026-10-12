package com.webappypie.optilens.core.ui.screens

import androidx.compose.ui.res.stringResource
import com.webappypie.optilens.core.ui.R
import android.app.Activity
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webappypie.optilens.core.ui.components.OptiButton
import com.webappypie.optilens.core.ui.components.OptiTopBar
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

// Only benefits the Pro entitlement actually unlocks today.
private val ProFeatures = listOf(
    R.string.pro_feature_tools,
    R.string.pro_feature_no_ads,
    R.string.pro_feature_on_device,
)

/**
 * Premium / Pro Upgrade screen for OptiLens.
 *
 * Fully integrated with Google Play Billing 7.x, central EntitlementRepository,
 * dynamic pricing, and Remote Config promotional copy.
 */
@Composable
fun ProUpgradeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProUpgradeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage, uiState.successMessage) {
        uiState.errorMessage?.let { error ->
            snackbarHostState.showSnackbar(error.resolve(context))
            viewModel.clearMessages()
        }
        uiState.successMessage?.let { success ->
            snackbarHostState.showSnackbar(success.resolve(context))
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            OptiTopBar(
                title = stringResource(R.string.pro_title),
                onBackClick = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        imageVector = if (uiState.isPro) Icons.Outlined.CheckCircle else Icons.Outlined.Diamond,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OptiLensTheme.iconSizes.large),
                    )
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))

            Text(
                text = stringResource(if (uiState.isPro) R.string.pro_you_are else R.string.pro_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (uiState.isLifetime) {
                    stringResource(R.string.pro_lifetime_active)
                } else if (uiState.isPro) {
                    stringResource(R.string.pro_sub_active)
                } else {
                    uiState.promotionalCopy ?: stringResource(R.string.pro_default_copy)
                },
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
                            text = stringResource(feature),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxl))

            if (!uiState.isPro && !uiState.hasPurchasableProducts) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.pro_unavailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(OptiLensTheme.spacing.l),
                    )
                }
            } else if (!uiState.isPro) {
                // Plan cards: only plans Google Play actually returned, at Play's prices.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(OptiLensTheme.spacing.m),
                ) {
                    // Annual Plan Card
                    val annual = uiState.annualProduct
                    val isAnnualSelected = uiState.selectedProduct?.id == annual?.id
                    if (annual != null) Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isAnnualSelected) 2.dp else 1.dp,
                                color = if (isAnnualSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { viewModel.selectPlan(0) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAnnualSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(OptiLensTheme.spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.pro_annual),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = annual.formattedPrice,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(if (annual.hasFreeTrial) R.string.pro_trial else R.string.pro_billed_yearly),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    // Lifetime Card
                    val lifetime = uiState.lifetimeProduct
                    val isLifetimeSelected = uiState.selectedProduct?.id == lifetime?.id
                    if (lifetime != null) Card(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                width = if (isLifetimeSelected) 2.dp else 1.dp,
                                color = if (isLifetimeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { viewModel.selectPlan(1) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLifetimeSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.padding(OptiLensTheme.spacing.l),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(R.string.pro_lifetime),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            )
                            Text(
                                text = lifetime.formattedPrice,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = stringResource(R.string.pro_pay_once),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xxl))

                // Primary CTA
                if (uiState.isPurchaseInProgress) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    val selected = uiState.selectedProduct
                    OptiButton(
                        text = stringResource(
                            when {
                                selected == null -> R.string.pro_cta_unavailable
                                selected.id == uiState.lifetimeProduct?.id -> R.string.pro_cta_lifetime
                                selected.hasFreeTrial -> R.string.pro_cta_trial
                                else -> R.string.pro_cta_subscribe
                            },
                        ),
                        enabled = selected != null,
                        onClick = {
                            if (activity != null) {
                                viewModel.startPurchase(activity)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(OptiLensTheme.spacing.l),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.pro_status_active),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(R.string.pro_status_text),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.m))

            // Secondary links
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (uiState.isRestoring) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.pro_restoring),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(onClick = { viewModel.restorePurchases() }) {
                        Text(
                            text = stringResource(R.string.pro_restore),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // "Privacy & Terms" removed: there is no published policy URL yet (owner input).
            }

            Spacer(modifier = Modifier.height(OptiLensTheme.spacing.xl))
        }
    }
}
