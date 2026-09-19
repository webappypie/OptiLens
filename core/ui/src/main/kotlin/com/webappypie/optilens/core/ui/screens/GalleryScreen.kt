package com.webappypie.optilens.core.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.webappypie.optilens.core.ui.components.OptiEmptyState
import com.webappypie.optilens.core.ui.components.OptiTopBar

/**
 * Gallery / Media Browser screen for OptiLens.
 *
 * Implements:
 * - Top bar navigation.
 * - Standardized empty state when no photos have been captured yet.
 * - Architecture hook for media store grid loading in Phase 15.
 */
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            OptiTopBar(
                title = "Gallery",
                onBackClick = onNavigateBack,
            )
        },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OptiEmptyState(
                title = "No Photos Yet",
                description = "Photos and portraits captured with OptiLens will be stored and organized here.",
                icon = Icons.Outlined.PhotoLibrary,
                actionLabel = "Open Camera",
                onActionClick = onNavigateToCamera,
            )
        }
    }
}
