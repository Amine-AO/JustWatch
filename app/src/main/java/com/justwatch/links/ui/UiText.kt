package com.justwatch.links.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.text.BidiFormatter

internal data class UiText(@StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String =
        context.getString(
            id,
            *args
                .map { if (it is String) BidiFormatter.getInstance().unicodeWrap(it) else it }
                .toTypedArray(),
        )
}
