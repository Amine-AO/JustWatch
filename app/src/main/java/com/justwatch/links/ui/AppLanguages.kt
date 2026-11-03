package com.justwatch.links.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.justwatch.links.R
import java.util.Locale

internal object AppLanguages {
    val tags = listOf("en", "ar", "fr", "de", "es", "pt", "zh-Hans", "zgh-Latn", "zgh-Tfng")

    fun label(activity: AppCompatActivity): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        val index = index(tag)
        return if (index < 0) activity.getString(R.string.system_default)
        else activity.resources.getStringArray(R.array.language_names)[index]
    }

    private fun index(tag: String): Int {
        if (tag.isEmpty()) return -1
        val locale = Locale.forLanguageTag(tag)
        return tags.indexOfFirst {
            val candidate = Locale.forLanguageTag(it)
            candidate.language == locale.language &&
                (candidate.script.isEmpty() ||
                    candidate.script == locale.script ||
                    (locale.script.isEmpty() && candidate.script in setOf("Hans", "Tfng")))
        }
    }

    fun choose(activity: AppCompatActivity) {
        val labels =
            arrayOf(
                activity.getString(R.string.system_default),
                *activity.resources.getStringArray(R.array.language_names),
            )
        val index = index(AppCompatDelegate.getApplicationLocales().toLanguageTags()) + 1
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels, index) { dialog, selection ->
                dialog.dismiss()
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(tags.getOrNull(selection - 1).orEmpty())
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
