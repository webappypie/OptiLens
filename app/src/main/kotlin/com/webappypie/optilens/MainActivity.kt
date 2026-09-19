package com.webappypie.optilens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.webappypie.optilens.ui.theme.OptiLensTheme

/**
 * Phase 00 — Temporary development entry point.
 *
 * This screen exists only to validate that the project compiles, launches,
 * and renders Compose content. It will be replaced entirely in Phase 02
 * (Design System and Navigation).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OptiLensTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    InitScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun InitScreen(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(text = "OptiLens — Project initialized")
    }
}

@Preview(showBackground = true)
@Composable
fun InitScreenPreview() {
    OptiLensTheme {
        InitScreen()
    }
}
