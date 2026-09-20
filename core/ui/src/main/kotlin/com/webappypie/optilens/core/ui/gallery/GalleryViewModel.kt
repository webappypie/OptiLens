package com.webappypie.optilens.core.ui.gallery

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.camera.storage.DateBucket
import com.webappypie.optilens.core.camera.storage.GalleryRepository
import com.webappypie.optilens.core.camera.storage.MediaItem
import com.webappypie.optilens.core.camera.storage.StorageMonitor
import com.webappypie.optilens.core.camera.storage.StorageState
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.logging.AppLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Filter categories for the media gallery.
 */
enum class GalleryFilter(val label: String) {
    ALL("All Photos"),
    FAVORITES("Favorites"),
    ENHANCED("AI Enhanced"),
    RAW("RAW / DNG");
}

/**
 * State for [GalleryScreen].
 */
data class GalleryUiState(
    val filter: GalleryFilter = GalleryFilter.ALL,
    val allItems: List<MediaItem> = emptyList(),
    val filteredItems: List<MediaItem> = emptyList(),
    val groupedItems: Map<DateBucket, List<MediaItem>> = emptyMap(),
    val selectedItemUris: Set<String> = emptySet(),
    val storageState: StorageState = StorageState(),
    val isLoading: Boolean = true,
    val pendingDeleteIntent: PendingIntent? = null,
    val freedCacheMessage: String? = null,
    val errorMessage: String? = null,
) {
    val isSelectionMode: Boolean get() = selectedItemUris.isNotEmpty()
    val totalCount: Int get() = allItems.size
    val filteredCount: Int get() = filteredItems.size
}

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val galleryRepository: GalleryRepository,
    private val storageMonitor: StorageMonitor,
    private val logger: AppLogger,
) : ViewModel() {

    private val _filter = MutableStateFlow(GalleryFilter.ALL)
    private val _selectedUris = MutableStateFlow<Set<String>>(emptySet())
    private val _pendingDeleteIntent = MutableStateFlow<PendingIntent?>(null)
    private val _freedCacheMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<GalleryUiState> = combine(
        galleryRepository.observeMedia(),
        storageMonitor.storageState,
        _filter,
        _selectedUris,
        combine(_pendingDeleteIntent, _freedCacheMessage, _errorMessage) { pending, freed, error ->
            Triple(pending, freed, error)
        }
    ) { rawItems, storage, filter, selected, (pending, freed, error) ->
        val filtered = when (filter) {
            GalleryFilter.ALL -> rawItems
            GalleryFilter.FAVORITES -> rawItems.filter { it.isFavorite }
            GalleryFilter.ENHANCED -> rawItems.filter { it.isEnhanced }
            GalleryFilter.RAW -> rawItems.filter { it.isRaw }
        }

        // Group by DateBucket in chronological order
        val grouped = filtered.groupBy { it.dateBucket }

        GalleryUiState(
            filter = filter,
            allItems = rawItems,
            filteredItems = filtered,
            groupedItems = grouped,
            selectedItemUris = selected,
            storageState = storage,
            isLoading = false,
            pendingDeleteIntent = pending,
            freedCacheMessage = freed,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GalleryUiState(),
    )

    init {
        viewModelScope.launch {
            storageMonitor.refreshStorageState()
            storageMonitor.cleanExpiredCache()
        }
    }

    fun setFilter(filter: GalleryFilter) {
        _filter.value = filter
    }

    fun toggleItemSelection(uriString: String) {
        _selectedUris.update { current ->
            if (current.contains(uriString)) {
                current - uriString
            } else {
                current + uriString
            }
        }
    }

    fun selectAll() {
        _selectedUris.value = uiState.value.filteredItems.map { it.contentUri }.toSet()
    }

    fun clearSelection() {
        _selectedUris.value = emptySet()
    }

    fun toggleFavorite(mediaItem: MediaItem) {
        viewModelScope.launch {
            val result = galleryRepository.toggleFavorite(mediaItem.contentUri)
            if (result is OptiResult.Error) {
                _errorMessage.value = "Failed to toggle favorite: ${result.error.displayMessage}"
            }
        }
    }

    fun deleteItem(uriString: String) {
        viewModelScope.launch {
            val result = galleryRepository.deleteMedia(uriString)
            when (result) {
                is OptiResult.Success -> {
                    val pendingIntent = result.data
                    if (pendingIntent != null) {
                        _pendingDeleteIntent.value = pendingIntent
                    } else {
                        // Deletion succeeded directly
                        _selectedUris.update { it - uriString }
                    }
                }
                is OptiResult.Error -> {
                    _errorMessage.value = "Failed to delete: ${result.error.displayMessage}"
                }
                is OptiResult.Loading -> Unit
            }
            storageMonitor.refreshStorageState()
        }
    }

    fun deleteSelectedItems() {
        val targets = _selectedUris.value.toList()
        if (targets.isEmpty()) return

        viewModelScope.launch {
            for (uri in targets) {
                val result = galleryRepository.deleteMedia(uri)
                if (result is OptiResult.Success && result.data != null) {
                    _pendingDeleteIntent.value = result.data
                    break
                }
            }
            _selectedUris.value = emptySet()
            storageMonitor.refreshStorageState()
        }
    }

    fun onPendingDeleteIntentConsumed() {
        _pendingDeleteIntent.value = null
        clearSelection()
    }

    fun clearCache() {
        viewModelScope.launch {
            val freedBytes = storageMonitor.clearCache()
            val freedMb = freedBytes / (1024.0 * 1024.0)
            val msg = if (freedMb >= 1.0) {
                String.format(java.util.Locale.US, "Freed %.1f MB of cache", freedMb)
            } else {
                "${freedBytes / 1024} KB freed"
            }
            _freedCacheMessage.value = msg
        }
    }

    fun dismissFreedCacheMessage() {
        _freedCacheMessage.value = null
    }

    fun dismissErrorMessage() {
        _errorMessage.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            storageMonitor.refreshStorageState()
        }
    }
}
