package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.imaging.alignment.FrameAlignmentEngine
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
import com.webappypie.optilens.core.imaging.fusion.MultiFrameFusionEngine
import com.webappypie.optilens.core.imaging.fusion.NativeMultiFrameFusionEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImagingModule {

    @Binds
    @Singleton
    abstract fun bindImagingPipeline(impl: ProductionImagingPipeline): ImagingPipeline

    @Binds
    @Singleton
    abstract fun bindFrameAlignmentEngine(impl: NativeFrameAlignmentEngine): FrameAlignmentEngine

    @Binds
    @Singleton
    abstract fun bindMultiFrameFusionEngine(impl: NativeMultiFrameFusionEngine): MultiFrameFusionEngine
}
