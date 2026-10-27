package com.justwatch.links.ui

import android.content.Context
import androidx.annotation.StringRes

internal data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())
}
