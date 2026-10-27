package com.justwatch.links.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.justwatch.links.R
import com.justwatch.links.data.MediaState
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class LoadingScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.LOADING
    private lateinit var indicator: CircularProgressIndicator
    private lateinit var detail: TextView

    init {
        build()
    }

    override fun render(state: WatchState) {
        val progress = (state.media as? MediaState.Loading)?.progress ?: 0
        indicator.isIndeterminate = progress == 0
        detail.visibility = if (progress == 0) View.GONE else View.VISIBLE
        if (progress > 0) indicator.setProgressCompat(progress, true)
        detail.text = getString(R.string.download_percent, progress)
    }

    private fun build() {
        val body = fixedPage(center = true).apply { gravity = Gravity.CENTER }
        indicator =
            CircularProgressIndicator(this).apply {
                id = R.id.loading_progress
                isIndeterminate = true
                indicatorSize = dp(56)
            }
        body.addView(indicator, LinearLayout.LayoutParams(dp(64), dp(64)))
        space(body, 24)
        body.addView(
            text(getString(R.string.loading_your_video), 26f, bold = true).apply {
                id = R.id.loading_title
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )
        detail =
            text("", 15f, muted = true).apply {
                gravity = Gravity.CENTER
                visibility = View.GONE
            }
        body.addView(detail, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        space(body, 28)
        body.addView(
            action(getString(R.string.cancel), R.drawable.ic_close, outlined = true) {
                model.navigate(Screen.HOME)
            },
            LinearLayout.LayoutParams(dp(160), dp(56)),
        )
    }
}
