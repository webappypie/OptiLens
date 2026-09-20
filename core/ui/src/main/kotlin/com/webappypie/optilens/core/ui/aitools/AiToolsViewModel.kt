package com.webappypie.optilens.core.ui.aitools

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webappypie.optilens.core.common.monetization.EntitlementRepository
import com.webappypie.optilens.core.common.monetization.OptiProductIds
import com.webappypie.optilens.core.imaging.ai.AiExecutionStage
import com.webappypie.optilens.core.imaging.ai.AiToolType
import com.webappypie.optilens.core.imaging.ai.AiToolsCoordinator
import com.webappypie.optilens.core.imaging.ai.BlurClassificationResult
import com.webappypie.optilens.core.imaging.ai.DeblurConfig
import com.webappypie.optilens.core.imaging.ai.InpaintingConfig
import com.webappypie.optilens.core.imaging.ai.ReflectionConfig
import com.webappypie.optilens.core.imaging.ai.RestorationConfig
import com.webappypie.optilens.core.imaging.ai.UpscaleConfig
import com.webappypie.optilens.core.logging.AppLogger
import com.webappypie.optilens.core.settings.AppSettings
import com.webappypie.optilens.core.ui.review.PhotoBitmapLoader
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
import javax.inject.Inject

data class AiToolsUiState(
    val photoUri: String = "",
    val originalBitmap: Bitmap? = null,
    val currentBitmap: Bitmap? = null,
    val selectedTool: AiToolType = AiToolType.DEBLUR,
    val isProcessing: Boolean = false,
    val currentStage: AiExecutionStage? = null,
    val blurClassification: BlurClassificationResult? = null,
    val deblurStrength: Float = 0.70f,
    val inpaintingBrushSize: Float = 40f,
    val reflectionStrength: Float = 0.60f,
    val upscaleFactor: Int = 2,
    val restorationStrength: Float = 0.75f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val splitFraction: Float = 0.50f,
    val isHoldingToCompare: Boolean = false,
    val disclosureMessage: String? = null,
    val isSaved: Boolean = false,
    val savedUri: String? = null,
    val errorMessage: String? = null,
    val isPro: Boolean = false,
    val hasAiPack: Boolean = false,
) {
    val isGated: Boolean get() = selectedTool.isGatedByProPack && !isPro && !hasAiPack
}

@HiltViewModel
class AiToolsViewModel @Inject constructor(
    private val photoBitmapLoader: PhotoBitmapLoader,
    private val aiToolsCoordinator: AiToolsCoordinator,
    private val appSettings: AppSettings,
    private val entitlementRepository: EntitlementRepository,
    private val logger: AppLogger,
) : ViewModel() {

    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    // Secondary constructor for testing with custom test dispatchers
    internal constructor(
        photoBitmapLoader: PhotoBitmapLoader,
        aiToolsCoordinator: AiToolsCoordinator,
        appSettings: AppSettings,
        entitlementRepository: EntitlementRepository,
        logger: AppLogger,
        ioDispatcher: CoroutineDispatcher,
    ) : this(photoBitmapLoader, aiToolsCoordinator, appSettings, entitlementRepository, logger) {
        this.ioDispatcher = ioDispatcher
    }

    companion object {
        private const val TAG = "AiToolsViewModel"
    }

    private val _uiState = MutableStateFlow(AiToolsUiState())
    val uiState: StateFlow<AiToolsUiState> = _uiState.asStateFlow()

    private val undoStack = mutableListOf<Bitmap>()
    private val redoStack = mutableListOf<Bitmap>()

    init {
        viewModelScope.launch {
            entitlementRepository.entitlements.collect { entitlements ->
                val isPro = entitlements.hasFullAccess
                val hasPack = entitlements.hasPack(OptiProductIds.PACK_AI_TOOLS)
                _uiState.update { it.copy(isPro = isPro, hasAiPack = hasPack) }
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
                    val classification = aiToolsCoordinator.classifyBlur(bitmap)
                    _uiState.update {
                        it.copy(
                            originalBitmap = bitmap,
                            currentBitmap = bitmap,
                            blurClassification = classification,
                        )
                    }
                } else {
                    _uiState.update { it.copy(errorMessage = "Failed to load image from URI") }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Error loading photo for AI tools", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to load photo") }
            }
        }
    }

    fun selectTool(tool: AiToolType) {
        val disclosure = if (tool.requiresDisclosure) tool.disclosureText else null
        _uiState.update { it.copy(selectedTool = tool, disclosureMessage = disclosure) }
    }

    fun updateDeblurStrength(strength: Float) {
        _uiState.update { it.copy(deblurStrength = strength.coerceIn(0.1f, 1.0f)) }
    }

    fun updateBrushSize(size: Float) {
        _uiState.update { it.copy(inpaintingBrushSize = size.coerceIn(10f, 100f)) }
    }

    fun updateReflectionStrength(strength: Float) {
        _uiState.update { it.copy(reflectionStrength = strength.coerceIn(0.1f, 1.0f)) }
    }

    fun updateUpscaleFactor(factor: Int) {
        val valid = if (factor >= 4) 4 else 2
        _uiState.update { it.copy(upscaleFactor = valid) }
    }

    fun updateRestorationStrength(strength: Float) {
        _uiState.update { it.copy(restorationStrength = strength.coerceIn(0.1f, 1.0f)) }
    }

    fun applyCurrentTool(maskBitmap: Bitmap? = null) {
        val current = _uiState.value.currentBitmap ?: return
        if (_uiState.value.isProcessing) return

        _uiState.update {
            it.copy(isProcessing = true, currentStage = AiExecutionStage.ANALYZING_IMAGE, errorMessage = null)
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val tool = _uiState.value.selectedTool
                val result = when (tool) {
                    AiToolType.DEBLUR -> {
                        aiToolsCoordinator.deblur(
                            bitmap = current,
                            config = DeblurConfig(strength = _uiState.value.deblurStrength),
                            presetClassification = _uiState.value.blurClassification,
                            onStage = { stage -> _uiState.update { it.copy(currentStage = stage) } },
                        )
                    }
                    AiToolType.INPAINTING -> {
                        if (maskBitmap == null) {
                            _uiState.update {
                                it.copy(isProcessing = false, errorMessage = "Please brush over an object to remove first.")
                            }
                            return@launch
                        }
                        aiToolsCoordinator.inpaint(
                            bitmap = current,
                            maskBitmap = maskBitmap,
                            config = InpaintingConfig(),
                            onStage = { stage -> _uiState.update { it.copy(currentStage = stage) } },
                        )
                    }
                    AiToolType.REFLECTION_REDUCTION -> {
                        aiToolsCoordinator.reduceReflections(
                            bitmap = current,
                            config = ReflectionConfig(strength = _uiState.value.reflectionStrength),
                            onStage = { stage -> _uiState.update { it.copy(currentStage = stage) } },
                        )
                    }
                    AiToolType.UPSCALE -> {
                        aiToolsCoordinator.upscale(
                            bitmap = current,
                            config = UpscaleConfig(scaleFactor = _uiState.value.upscaleFactor),
                            onStage = { stage -> _uiState.update { it.copy(currentStage = stage) } },
                        )
                    }
                    AiToolType.RESTORATION -> {
                        aiToolsCoordinator.restore(
                            bitmap = current,
                            config = RestorationConfig(
                                scratchRemovalStrength = _uiState.value.restorationStrength,
                                colorRevivalStrength = _uiState.value.restorationStrength * 0.9f,
                            ),
                            onStage = { stage -> _uiState.update { it.copy(currentStage = stage) } },
                        )
                    }
                }

                // Push previous into undo stack
                undoStack.add(current)
                redoStack.clear()

                _uiState.update {
                    it.copy(
                        currentBitmap = result.processedBitmap,
                        isProcessing = false,
                        currentStage = null,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = false,
                        disclosureMessage = result.disclosureText,
                    )
                }
            } catch (e: Exception) {
                logger.e(TAG, "Error applying AI transformation", e)
                _uiState.update {
                    it.copy(isProcessing = false, currentStage = null, errorMessage = e.message ?: "Processing failed")
                }
            }
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val previous = undoStack.removeAt(undoStack.lastIndex)
        val current = _uiState.value.currentBitmap ?: return
        redoStack.add(current)

        _uiState.update {
            it.copy(
                currentBitmap = previous,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.removeAt(redoStack.lastIndex)
        val current = _uiState.value.currentBitmap ?: return
        undoStack.add(current)

        _uiState.update {
            it.copy(
                currentBitmap = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    fun revertAll() {
        val original = _uiState.value.originalBitmap ?: return
        undoStack.clear()
        redoStack.clear()

        _uiState.update {
            it.copy(
                currentBitmap = original,
                canUndo = false,
                canRedo = false,
                isSaved = false,
                disclosureMessage = null,
            )
        }
    }

    fun setSplitFraction(fraction: Float) {
        _uiState.update { it.copy(splitFraction = fraction.coerceIn(0.0f, 1.0f)) }
    }

    fun setHoldingToCompare(isHolding: Boolean) {
        _uiState.update { it.copy(isHoldingToCompare = isHolding) }
    }

    fun saveCopy() {
        val processed = _uiState.value.currentBitmap ?: return
        if (_uiState.value.isSaved || _uiState.value.isProcessing) return

        _uiState.update { it.copy(isProcessing = true, errorMessage = null) }

        viewModelScope.launch(ioDispatcher) {
            try {
                val keepOriginal = appSettings.keepOriginalEnabled.first()
                val savedUri = photoBitmapLoader.saveEnhancedBitmap(
                    bitmap = processed,
                    originalUri = _uiState.value.photoUri,
                    keepOriginal = keepOriginal,
                )

                if (savedUri != null) {
                    _uiState.update {
                        it.copy(isProcessing = false, isSaved = true, savedUri = savedUri)
                    }
                } else {
                    _uiState.update {
                        it.copy(isProcessing = false, errorMessage = "Failed to export enhanced image")
                    }
                }
            } catch (e: Exception) {
                logger.e(TAG, "Error saving AI transformed photo", e)
                _uiState.update {
                    it.copy(isProcessing = false, errorMessage = e.message ?: "Failed to save photo")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
