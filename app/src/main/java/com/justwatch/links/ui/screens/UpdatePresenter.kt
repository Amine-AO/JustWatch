package com.justwatch.links.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.justwatch.links.AppUpdates
import com.justwatch.links.BuildConfig
import com.justwatch.links.R
import com.justwatch.links.ui.UpdateState
import com.justwatch.links.ui.WatchViewModel

internal class UpdatePresenter(activity: AppCompatActivity, private val model: WatchViewModel) :
    ScreenUi(activity) {
    private val dialogs = mutableSetOf<AlertDialog>()
    private var rendered: UpdateState? = null
    private var updateProgress: LinearProgressIndicator? = null
    private var updateLabel: TextView? = null
    private var updateDialog: AlertDialog? = null

    fun render(state: UpdateState) {
        if (state == rendered) return
        rendered = state
        renderUpdate(state)
    }

    fun close() {
        dialogs.toList().forEach { it.dismiss() }
        dialogs.clear()
        updateDialog = null
    }

    private fun showAppUpdateMessage(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.app_updates))
            .setMessage(message)
            .setPositiveButton(getString(R.string.ok), null)
            .showTracked()
    }

    private fun renderUpdate(state: UpdateState) {
        if (state !is UpdateState.Downloading) {
            updateDialog?.dismiss()
            updateDialog = null
            updateProgress = null
            updateLabel = null
        }
        when (state) {
            is UpdateState.Message -> {
                model.dismissUpdate()
                showAppUpdateMessage(state.text.resolve(this))
            }
            is UpdateState.Offer ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.update_available, state.info.versionName))
                    .setMessage(
                        getString(R.string.installed_version, BuildConfig.VERSION_NAME) +
                            state.info.notes.ifBlank { getString(R.string.update_default_notes) }
                    )
                    .setPositiveButton(getString(R.string.download_update)) { _, _ ->
                        model.download(state.info)
                    }
                    .setNegativeButton(getString(R.string.later)) { _, _ -> model.dismissUpdate() }
                    .setOnCancelListener { model.dismissUpdate() }
                    .showTracked()
            is UpdateState.Downloading -> {
                if (updateDialog == null) {
                    val panel = column().apply { setPadding(dp(24), dp(12), dp(24), dp(12)) }
                    updateLabel = text("", 15f).also { panel.addView(it) }
                    space(panel, 16)
                    updateProgress = LinearProgressIndicator(this).apply { max = 100 }
                    panel.addView(updateProgress, LinearLayout.LayoutParams(MATCH_PARENT, dp(8)))
                    updateDialog =
                        MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.updating_justwatch))
                            .setView(panel)
                            .setCancelable(false)
                            .setNegativeButton(getString(R.string.cancel)) { _, _ ->
                                model.cancelUpdate()
                            }
                            .showTracked()
                }
                updateLabel?.text =
                    if (state.percent == 100) getString(R.string.verifying_update)
                    else getString(R.string.update_download_progress, state.percent)
                updateProgress?.setProgressCompat(state.percent, true)
            }
            is UpdateState.Install -> {
                model.dismissUpdate()
                if (!packageManager.canRequestPackageInstalls()) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle(getString(R.string.allow_justwatch_updates))
                        .setMessage(getString(R.string.install_permission_description))
                        .setPositiveButton(getString(R.string.open_settings)) { _, _ ->
                            model.awaitInstallPermission(true)
                            try {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:$packageName"),
                                    )
                                )
                            } catch (_: ActivityNotFoundException) {
                                model.awaitInstallPermission(false)
                                showAppUpdateMessage(
                                    getString(R.string.install_permission_fallback)
                                )
                            }
                        }
                        .setNegativeButton(getString(R.string.later), null)
                        .showTracked()
                } else {
                    try {
                        startActivity(AppUpdates.installIntent(activity, state.file))
                    } catch (_: ActivityNotFoundException) {
                        showAppUpdateMessage(getString(R.string.installer_unavailable))
                    }
                }
            }
            else -> Unit
        }
    }

    private fun MaterialAlertDialogBuilder.showTracked(): AlertDialog =
        show().also { dialog ->
            dialogs += dialog
            dialog.setOnDismissListener { dialogs -= dialog }
        }
}
