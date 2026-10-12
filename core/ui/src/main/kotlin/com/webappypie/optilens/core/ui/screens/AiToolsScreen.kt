package com.webappypie.optilens.core.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.webappypie.optilens.core.ui.aitools.AiToolsScreen as AiToolsScreenImpl

/**
 * Entry-point wrapper for the AI Tools screen. Delegates to the real [AiToolsScreenImpl]
 * composable which hosts the ViewModel, image loading, and all interactive AI controls.
 *
 * @param photoUri Optional content URI string of a photo to load. Empty if opened standalone.
 * @param onNavigateBack Callback to pop the back stack.
 * @param onNavigateToUpgrade Callback to navigate to the Pro upgrade screen.
 */
@Composable
fun AiToolsScreen(
    photoUri: String = "",
    onNavigateBack: () -> Unit,
    onNavigateToUpgrade: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    AiToolsScreenImpl(
        photoUri = photoUri,
        onNavigateBack = onNavigateBack,
        onNavigateToUpgrade = onNavigateToUpgrade,
        modifier = modifier,
    )
}
