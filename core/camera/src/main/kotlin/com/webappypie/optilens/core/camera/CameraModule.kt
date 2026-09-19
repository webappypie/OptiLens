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

    companion object {
        @Provides
        @Singleton
        fun provideCameraManager(@ApplicationContext context: Context): CameraManager {
            return context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        }
    }
}
