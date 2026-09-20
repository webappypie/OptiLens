package com.webappypie.optilens.core.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import com.webappypie.optilens.core.camera.discovery.AndroidCameraCapabilityDetector
import com.webappypie.optilens.core.camera.discovery.CameraCapabilityDetector
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CameraModule {

    @Binds
    @Singleton
    abstract fun bindCameraController(impl: CameraXController): CameraController

    @Binds
    @Singleton
    abstract fun bindCameraCapabilityDetector(impl: AndroidCameraCapabilityDetector): CameraCapabilityDetector

    @Binds
    @Singleton
    abstract fun bindGalleryRepository(impl: com.webappypie.optilens.core.camera.storage.GalleryRepositoryImpl): com.webappypie.optilens.core.camera.storage.GalleryRepository

    @Binds
    @Singleton
    abstract fun bindStorageMonitor(impl: com.webappypie.optilens.core.camera.storage.StorageMonitorImpl): com.webappypie.optilens.core.camera.storage.StorageMonitor

    companion object {
        @Provides
        @Singleton
        fun provideCameraManager(@ApplicationContext context: Context): CameraManager {
            return context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    }
}
