package com.webappypie.optilens.core.ui.review

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.imaging.enhance.EnhancementConfig
import com.webappypie.optilens.core.imaging.enhance.OnDeviceEnhancementEngine
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI State for [PhotoReviewScreen].
 */
data class PhotoReviewUiState(
    val photoUri: String = "",
    val originalBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val isEnhancing: Boolean = false,
    /** Real progress fraction reported by the enhancement engine (0..1). */
    val enhanceProgress: Float = 0f,
    val isEnhanced: Boolean = false,
    val isSaved: Boolean = false,
    val savedUri: String? = null,
    val keepOriginal: Boolean = true,
    val splitFraction: Float = 0.50f,
    val isHoldingToCompare: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Review of a captured or picked photo with one-tap on-device enhancement
 * ([OnDeviceEnhancementEngine]: tone, colour and detail adjustment; no cloud processing).
 */
@HiltViewModel
class PhotoReviewViewModel @Inject constructor(
    private val photoBitmapLoader: PhotoBitmapLoader,
    private val appSettings: AppSettings,
    private val logger: AppLogger,
    private val enhancementEngine: OnDeviceEnhancementEngine,
) : androidx.lifecycle.ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Secondary constructor for unit testing with custom test dispatchers
    internal constructor(
        photoBitmapLoader: PhotoBitmapLoader,
        appSettings: AppSettings,
        logger: AppLogger,
        enhancementEngine: OnDeviceEnhancementEngine,
        ioDispatcher: CoroutineDispatcher,
    ) : this(photoBitmapLoader, appSettings, logger, enhancementEngine) {
        this.ioDispatcher = ioDispatcher
    }

    private val _uiState = MutableStateFlow(PhotoReviewUiState())
    val uiState: StateFlow<PhotoReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            appSettings.keepOriginalEnabled.collect { keep ->
                _uiState.update { it.copy(keepOriginal = keep) }
            }
        }
    }

    fun loadPhoto(uriString: String) {
        if (uriString.isBlank() || uriString == _uiState.value.photoUri) return
        _uiState.update { it.copy(photoUri = uriString, errorMessage = null) }

        viewModelScope.launch(ioDispatcher) {
            try {
                val bitmap = photoBitmapLoader.loadBitmap(uriString)
                if (bitmap != null) {
                    _uiState.update { it.copy(originalBitmap = bitmap) }
                } else {
                    _uiState.update { it.copy(errorMessage = "This photo couldn't be opened.") }
                }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error loading photo", e)
                _uiState.update { it.copy(errorMessage = "This photo couldn't be opened.") }
            }
        }
    }

    fun enhance() {
        val original = _uiState.value.originalBitmap ?: return
        if (_uiState.value.isEnhancing) return

        _uiState.update { it.copy(isEnhancing = true, enhanceProgress = 0f, errorMessage = null) }

        viewModelScope.launch {
            try {
                val enhanced = enhancementEngine.enhance(
                    bitmap = original,
                    config = EnhancementConfig.BALANCED,
                    onProgress = { p -> _uiState.update { it.copy(enhanceProgress = p.coerceIn(0f, 1f)) } },
                )
                _uiState.update {
                    it.copy(
                        enhancedBitmap = enhanced,
                        isEnhanced = true,
                        isEnhancing = false,
                        enhanceProgress = 1f,
                        isSaved = false,
                        savedUri = null,
                        splitFraction = 0.50f,
                    )
                }
            } catch (e: OutOfMemoryError) {
                logger.e("PhotoReviewViewModel", "Out of memory during enhance", e)
                _uiState.update { it.copy(isEnhancing = false, errorMessage = "Not enough memory to enhance this photo.") }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error during enhance", e)
                _uiState.update { it.copy(isEnhancing = false, errorMessage = "Enhancement failed. Please try again.") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun revert() {
        if (!_uiState.value.isEnhanced) return

        _uiState.update {
            it.copy(
                enhancedBitmap = null,
                isEnhanced = false,
                isSaved = false,
                savedUri = null,
            )
        }

        // Anonymously track that enhancement was reverted
        viewModelScope.launch {
            appSettings.recordAiEnhanceOutcome(kept = false)
        }
    }

    fun saveEnhanced() {
        val enhanced = _uiState.value.enhancedBitmap ?: return
        if (_uiState.value.isSaved) return

        viewModelScope.launch(ioDispatcher) {
            try {
                val keepOriginal = appSettings.keepOriginalEnabled.first()
                val savedUri = photoBitmapLoader.saveEnhancedBitmap(
                    bitmap = enhanced,
                    originalUri = _uiState.value.photoUri,
                    keepOriginal = keepOriginal,
                )

                if (savedUri != null) {
                    // Anonymously track that enhancement was kept
                    appSettings.recordAiEnhanceOutcome(kept = true)

                    _uiState.update {
                        it.copy(isSaved = true, savedUri = savedUri)
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "The enhanced copy couldn't be saved.") }
                }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error saving enhanced photo", e)
                _uiState.update { it.copy(errorMessage = "The enhanced copy couldn't be saved.") }
            }
        }
    }

    fun setSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.02f, 0.98f)) }
    }

    fun setHoldingToCompare(isHolding: Boolean) {
        _uiState.update { it.copy(isHoldingToCompare = isHolding) }
    }

    internal fun setEnhancedStateForTesting(enhanced: Bitmap?) {
        _uiState.update {
            it.copy(
                isEnhanced = true,
                enhancedBitmap = enhanced,
            )
        }
    }
}
