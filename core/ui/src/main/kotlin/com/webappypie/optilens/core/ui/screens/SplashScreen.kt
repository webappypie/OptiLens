package com.webappypie.optilens.core.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Premium branded splash screen with animated logo, title, and subtitle.
 *
 * Animation sequence:
 *  - 0ms:   Logo scales from 0.7f to 1.0f and fades in (300ms)
 *  - 200ms: "OptiLens" title fades in with letter spacing (300ms)
 *  - 450ms: "Enhanced by AI" subtitle slides up and fades in (300ms)
 *  - 1300ms: [onSplashComplete] is invoked to transition to Camera
 */
@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val logoScale = remember { Animatable(0.7f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val subtitleAlpha = remember { Animatable(0f) }
    val subtitleOffset = remember { Animatable(18f) }

    LaunchedEffect(Unit) {
        // Logo entrance
        launch {
            logoScale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        }
        launch {
            logoAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 320),
            )
        }

        // Title after 200ms
        delay(200)
        launch {
            titleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300),
            )
        }

        // Subtitle after 450ms
        delay(250)
        launch {
            subtitleAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300),
            )
        }
        launch {
            subtitleOffset.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            )
        }

        // Hold then navigate
        delay(850)
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
            // App logo — uses the launcher icon as a simple branded circle
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "\uD83D\uDCF7",
                    fontSize = 64.sp,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App name
            Text(
                text = "OptiLens",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 2.sp,
                modifier = Modifier.alpha(titleAlpha.value),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = "Enhanced by AI",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                letterSpacing = 3.sp,
                modifier = Modifier
                    .alpha(subtitleAlpha.value),
            )
        }
    }
}
