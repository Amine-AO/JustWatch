package com.justwatch.links.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup.LayoutParams
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.justwatch.links.R

internal class PrivacyDialog(private val activity: AppCompatActivity) {
    private val web =
        WebView(activity).apply {
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

    private val dialog =
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

    fun show() = dialog.show()

    fun dismiss() = dialog.dismiss()

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "https") return
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.browser_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
}
