package com.webappypie.optilens.core.ui.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webappypie.optilens.core.camera.bestshot.BestShotCandidate
import com.webappypie.optilens.core.camera.bestshot.BestShotResult
import com.webappypie.optilens.core.ui.theme.OptiLensCameraTypography
import com.webappypie.optilens.core.ui.theme.OptiLensTheme

/**
 * Interactive inspection and manual override sheet for Best Shot multi-frame bursts.
 *
 * Presents:
 * 1. The top recommended Best Shot candidate with score metrics.
 * 2. Horizontal carousel of all alternate candidates with their respective scores.
 * 3. Manual override capability allowing the user to select any candidate as the keeper.
 * 4. Strictly authentic physical frame review without expression synthesis.
 */
@Composable
fun BestShotAlternatesSheet(
    result: BestShotResult,
    onSelectCandidate: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlayColors = OptiLensTheme.overlayColors
    val selectedCandidate = result.selectedCandidate ?: return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF0121212),
        ),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header Row: Title, Best Badge, and Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Best Shot Selection",
                        style = OptiLensCameraTypography.readout.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        ),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(32.dp)
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
                        contentDescription = "Close Best Shot review",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected Shot Breakdown Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E1E1E))
                    .border(1.dp, if (selectedCandidate.isBest) Color(0x66FFD54F) else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Frame #${selectedCandidate.index + 1}",
                                style = OptiLensCameraTypography.readout.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                ),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (selectedCandidate.isBest) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x33FFD54F))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "AI RECOMMENDED",
                                        style = OptiLensCameraTypography.readout.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD54F),
                                        ),
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x3364B5F6))
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "MANUAL OVERRIDE",
                                        style = OptiLensCameraTypography.readout.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF64B5F6),
                                        ),
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Score: ${selectedCandidate.compositeScore.toInt()}%",
                            style = OptiLensCameraTypography.readout.copy(
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF81C784),
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Metrics Row: Sharpness | Motion | Exposure | Eye Open
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        MetricPill(label = "Sharpness", value = "${selectedCandidate.sharpnessScore.toInt()}%")
                        MetricPill(label = "Stability", value = "${selectedCandidate.motionScore.toInt()}%")
                        MetricPill(label = "Exposure", value = "${selectedCandidate.exposureScore.toInt()}%")
                        selectedCandidate.eyeOpenScore?.let { eye ->
                            MetricPill(label = "Eye Open", value = "${(eye * 100).toInt()}%")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Alternates (${result.candidates.size} burst frames):",
                style = OptiLensCameraTypography.readout.copy(fontSize = 12.sp, color = Color(0xFFAAAAAA)),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Carousel of All Candidates
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(result.candidates) { candidate ->
                    CandidateThumbnailItem(
                        candidate = candidate,
                        isSelected = candidate.index == result.selectedIndex,
                        onClick = { onSelectCandidate(candidate.index) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Done / Keep Selection Button
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32),
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Keep Frame #${selectedCandidate.index + 1}",
                    style = OptiLensCameraTypography.readout.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }
}

@Composable
private fun CandidateThumbnailItem(
    candidate: BestShotCandidate,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFFFFD54F)
            candidate.isBest -> Color(0x66FFD54F)
            else -> Color(0x33FFFFFF)
        },
        label = "border",
    )

    Box(
        modifier = Modifier
            .size(width = 68.dp, height = 80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF262626))
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(
                indication = ripple(bounded = true),
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            )
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "#${candidate.index + 1}",
                style = OptiLensCameraTypography.readout.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                ),
            )

            if (candidate.isBest) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color(0xFFFFD54F),
                    modifier = Modifier.size(14.dp),
                )
            }

            Text(
                text = "${candidate.compositeScore.toInt()}%",
                style = OptiLensCameraTypography.readout.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (candidate.isBest) Color(0xFFFFD54F) else Color(0xFF81C784),
                ),
            )
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = OptiLensCameraTypography.readout.copy(fontSize = 10.sp, color = Color(0xFF888888)),
        )
        Text(
            text = value,
            style = OptiLensCameraTypography.readout.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            ),
        )
    }
}
