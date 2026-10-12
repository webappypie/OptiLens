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
