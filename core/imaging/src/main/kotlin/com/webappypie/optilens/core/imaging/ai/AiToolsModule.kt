package com.webappypie.optilens.core.imaging.ai

import com.webappypie.optilens.core.imaging.ai.blur.BlurClassifier
import com.webappypie.optilens.core.imaging.ai.blur.DeblurEngine
import com.webappypie.optilens.core.imaging.ai.download.ModelDownloadManager
import com.webappypie.optilens.core.imaging.ai.inpainting.InpaintingEngine
import com.webappypie.optilens.core.imaging.ai.reflection.ReflectionReductionEngine
import com.webappypie.optilens.core.imaging.ai.registry.AiModelRegistry
import com.webappypie.optilens.core.imaging.ai.restoration.PhotoRestorationEngine
import com.webappypie.optilens.core.imaging.ai.tier.AiDeviceTierGate
import com.webappypie.optilens.core.imaging.ai.tiling.TileProcessingCoordinator
import com.webappypie.optilens.core.imaging.ai.upscale.AiUpscaleEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiToolsModule {

    @Provides
    @Singleton
    fun provideBlurClassifier(): BlurClassifier = BlurClassifier()

    @Provides
    @Singleton
    fun provideDeblurEngine(blurClassifier: BlurClassifier): DeblurEngine =
        DeblurEngine(blurClassifier)

    @Provides
    @Singleton
    fun provideInpaintingEngine(): InpaintingEngine = InpaintingEngine()

    @Provides
    @Singleton
    fun provideReflectionReductionEngine(): ReflectionReductionEngine =
        ReflectionReductionEngine()

    @Provides
    @Singleton
    fun provideAiUpscaleEngine(): AiUpscaleEngine = AiUpscaleEngine()

    @Provides
    @Singleton
    fun providePhotoRestorationEngine(): PhotoRestorationEngine = PhotoRestorationEngine()

    @Provides
    @Singleton
    fun provideAiDeviceTierGate(): AiDeviceTierGate = AiDeviceTierGate()

    @Provides
    @Singleton
    fun provideTileProcessingCoordinator(tierGate: AiDeviceTierGate): TileProcessingCoordinator =
        TileProcessingCoordinator(tierGate)

    @Provides
    @Singleton
    fun provideAiModelRegistry(): AiModelRegistry = AiModelRegistry()

    @Provides
    @Singleton
    fun provideModelDownloadManager(registry: AiModelRegistry): ModelDownloadManager =
        ModelDownloadManager(registry)

    @Provides
    @Singleton
    fun provideAiToolsCoordinator(
        blurClassifier: BlurClassifier,
        deblurEngine: DeblurEngine,
        inpaintingEngine: InpaintingEngine,
        reflectionEngine: ReflectionReductionEngine,
        upscaleEngine: AiUpscaleEngine,
        restorationEngine: PhotoRestorationEngine,
        tileCoordinator: TileProcessingCoordinator,
    ): AiToolsCoordinator = AiToolsCoordinator(
        blurClassifier = blurClassifier,
        deblurEngine = deblurEngine,
        inpaintingEngine = inpaintingEngine,
        reflectionEngine = reflectionEngine,
        upscaleEngine = upscaleEngine,
        restorationEngine = restorationEngine,
        tileCoordinator = tileCoordinator,
    )
}
