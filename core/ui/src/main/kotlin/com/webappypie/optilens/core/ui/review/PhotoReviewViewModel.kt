package com.webappypie.optilens.core.ui.review

import android.app.PendingIntent
import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.camera.storage.GalleryRepository
import com.webappypie.optilens.core.common.result.OptiResult
import com.webappypie.optilens.core.imaging.enhance.EnhancementConfig
import com.webappypie.optilens.core.imaging.enhance.EnhancementReport
import com.webappypie.optilens.core.imaging.enhance.OnDeviceEnhancementEngine
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.settings.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** Which image the review canvas shows. */
enum class ReviewViewMode { ORIGINAL, ENHANCED, COMPARE }

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
    val viewMode: ReviewViewMode = ReviewViewMode.ORIGINAL,
    /** Short, factual notes about what the enhancement measured and changed. */
    val enhancementNotes: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val savedUri: String? = null,
    val keepOriginal: Boolean = true,
    val splitFraction: Float = 0.50f,
    val isHoldingToCompare: Boolean = false,
    val errorMessage: String? = null,
    /** One-shot confirmation message (e.g. after saving). */
    val message: String? = null,
    val isDiscarding: Boolean = false,
    /** System consent needed to delete the photo (Android 11+ for items the app does not own). */
    val pendingDeleteIntent: PendingIntent? = null,
    /** Set once the captured photo has been deleted; the screen should close. */
    val isDiscarded: Boolean = false,
)

/**
 * Review of a captured or picked photo with one-tap on-device enhancement
 * ([OnDeviceEnhancementEngine]: tone, colour, noise and detail; no network, no cloud).
 * The original file is never modified; the enhanced result is saved as a new photo.
 */
@HiltViewModel
class PhotoReviewViewModel @Inject constructor(
    private val photoBitmapLoader: PhotoBitmapLoader,
    private val appSettings: AppSettings,
    private val logger: AppLogger,
    private val enhancementEngine: OnDeviceEnhancementEngine,
    private val galleryRepository: GalleryRepository?,
) : androidx.lifecycle.ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Secondary constructor for unit testing with custom test dispatchers
    internal constructor(
        photoBitmapLoader: PhotoBitmapLoader,
        appSettings: AppSettings,
        logger: AppLogger,
        enhancementEngine: OnDeviceEnhancementEngine,
        ioDispatcher: CoroutineDispatcher,
        galleryRepository: GalleryRepository? = null,
    ) : this(photoBitmapLoader, appSettings, logger, enhancementEngine, galleryRepository) {
        this.ioDispatcher = ioDispatcher
    }

    private val _uiState = MutableStateFlow(PhotoReviewUiState())
    val uiState: StateFlow<PhotoReviewUiState> = _uiState.asStateFlow()

    private var enhanceJob: Job? = null

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
        val state = _uiState.value
        val original = state.originalBitmap ?: return
        if (state.isEnhancing || state.isEnhanced) return

        _uiState.update { it.copy(isEnhancing = true, enhanceProgress = 0f, errorMessage = null) }

        enhanceJob = viewModelScope.launch {
            try {
                val result = enhancementEngine.enhanceWithReport(
                    bitmap = original,
                    config = EnhancementConfig.BALANCED,
                    onProgress = { p ->
                        // Progress arrives from worker threads; only ever move forward.
                        _uiState.update { s ->
                            if (s.isEnhancing && p > s.enhanceProgress) s.copy(enhanceProgress = p.coerceIn(0f, 1f)) else s
                        }
                    },
                )
                _uiState.update {
                    it.copy(
                        enhancedBitmap = result.bitmap,
                        isEnhanced = true,
                        isEnhancing = false,
                        enhanceProgress = 1f,
                        viewMode = ReviewViewMode.COMPARE,
                        enhancementNotes = describe(result.report),
                        isSaved = false,
                        savedUri = null,
                        splitFraction = 0.50f,
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isEnhancing = false, enhanceProgress = 0f) }
                throw e
            } catch (e: OutOfMemoryError) {
                logger.e("PhotoReviewViewModel", "Out of memory during enhance", e)
                _uiState.update { it.copy(isEnhancing = false, errorMessage = "Not enough memory to enhance this photo.") }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error during enhance", e)
                _uiState.update { it.copy(isEnhancing = false, errorMessage = "Enhancement failed. Please try again.") }
            } finally {
                enhanceJob = null
            }
        }
    }

    fun cancelEnhance() {
        enhanceJob?.cancel()
        _uiState.update { it.copy(isEnhancing = false, enhanceProgress = 0f) }
    }

    fun setViewMode(mode: ReviewViewMode) {
        if (mode != ReviewViewMode.ORIGINAL && !_uiState.value.isEnhanced) return
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun revert() {
        if (!_uiState.value.isEnhanced) return

        _uiState.update {
            it.copy(
                enhancedBitmap = null,
                isEnhanced = false,
                viewMode = ReviewViewMode.ORIGINAL,
                enhancementNotes = emptyList(),
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
        val state = _uiState.value
        val enhanced = state.enhancedBitmap ?: return
        if (state.isSaved || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }

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
                        it.copy(isSaving = false, isSaved = true, savedUri = savedUri, message = "Enhanced copy saved to Gallery")
                    }
                } else {
                    _uiState.update { it.copy(isSaving = false, errorMessage = "The enhanced copy couldn't be saved.") }
                }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error saving enhanced photo", e)
                _uiState.update { it.copy(isSaving = false, errorMessage = "The enhanced copy couldn't be saved.") }
            }
        }
    }

    /** Deletes the just-captured original (Retake). Never called for photos opened from the gallery. */
    fun discardCapturedPhoto() {
        val state = _uiState.value
        val repository = galleryRepository ?: return
        if (state.isDiscarding || state.photoUri.isBlank()) return
        cancelEnhance()
        _uiState.update { it.copy(isDiscarding = true, errorMessage = null) }

        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.deleteMedia(state.photoUri)) {
                is OptiResult.Success -> {
                    val consent = result.data
                    _uiState.update {
                        if (consent != null) it.copy(isDiscarding = false, pendingDeleteIntent = consent)
                        else it.copy(isDiscarding = false, isDiscarded = true)
                    }
                }
                is OptiResult.Error -> _uiState.update {
                    it.copy(isDiscarding = false, errorMessage = "This photo couldn't be deleted.")
                }
                else -> _uiState.update { it.copy(isDiscarding = false) }
            }
        }
    }

    /** Result of the system delete-consent dialog. */
    fun onDeleteConsentResult(granted: Boolean) {
        _uiState.update { it.copy(pendingDeleteIntent = null, isDiscarded = granted) }
    }

    fun setSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.02f, 0.98f)) }
    }

    fun setHoldingToCompare(isHolding: Boolean) {
        _uiState.update { it.copy(isHoldingToCompare = isHolding) }
    }

    override fun onCleared() {
        enhanceJob?.cancel()
        super.onCleared()
    }

    internal fun setEnhancedStateForTesting(enhanced: Bitmap?) {
        _uiState.update {
            it.copy(
                isEnhanced = true,
                enhancedBitmap = enhanced,
            )
        }
    }

    internal companion object {
        /** Factual description of what the pipeline measured and did. */
        fun describe(report: EnhancementReport): List<String> = buildList {
            when {
                report.exposureStops >= 0.15f -> add("Brightened by %.1f EV".format(report.exposureStops))
                report.exposureStops <= -0.15f -> add("Darkened by %.1f EV".format(-report.exposureStops))
            }
            if (report.shadowCompression < 0.97f) add("Shadows lifted")
            val wbShift = maxOf(abs(report.whiteBalanceGainR - 1f), abs(report.whiteBalanceGainB - 1f))
            if (wbShift >= 0.02f) add("Colour cast reduced")
            if (report.lumaDenoiseRadius > 0) add("Noise reduced")
            if (report.clippedHighlightFraction >= 0.02f) {
                add(
                    "%d%% of the photo was already pure white; that detail can't be recovered"
                        .format((report.clippedHighlightFraction * 100).toInt()),
                )
            }
        }
    }
}
