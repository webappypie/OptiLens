package com.webappypie.optilens.core.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Total time the branded splash is shown before navigating to the camera. */
internal const val SPLASH_DURATION_MS = 700L

/**
 * Brief branded splash: an aperture mark closes down while the wordmark and tagline fade in,
 * then the app navigates straight to the camera. No progress indicator is shown because no
 * startup work is being tracked here.
 *
 * Timeline: 0 ms aperture + title start, 150 ms tagline, [SPLASH_DURATION_MS] navigate.
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aperture = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        launch { aperture.animateTo(1f, tween(durationMillis = 450, easing = FastOutSlowInEasing)) }
        launch { titleAlpha.animateTo(1f, tween(durationMillis = 300)) }
        delay(150)
        launch { subtitleAlpha.animateTo(1f, tween(durationMillis = 300)) }
        delay(SPLASH_DURATION_MS - 150)
        onSplashComplete()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ApertureMark(
                progress = aperture.value,
                color = accent,
                modifier = Modifier
                    .size(88.dp)
                    .semantics { contentDescription = "OptiLens" },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "OptiLens",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(titleAlpha.value),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Enhanced by AI",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = accent.copy(alpha = 0.85f),
                letterSpacing = 3.sp,
                modifier = Modifier.alpha(subtitleAlpha.value),
            )
        }
    }
}

/**
 * Six-blade aperture outline. [progress] 0→1 rotates the blades in and narrows the iris,
 * like a lens stopping down.
 */
@Composable
private fun ApertureMark(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.alpha(progress.coerceIn(0f, 1f))) {
        val radius = size.minDimension / 2f
        val stroke = radius * 0.08f
        drawCircle(color = color, radius = radius - stroke / 2f, style = Stroke(width = stroke))

        val blades = 6
        val irisRadius = radius * (0.62f - 0.22f * progress)
        rotate(degrees = 30f * (1f - progress)) {
            for (i in 0 until blades) {
                val a = Math.toRadians((i * 360.0 / blades))
                val start = Offset(
                    x = center.x + (irisRadius * cos(a)).toFloat(),
                    y = center.y + (irisRadius * sin(a)).toFloat(),
                )
                // Each blade edge runs tangentially from the iris and ends exactly on the ring.
                val tangent = a + Math.PI / 2.0
                val ring = radius - stroke
                val length = kotlin.math.sqrt((ring * ring - irisRadius * irisRadius).coerceAtLeast(0f))
                val end = Offset(
                    x = start.x + (length * cos(tangent)).toFloat(),
                    y = start.y + (length * sin(tangent)).toFloat(),
                )
                drawLine(color = color, start = start, end = end, strokeWidth = stroke)
            }
        }
    }
}
