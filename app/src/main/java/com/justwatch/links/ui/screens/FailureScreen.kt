package com.justwatch.links.ui.screens

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.justwatch.links.R
import com.justwatch.links.data.MediaState
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class FailureScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.ERROR
    private var rendered: Pair<String, String>? = null

    override fun render(state: WatchState) {
        val content = state.url to (state.media as? MediaState.Failed)?.details.orEmpty()
        if (rendered == content) return
        rendered = content
        container.removeAllViews()
        renderFailure(content.first, content.second)
    }

    private fun renderFailure(url: String, status: String) {
        val body = scrollPage().apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(logo(64))
        space(body, 24)
        body.addView(text(getString(R.string.video_unavailable), 28f, bold = true))
        space(body, 12)
        val message =
            if (status.contains("login", true) || status.contains("rate-limit", true))
                getString(R.string.video_restricted_description)
            else getString(R.string.video_failure_description)
        body.addView(text(message, 16f, muted = true))
        space(body, 24)
        fullButton(
            body,
            action(getString(R.string.try_again), R.drawable.ic_play) { model.open(url) },
        )
        fullButton(
            body,
            action(
                getString(R.string.try_restricted_webpage),
                R.drawable.ic_link,
                outlined = true,
            ) {
                model.navigate(Screen.WEB)
            },
        )
        space(body, 12)
        body.addView(text(getString(R.string.webpage_dns_notice), 13f, muted = true))
        val details =
            text(status, 12f, muted = true).apply {
                visibility = View.GONE
                setTextIsSelectable(true)
                typeface = Typeface.MONOSPACE
            }
        fullButton(
            body,
            action(getString(R.string.technical_details), outlined = true) {
                details.visibility =
                    if (details.visibility == View.GONE) View.VISIBLE else View.GONE
            },
        )
        body.addView(details)
        fullButton(
            body,
            action(getString(R.string.close), R.drawable.ic_close, outlined = true) {
                model.navigate(Screen.HOME)
            },
        )
    }
}
