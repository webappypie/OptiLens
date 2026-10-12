package com.webappypie.optilens.core.ui.text

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-visible text produced outside composition (e.g. by a ViewModel), resolved against the
 * current locale only when shown. Keeps ViewModels free of Context and English literals.
 */
data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {

    @Composable
    fun resolve(): String = stringResource(id, *args.toTypedArray())

    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}

/**
 * Short, localized message for a core error. Internal details (`reason`, `stage`, exception
 * text) are English diagnostics and are deliberately not shown to users.
 */
fun com.webappypie.optilens.core.common.result.OptiError.toUiText(): UiText = UiText(
    when (this) {
        is com.webappypie.optilens.core.common.result.OptiError.CameraUnavailable -> com.webappypie.optilens.core.ui.R.string.error_camera_unavailable
        is com.webappypie.optilens.core.common.result.OptiError.ProcessingFailed -> com.webappypie.optilens.core.ui.R.string.error_processing
        is com.webappypie.optilens.core.common.result.OptiError.CapabilityNotSupported -> com.webappypie.optilens.core.ui.R.string.error_not_supported
        is com.webappypie.optilens.core.common.result.OptiError.StorageFailed -> com.webappypie.optilens.core.ui.R.string.error_storage
        is com.webappypie.optilens.core.common.result.OptiError.PermissionDenied -> com.webappypie.optilens.core.ui.R.string.error_permission
        is com.webappypie.optilens.core.common.result.OptiError.Unknown -> com.webappypie.optilens.core.ui.R.string.error_unknown
    },
)
