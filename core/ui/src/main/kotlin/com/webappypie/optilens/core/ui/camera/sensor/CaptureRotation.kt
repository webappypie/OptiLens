package com.webappypie.optilens.core.ui.camera.sensor

import android.view.OrientationEventListener
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Maps a physical device orientation (degrees from [OrientationEventListener]) to the
 * `Surface.ROTATION_*` value CameraX expects for `ImageCapture.targetRotation`, so saved photos
 * are upright regardless of how the phone is held or whether the UI itself rotates.
 *
 * [OrientationEventListener.ORIENTATION_UNKNOWN] (device flat) keeps [currentRotation].
 */
fun orientationToSurfaceRotation(orientationDegrees: Int, currentRotation: Int): Int {
    if (orientationDegrees == OrientationEventListener.ORIENTATION_UNKNOWN) return currentRotation
    return when ((orientationDegrees % 360 + 360) % 360) {
        in 45 until 135 -> Surface.ROTATION_270
        in 135 until 225 -> Surface.ROTATION_180
        in 225 until 315 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }
}

/**
 * Tracks the device's physical orientation while in composition and exposes it as a
 * `Surface.ROTATION_*` value to pass to capture calls.
 */
@Composable
fun rememberCaptureRotation(): IntState {
    val context = LocalContext.current
    val rotation = remember { mutableIntStateOf(Surface.ROTATION_0) }

    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                val next = orientationToSurfaceRotation(orientation, rotation.intValue)
                if (next != rotation.intValue) rotation.intValue = next
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }
    return rotation
}
