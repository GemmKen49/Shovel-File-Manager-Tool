package com.shovel.filemanager.ui.localization

import android.content.Context
import androidx.annotation.StringRes

/** The app is English only, so the context is returned unchanged. */
fun Context.appLanguageContext(): Context = this

fun Context.appString(@StringRes resource: Int, vararg arguments: Any): String =
    if (arguments.isEmpty()) getString(resource) else getString(resource, *arguments)
