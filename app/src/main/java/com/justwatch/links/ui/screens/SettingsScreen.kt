package com.justwatch.links.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.justwatch.links.BuildConfig
import com.justwatch.links.R
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class SettingsScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.SETTINGS
    private lateinit var themeDescription: TextView
    private lateinit var linkSummary: TextView
    private lateinit var engineDescription: TextView

    init {
        build()
    }

    override fun render(state: WatchState) {
        themeDescription.text = themeName()
        linkSummary.text = linkStatus()
        engineDescription.text =
            if (state.updatingEngine) getString(R.string.checking_for_updates)
            else getString(R.string.engine_version, state.engineVersion)
    }

    private fun build() {
        val body = scrollPage()
        section(body, getString(R.string.open_shared_links))
        val links = card(body, tonal = true)
        links.addView(text(getString(R.string.link_setup_heading), 20f, bold = true))
        space(links, 8)
        links.addView(text(getString(R.string.link_setup_description), 14f, muted = true))
        space(links, 12)
        linkSummary = text(linkStatus(), 13f, bold = true).also { links.addView(it) }
        space(links, 16)
        fullButton(
            links,
            action(getString(R.string.enable_link_opening), R.drawable.ic_link) {
                model.navigate(Screen.LINKS)
            },
        )
        settingRow(
            body,
            getString(R.string.link_troubleshooting),
            getString(R.string.link_troubleshooting_description),
            R.drawable.ic_info,
        ) {
            model.troubleshooting()
        }
        settingRow(
            body,
            getString(R.string.share_to_justwatch),
            getString(R.string.share_link_description),
            R.drawable.ic_share,
        ) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.share_one_video))
                .setMessage(getString(R.string.share_link_instructions))
                .setPositiveButton(getString(R.string.got_it), null)
                .show()
        }
        section(body, getString(R.string.appearance))
        themeDescription =
            settingRow(body, getString(R.string.theme), themeName(), R.drawable.ic_theme) {
                chooseTheme()
            }
        section(body, getString(R.string.section_app_updates))
        settingRow(
            body,
            getString(R.string.check_for_app_updates),
            getString(R.string.app_update_schedule, BuildConfig.VERSION_NAME),
            R.drawable.ic_network,
        ) {
            model.checkUpdates(true)
        }
        section(body, getString(R.string.app_maintenance))
        engineDescription =
            settingRow(
                body,
                getString(R.string.check_for_engine_updates),
                if (model.state.value.updatingEngine) getString(R.string.checking_for_updates)
                else getString(R.string.engine_version, model.state.value.engineVersion),
                R.drawable.ic_network,
            ) {
                model.updateEngine()
            }
        settingRow(
            body,
            getString(R.string.connection_test),
            getString(R.string.check_independent_dns_and_https),
            R.drawable.ic_network,
        ) {
            model.navigate(Screen.DIAGNOSTICS)
        }
        section(body, getString(R.string.about))
        body.addView(
            text(getString(R.string.app_version, BuildConfig.VERSION_NAME), 16f, bold = true)
        )
        space(body, 6)
        body.addView(text(getString(R.string.home_headline), 14f, muted = true))
        space(body, 16)
        settingRow(
            body,
            getString(R.string.creator_credit),
            "github.com/Amine-AO",
            R.drawable.ic_link,
        ) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Amine-AO")))
            } catch (_: ActivityNotFoundException) {
                Snackbar.make(
                        container,
                        getString(R.string.browser_unavailable),
                        Snackbar.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    private fun chooseTheme() {
        val modes =
            intArrayOf(
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES,
            )
        val selected = modes.indexOf(model.state.value.theme).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.theme))
            .setSingleChoiceItems(
                arrayOf(
                    getString(R.string.use_device_theme),
                    getString(R.string.light),
                    getString(R.string.dark),
                ),
                selected,
            ) { dialog, choice ->
                model.theme(modes[choice])
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun themeName() =
        when (model.state.value.theme) {
            AppCompatDelegate.MODE_NIGHT_YES -> getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> getString(R.string.light)
            else -> getString(R.string.use_device_theme)
        }

    private fun linkStatus(): String =
        if (Build.VERSION.SDK_INT < 31) getString(R.string.legacy_link_settings)
        else model.state.value.links?.summary(this) ?: getString(R.string.checking_supported_links)
}
