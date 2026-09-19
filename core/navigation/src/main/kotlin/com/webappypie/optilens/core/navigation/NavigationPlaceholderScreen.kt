package com.webappypie.optilens.core.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Temporary placeholder shown for any navigation destination
 * that hasn't been implemented yet.
 *
 * Replaced screen-by-screen in Phase 02 onwards.
 */
@Composable
internal fun NavigationPlaceholderScreen(
    destination: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "$destination — coming soon",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
