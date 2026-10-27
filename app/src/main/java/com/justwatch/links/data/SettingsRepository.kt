package com.justwatch.links.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.justwatch.links.DnsDiagnostic
import com.justwatch.links.EngineUpdates
import com.justwatch.links.LinkAssociations
import com.justwatch.links.LocalExtractor
import com.justwatch.links.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SettingsRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("justwatch", Context.MODE_PRIVATE)
    val theme
        get() = prefs.getInt("theme", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    val wizardSeen
        get() = prefs.getBoolean("link_wizard_seen", false)

    val wizardActive
        get() = prefs.getBoolean("link_wizard_active", false)

    val comparison
        get() = prefs.getString("link_changes", null)

    val engineVersion
        get() = EngineUpdates.version(context)

    fun setTheme(mode: Int) {
        prefs.edit().putInt("theme", mode).apply()
    }

    fun wizard(active: Boolean) {
        prefs
            .edit()
            .putBoolean("link_wizard_active", active)
            .putBoolean("link_wizard_seen", true)
            .apply()
    }

    fun shouldCheckUpdate(manual: Boolean): Boolean {
        val now = System.currentTimeMillis()
        val last = prefs.getLong("app_update_checked_at", 0)
        if (!manual && now >= last && now - last < DAY) return false
        prefs.edit().putLong("app_update_checked_at", now).apply()
        return true
    }

    fun resumeInstall(value: Boolean) {
        prefs.edit().putBoolean("resume_app_install", value).apply()
    }

    fun consumeInstall(): Boolean =
        prefs.getBoolean("resume_app_install", false).also { resumeInstall(false) }

    suspend fun diagnose() = DnsDiagnostic.run()

    suspend fun updateEngine() = LocalExtractor.updateEngine(context)

    suspend fun beforeLinkSettings() =
        withContext(Dispatchers.IO) {
            val before = LinkAssociations.read(context)
            prefs
                .edit()
                .putString("link_before", before.details())
                .putString("link_before_states", before.states.toSortedMap().toString())
                .putBoolean("link_before_allowed", before.allowed)
                .putBoolean("link_capture_pending", true)
                .commit()
            Unit
        }

    suspend fun afterLinkSettings() =
        withContext(Dispatchers.IO) {
            val report = LinkAssociations.read(context)
            if (prefs.getBoolean("link_capture_pending", false)) {
                val unchanged =
                    prefs.getString("link_before_states", "") ==
                        report.states.toSortedMap().toString() &&
                        prefs.getBoolean("link_before_allowed", false) == report.allowed
                prefs
                    .edit()
                    .putBoolean("link_capture_pending", false)
                    .putString("link_after", report.details())
                    .putString(
                        "link_changes",
                        if (unchanged) context.getString(R.string.links_unchanged)
                        else context.getString(R.string.links_changed),
                    )
                    .apply()
            }
            report
        }

    suspend fun diagnostics(): String =
        withContext(Dispatchers.IO) {
            buildString {
                appendLine("CURRENT STATE\n" + LinkAssociations.read(context).details())
                prefs.getString("link_before", null)?.let { appendLine("\nBEFORE SETTINGS\n$it") }
                prefs.getString("link_after", null)?.let { appendLine("\nAFTER SETTINGS\n$it") }
                prefs.getString("link_changes", null)?.let { appendLine("\nCOMPARISON\n$it") }
            }
        }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
