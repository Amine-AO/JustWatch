package com.justwatch.links.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.justwatch.links.R

internal class PrivacyDialog(private val activity: AppCompatActivity) {
    private var fullDialog: AlertDialog? = null

    private fun showFullPolicy() {
        val web =
            WebView(activity).apply {
                layoutDirection = View.LAYOUT_DIRECTION_LTR
                layoutParams =
                    LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        (activity.resources.displayMetrics.heightPixels * 0.65f).toInt(),
                    )
                settings.apply {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean {
                            if (request.isForMainFrame) openExternal(request.url)
                            return true
                        }
                    }
                // Bundling the notice keeps it available offline and before Pages is published.
                loadUrl("file:///android_asset/legal/privacy.html")
            }

        fullDialog =
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.privacy_policy)
                .setView(web)
                .setPositiveButton(R.string.close, null)
                .create()
                .apply {
                    setOnDismissListener {
                        web.stopLoading()
                        web.destroy()
                    }
                }

        fullDialog?.show()
    }

    private val dialog =
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.privacy_policy)
            .setMessage(
                activity.getString(R.string.privacy_summary) +
                    "\n\n" +
                    activity.getString(R.string.privacy_english_notice)
            )
            .setNeutralButton(R.string.privacy_full_english) { _, _ -> showFullPolicy() }
            .setPositiveButton(R.string.close, null)
            .create()

    fun show() = dialog.show()

    fun dismiss() {
        dialog.dismiss()
        fullDialog?.dismiss()
        fullDialog = null
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "https") return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
