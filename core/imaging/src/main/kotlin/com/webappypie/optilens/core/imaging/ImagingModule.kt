package com.webappypie.optilens.core.imaging

import com.webappypie.optilens.core.imaging.alignment.FrameAlignmentEngine
import com.webappypie.optilens.core.imaging.alignment.NativeFrameAlignmentEngine
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
    abstract fun bindImagingPipeline(impl: FakeImagingPipeline): ImagingPipeline

    @Binds
    @Singleton
    abstract fun bindFrameAlignmentEngine(impl: NativeFrameAlignmentEngine): FrameAlignmentEngine
}
