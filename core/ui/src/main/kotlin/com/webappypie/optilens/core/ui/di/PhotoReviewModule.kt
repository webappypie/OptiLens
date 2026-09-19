package com.webappypie.optilens.core.ui.di

import com.webappypie.optilens.core.ui.review.DefaultPhotoBitmapLoader
import com.webappypie.optilens.core.ui.review.PhotoBitmapLoader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PhotoReviewModule {

    @Binds
    @Singleton
    abstract fun bindPhotoBitmapLoader(
        impl: DefaultPhotoBitmapLoader,
    ): PhotoBitmapLoader
}
