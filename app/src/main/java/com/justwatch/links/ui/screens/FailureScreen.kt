package com.justwatch.links.ui.screens

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.justwatch.links.R
import com.justwatch.links.data.FailureKind
import com.justwatch.links.data.MediaFailure
import com.justwatch.links.data.MediaState
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class FailureScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.ERROR
    private var rendered: Pair<String, MediaFailure>? = null

    override fun render(state: WatchState) {
        val failure = (state.media as? MediaState.Failed)?.failure ?: return
        val content = state.url to failure
        if (rendered == content) return
        rendered = content
        container.removeAllViews()
        val body =
            scrollPage().apply {
                gravity = Gravity.CENTER
                setPaddingRelative(dp(24), dp(24), dp(24), dp(24))
            }
        val (title, description) =
            when (failure.kind) {
                FailureKind.NON_VIDEO ->
                    R.string.post_no_video to R.string.post_no_video_description
                FailureKind.CONNECTION ->
                    R.string.connection_failed to R.string.connection_failed_description
                FailureKind.RESTRICTED ->
                    R.string.video_restricted to R.string.video_restricted_description
                FailureKind.PLAYBACK ->
                    R.string.video_playback_failed to R.string.video_playback_description
                FailureKind.ENGINE ->
                    R.string.engine_unavailable_title to R.string.engine_unavailable_description
                FailureKind.UNKNOWN ->
                    R.string.video_unavailable to R.string.video_failure_description
            }
        body.addView(centeredText(getString(title), 26f, bold = true))
        space(body, 12)
        body.addView(centeredText(getString(description), 16f, muted = true))
        space(body, 24)
        val nonVideo = failure.kind == FailureKind.NON_VIDEO
        val engine = failure.kind == FailureKind.ENGINE
        fullButton(
            body,
            action(
                getString(
                    if (nonVideo) R.string.use_another_link
                    else if (engine) R.string.open_settings else R.string.try_again
                ),
                if (nonVideo) R.drawable.ic_link
                else if (engine) R.drawable.ic_settings else R.drawable.ic_refresh,
            ) {
                if (nonVideo) model.useAnotherLink()
                else if (engine) model.navigate(Screen.SETTINGS) else model.open(state.url)
            },
        )
        if (
            failure.kind in setOf(FailureKind.UNKNOWN, FailureKind.RESTRICTED, FailureKind.PLAYBACK)
        ) {
            space(body, 8)
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
            space(body, 8)
            body.addView(centeredText(getString(R.string.webpage_fallback_hint), 13f, muted = true))
        }
        if (!nonVideo) {
            space(body, 12)
            fullButton(
                body,
                action(getString(R.string.use_another_link), R.drawable.ic_link, outlined = true) {
                    model.useAnotherLink()
                },
            )
        }
        if (failure.details.isNotBlank()) {
            space(body, 12)
            val details =
                text(failure.details.take(16000), 12f, muted = true).apply {
                    visibility = View.GONE
                    setTextIsSelectable(true)
                    textDirection = View.TEXT_DIRECTION_LTR
                    textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                    typeface = Typeface.MONOSPACE
                }
            textAction(body, getString(R.string.show_details)) { button ->
                val expanded = details.visibility == View.VISIBLE
                details.visibility = if (expanded) View.GONE else View.VISIBLE
                button.text =
                    getString(if (expanded) R.string.show_details else R.string.hide_details)
            }
            body.addView(details, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
    }

    private fun centeredText(
        value: String,
        size: Float,
        muted: Boolean = false,
        bold: Boolean = false,
    ) =
        text(value, size, muted, bold).apply {
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

    private fun textAction(parent: LinearLayout, label: String, click: (MaterialButton) -> Unit) {
        val button =
            MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle)
                .apply {
                    text = label
                    isAllCaps = false
                    minHeight = dp(48)
                    setOnClickListener { click(this) }
                }
        parent.addView(button, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
}
