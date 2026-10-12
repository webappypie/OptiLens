package com.webappypie.optilens.core.ui.screens

import androidx.compose.ui.res.stringResource
import com.webappypie.optilens.core.ui.R
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webappypie.optilens.core.camera.storage.ThumbnailLoader
import com.webappypie.optilens.core.ui.components.OptiEmptyState
import com.webappypie.optilens.core.ui.components.OptiTopBar
import com.webappypie.optilens.core.ui.gallery.DateGroupHeader
import com.webappypie.optilens.core.ui.gallery.GalleryFilter
import com.webappypie.optilens.core.ui.gallery.GalleryFilterChips
import com.webappypie.optilens.core.ui.gallery.GalleryViewModel
import com.webappypie.optilens.core.ui.gallery.MediaThumbnailCard
import com.webappypie.optilens.core.ui.gallery.StorageWarningBanner

/**
 * Production Gallery & Media Browser screen for OptiLens.
 *
 * Implements:
 * - Privacy-minimal MediaStore capture grid (scoped strictly to DCIM/OptiLens).
 * - System Android Photo Picker integration (zero broad media permissions required).
 * - Categorized date grouping (Today, Yesterday, This Week, Month, Older).
 * - Multi-version original/enhanced indicators and RAW badges.
 * - Low-storage warning banner with cache purging.
 * - Multi-selection and safe deletion workflows.
 */
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToPhotoDetail: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: GalleryViewModel = hiltViewModel(),
    helper: GalleryScreenViewModelHelper = hiltViewModel(),
    thumbnailLoader: ThumbnailLoader = helper.thumbnailLoader,
    entitlementRepository: com.webappypie.optilens.core.common.monetization.EntitlementRepository = helper.entitlementRepository,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Android Photo Picker for picking ANY existing photo with zero runtime permissions
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onNavigateToPhotoDetail(uri.toString())
        }
    }

    // Android 11+ system delete confirmation launcher
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onPendingDeleteIntentConsumed()
        }
    }

    LaunchedEffect(uiState.pendingDeleteIntent) {
        uiState.pendingDeleteIntent?.let { pendingIntent ->
            deleteConsentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
        }
    }

    LaunchedEffect(uiState.freedCacheMessage) {
        uiState.freedCacheMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg.resolve(context))
            viewModel.dismissFreedCacheMessage()
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err.resolve(context))
            viewModel.dismissErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            if (uiState.isSelectionMode) {
                OptiTopBar(
                    title = stringResource(R.string.gallery_selected, uiState.selectedItemUris.size),
                    onBackClick = { viewModel.clearSelection() },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = stringResource(R.string.gallery_select_all),
                            )
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                )
            } else {
                OptiTopBar(
                    title = stringResource(R.string.gallery_title),
                    onBackClick = onNavigateBack,
                    actions = {
                        IconButton(onClick = { viewModel.clearCache() }) {
                            Icon(
                                imageVector = Icons.Outlined.CleaningServices,
                                contentDescription = stringResource(R.string.gallery_clear_cache),
                            )
                        }
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.gallery_enhance_existing),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode && uiState.allItems.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                        )
                    },
                    text = { Text(stringResource(R.string.review_enhance_photo), fontSize = 13.sp) },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        bottomBar = {
            com.webappypie.optilens.core.ui.ads.SafeAdBanner(
                placement = com.webappypie.optilens.core.ui.ads.AdPlacement.GALLERY_BOTTOM_BANNER,
                entitlementRepository = entitlementRepository,
                adsRepository = helper.adsRepository,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Low Storage Warning Banner
            StorageWarningBanner(
                status = uiState.storageState.status,
                formattedFree = uiState.storageState.formattedFree,
                onCleanCacheClick = { viewModel.clearCache() },
            )

            // Filter Chips Row
            GalleryFilterChips(
                selectedFilter = uiState.filter,
                onFilterSelected = { viewModel.setFilter(it) },
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredItems.isEmpty()) {
                val isFilterActive = uiState.filter != GalleryFilter.ALL
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    OptiEmptyState(
                        title = if (isFilterActive) {
                            stringResource(R.string.gallery_empty_filtered_title, stringResource(uiState.filter.labelRes))
                        } else {
                            stringResource(R.string.gallery_empty_title)
                        },
                        description = stringResource(
                            if (isFilterActive) R.string.gallery_empty_filtered_message else R.string.gallery_empty_message,
                        ),
                        icon = Icons.Outlined.PhotoLibrary,
                        actionLabel = stringResource(if (isFilterActive) R.string.gallery_show_all else R.string.gallery_open_camera),
                        onActionClick = if (isFilterActive) {
                            { viewModel.setFilter(GalleryFilter.ALL) }
                        } else onNavigateToCamera,
                    )
                }
            } else {
                // Media Grid Grouped by Date
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 105.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp, top = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    uiState.groupedItems.forEach { (bucket, items) ->
                        // Sticky / span full width date header
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            DateGroupHeader(
                                bucket = bucket,
                                count = items.size,
                            )
                        }

                        items(
                            items = items,
                            key = { it.contentUri },
                        ) { item ->
                            MediaThumbnailCard(
                                item = item,
                                thumbnailLoader = thumbnailLoader,
                                isSelected = uiState.selectedItemUris.contains(item.contentUri),
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleItemSelection(item.contentUri)
                                    } else {
                                        onNavigateToPhotoDetail(item.contentUri)
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleItemSelection(item.contentUri)
                                },
                                onToggleFavorite = {
                                    viewModel.toggleFavorite(item)
                                },
                                modifier = Modifier.padding(3.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmDialog) {
        val count = uiState.selectedItemUris.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.gallery_delete_title, count, count)) },
            text = { Text(stringResource(R.string.gallery_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteSelectedItems()
                    }
                ) {
                    Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@dagger.hilt.android.lifecycle.HiltViewModel
class GalleryScreenViewModelHelper @javax.inject.Inject constructor(
    val thumbnailLoader: ThumbnailLoader,
    val entitlementRepository: com.webappypie.optilens.core.common.monetization.EntitlementRepository,
    val adsRepository: com.webappypie.optilens.core.common.wapcentral.WapCentralAdsRepository,
) : androidx.lifecycle.ViewModel()
