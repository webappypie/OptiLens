package com.webappypie.optilens.core.ui.camera

import androidx.annotation.StringRes
import com.webappypie.optilens.core.camera.model.CameraMode
import com.webappypie.optilens.core.ui.R

/** Localized display name of a capture mode ([CameraMode.label] stays as the English identifier). */
@get:StringRes
val CameraMode.labelRes: Int
    get() = when (this) {
        CameraMode.PHOTO -> R.string.mode_photo
        CameraMode.NIGHT -> R.string.mode_night
        CameraMode.PORTRAIT -> R.string.mode_portrait
        CameraMode.BEST_SHOT -> R.string.mode_best_shot
        CameraMode.PET -> R.string.mode_pet
        CameraMode.FOOD -> R.string.mode_food
        CameraMode.DOCUMENT -> R.string.mode_document
        CameraMode.MOON -> R.string.mode_moon
        CameraMode.WILDLIFE -> R.string.mode_wildlife
        CameraMode.PRO -> R.string.mode_pro
        CameraMode.VIDEO -> R.string.mode_video
    }
