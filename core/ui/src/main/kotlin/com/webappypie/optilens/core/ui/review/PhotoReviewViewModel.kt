package com.webappypie.optilens.core.ui.review

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.imaging.enhance.AiEnhanceConfig
import com.webappypie.optilens.core.imaging.enhance.AiEnhanceStage
import com.webappypie.optilens.core.imaging.enhance.NativeAiEnhanceEngine
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
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * UI State for [PhotoReviewScreen].
 */
data class PhotoReviewUiState(
    val photoUri: String = "",
    val originalBitmap: Bitmap? = null,
    val enhancedBitmap: Bitmap? = null,
    val isEnhancing: Boolean = false,
    val currentStage: AiEnhanceStage? = null,
    val isEnhanced: Boolean = false,
    val isSaved: Boolean = false,
    val savedUri: String? = null,
    val keepOriginal: Boolean = true,
    val splitFraction: Float = 0.50f,
    val isHoldingToCompare: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class PhotoReviewViewModel @Inject constructor(
    private val photoBitmapLoader: PhotoBitmapLoader,
    private val appSettings: AppSettings,
    private val logger: AppLogger,
    private val aiEnhanceEngine: NativeAiEnhanceEngine,
) : androidx.lifecycle.ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Secondary constructor for unit testing with custom test dispatchers
    internal constructor(
        photoBitmapLoader: PhotoBitmapLoader,
        appSettings: AppSettings,
        logger: AppLogger,
        aiEnhanceEngine: NativeAiEnhanceEngine,
        ioDispatcher: CoroutineDispatcher,
    ) : this(photoBitmapLoader, appSettings, logger, aiEnhanceEngine) {
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
                    _uiState.update { it.copy(errorMessage = "Failed to load image from URI") }
                }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error loading photo", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to load photo") }
            }
        }
    }

    fun enhance() {
        val original = _uiState.value.originalBitmap ?: return
        if (_uiState.value.isEnhancing) return

        _uiState.update { it.copy(isEnhancing = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val width = original.width
                val height = original.height

                // Extract YUV planar data from original Bitmap
                val pixels = IntArray(width * height)
                original.getPixels(pixels, 0, width, 0, 0, width, height)

                val yPlane = ByteArray(width * height)
                val uvWidth = width / 2
                val uvHeight = height / 2
                val uPlane = ByteArray(uvWidth * uvHeight)
                val vPlane = ByteArray(uvWidth * uvHeight)

                for (y in 0 until height) {
                    val row = y * width
                    val uvRow = (y / 2) * uvWidth
                    for (x in 0 until width) {
                        val color = pixels[row + x]
                        val r = (color shr 16) and 0xFF
                        val g = (color shr 8) and 0xFF
                        val b = color and 0xFF

                        // BT.601 standard conversion
                        val yVal = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                        yPlane[row + x] = yVal.toByte()

                        if (x % 2 == 0 && y % 2 == 0) {
                            val uvIndex = uvRow + (x / 2)
                            val uVal = ((-0.169f * r - 0.331f * g + 0.500f * b) + 128f).toInt().coerceIn(0, 255)
                            val vVal = ((0.500f * r - 0.419f * g - 0.081f * b) + 128f).toInt().coerceIn(0, 255)
                            uPlane[uvIndex] = uVal.toByte()
                            vPlane[uvIndex] = vVal.toByte()
                        }
                    }
                }

                val success = aiEnhanceEngine.processEnhance(
                    yPlane = yPlane,
                    uPlane = uPlane,
                    vPlane = vPlane,
                    width = width,
                    height = height,
                    yStride = width,
                    uvStride = uvWidth,
                    config = AiEnhanceConfig(strength = 1.0f),
                    onStageChanged = { stage ->
                        _uiState.update { it.copy(currentStage = stage) }
                    }
                )

                if (success) {
                    // Reconstruct RGB Bitmap from enhanced YUV planar buffers
                    val enhancedPixels = IntArray(width * height)
                    for (y in 0 until height) {
                        val row = y * width
                        val uvRow = (y / 2) * uvWidth
                        for (x in 0 until width) {
                            val yVal = (yPlane[row + x].toInt() and 0xFF).toFloat()
                            val uvIndex = uvRow + (x / 2)
                            val uVal = (uPlane[uvIndex].toInt() and 0xFF).toFloat() - 128f
                            val vVal = (vPlane[uvIndex].toInt() and 0xFF).toFloat() - 128f

                            val r = (yVal + 1.402f * vVal).toInt().coerceIn(0, 255)
                            val g = (yVal - 0.344136f * uVal - 0.714136f * vVal).toInt().coerceIn(0, 255)
                            val b = (yVal + 1.772f * uVal).toInt().coerceIn(0, 255)

                            enhancedPixels[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }

                    val enhancedBitmap = Bitmap.createBitmap(enhancedPixels, width, height, Bitmap.Config.ARGB_8888)
                    _uiState.update {
                        it.copy(
                            enhancedBitmap = enhancedBitmap,
                            isEnhanced = true,
                            isEnhancing = false,
                            splitFraction = 0.50f,
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isEnhancing = false,
                            errorMessage = "AI Enhancement failed to converge"
                        )
                    }
                }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error during AI enhance", e)
                _uiState.update {
                    it.copy(isEnhancing = false, errorMessage = e.message ?: "Enhancement error")
                }
            }
        }
    }

    fun revert() {
        if (!_uiState.value.isEnhanced) return

        _uiState.update {
            it.copy(
                enhancedBitmap = null,
                isEnhanced = false,
                isSaved = false,
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
                    _uiState.update { it.copy(errorMessage = "Failed to save enhanced image") }
                }
            } catch (e: Exception) {
                logger.e("PhotoReviewViewModel", "Error saving enhanced photo", e)
                _uiState.update { it.copy(errorMessage = "Failed to save: ${e.message}") }
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
