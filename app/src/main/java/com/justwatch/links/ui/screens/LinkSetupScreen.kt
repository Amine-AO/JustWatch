package com.justwatch.links.ui.screens

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.justwatch.links.LinkAssociations
import com.justwatch.links.R
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class LinkSetupScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.LINKS

    private data class Content(
        val wizard: Boolean,
        val report: LinkAssociations.Report?,
        val comparison: String?,
    )

    private var rendered: Content? = null

    override fun render(state: WatchState) {
        val next = Content(state.wizardActive, state.links, state.comparison)
        if (next == rendered) return
        rendered = next
        container.removeAllViews()
        if (next.wizard) renderLinkWizard() else renderLinkTroubleshooting()
    }

    private fun openLinkSettings(targetPackage: String = packageName) =
        model.linkSettings(targetPackage)

    private fun renderLinkWizard() {
        val body = scrollPage()
        val report = model.state.value.links ?: return
        val conflict = report.handlers.firstOrNull()
        body.addView(
            text(
                if (conflict != null) getString(R.string.wizard_free_links)
                else getString(R.string.wizard_enable_links),
                26f,
                bold = true,
            )
        )
        space(body, 16)
        if (conflict != null) {
            body.addView(text(getString(R.string.wizard_conflict_description, conflict.label), 16f))
            space(body, 12)
            body.addView(text(conflict.hosts.joinToString("\n"), 14f, muted = true))
            space(body, 20)
            fullButton(
                body,
                action(getString(R.string.manage_handler, conflict.label), R.drawable.ic_link) {
                    openLinkSettings(conflict.packageName)
                },
            )
            space(body, 12)
            body.addView(text(getString(R.string.wizard_return_hint), 14f, muted = true))
        } else {
            body.addView(text(getString(R.string.wizard_enable_description), 16f))
            space(body, 20)
            body.addView(text(report.summary(this), 15f, bold = true))
            space(body, 20)
            fullButton(
                body,
                action(getString(R.string.manage_justwatch_links), R.drawable.ic_link) {
                    openLinkSettings()
                },
            )
        }
        space(body, 16)
        fullButton(
            body,
            action(getString(R.string.done_for_now), R.drawable.ic_check, outlined = true) {
                model.navigate(Screen.HOME)
            },
        )
        space(body, 12)
        fullButton(
            body,
            action(getString(R.string.link_diagnostics), R.drawable.ic_info, outlined = true) {
                model.troubleshooting()
            },
        )
    }

    private fun renderLinkTroubleshooting() {
        val body = scrollPage()
        space(body, 12)
        body.addView(text(getString(R.string.keep_shared_links_in_justwatch), 26f, bold = true))
        space(body, 12)
        body.addView(text(getString(R.string.link_report_description), 15f, muted = true))
        space(body, 20)
        val report = model.state.value.links ?: return
        val status = card(body, tonal = true)
        status.addView(text(report.summary(this), 15f, bold = true))
        space(status, 16)
        fullButton(
            status,
            action(getString(R.string.manage_justwatch_links), R.drawable.ic_link) {
                openLinkSettings()
            },
        )
        section(body, getString(R.string.other_enabled_link_handlers))
        if (report.handlers.isEmpty()) {
            body.addView(text(getString(R.string.no_visible_conflicts), 14f, muted = true))
        }
        for (handler in report.handlers) {
            settingRow(
                body,
                getString(R.string.manage_handler, handler.label),
                handler.hosts.joinToString(),
                R.drawable.ic_link,
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.disable_handler_title, handler.label))
                    .setMessage(getString(R.string.disable_handler_description))
                    .setPositiveButton(getString(R.string.open_app_settings)) { _, _ ->
                        openLinkSettings(handler.packageName)
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
        }
        val comparison = model.state.value.comparison
        if (comparison != null) {
            section(body, getString(R.string.last_settings_visit))
            body.addView(text(model.state.value.comparison.orEmpty(), 14f, muted = true))
        }
        section(body, getString(R.string.browser_troubleshooting_heading))
        body.addView(
            text(getString(R.string.browser_troubleshooting_description), 14f, muted = true)
        )
        space(body, 16)
        fullButton(
            body,
            action(
                getString(R.string.copy_link_diagnostics),
                R.drawable.ic_paste,
                outlined = true,
            ) {
                model.copyReport()
            },
        )
    }
}
