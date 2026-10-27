package com.justwatch.links.ui.screens

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.justwatch.links.LinkPolicy
import com.justwatch.links.R
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class WebScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.WEB
    private var player: WebView? = null
    private var renderedUrl: String? = null

    override fun render(state: WatchState) {
        if (renderedUrl == state.url) return
        close()
        container.removeAllViews()
        renderedUrl = state.url
        renderViewer(state.url)
    }

    override fun onStart() {
        player?.onResume()
    }

    override fun onStop() {
        player?.onPause()
    }

    override fun close() {
        player?.destroy()
        player = null
    }

    private fun renderViewer(url: String) {
        val body = column()
        container.addView(body, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        body.addView(
            text(getString(R.string.webpage_notice), 13f, muted = true).apply {
                setPadding(dp(16), dp(8), dp(16), dp(12))
            }
        )
        val web = WebView(this)
        player = web
        body.addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    if (!request.isForMainFrame) return false
                    val next = request.url
                    if (next.toString() == url) return false
                    if (LinkPolicy.validated(next.toString()) == null) return true
                    val path = next.path.orEmpty()
                    return when (LinkPolicy.platform(next.toString())) {
                        "TikTok" -> !path.contains("/video/") && !path.startsWith("/player/v1/")
                        "Instagram" -> listOf("/reel/", "/p/", "/tv/").none { path.startsWith(it) }
                        else ->
                            listOf("/reel/", "/watch/", "/share/", "/videos/").none {
                                path.startsWith(it)
                            }
                    }
                }
            }
        fullButton(
            body,
            action(getString(R.string.close_video), R.drawable.ic_close, outlined = true) {
                model.navigate(Screen.HOME)
            },
        )
        web.loadUrl(url)
    }
}
