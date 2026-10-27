package com.justwatch.links.ui.screens

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.justwatch.links.ExtractedMedia
import com.justwatch.links.GrowingDownload
import com.justwatch.links.GrowingFileDataSource
import com.justwatch.links.PublicMediaResolver
import com.justwatch.links.R
import com.justwatch.links.data.MediaState
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel
import java.io.File

@OptIn(UnstableApi::class)
internal class VideoScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.PLAYER
    var fullscreenChanged: (Boolean) -> Unit = {}
    private var player: ExoPlayer? = null
    private var fullscreen = false
    private var rendered: Pair<String, MediaState.Ready>? = null

    override fun render(state: WatchState) {
        val media = state.media as? MediaState.Ready ?: return
        val next = state.url to media
        if (rendered == next) return
        close()
        container.removeAllViews()
        rendered = next
        renderPlayer(media.media, state.url, media.file, media.growing)
    }

    override fun onStop() {
        player?.pause()
    }

    override fun close() {
        if (fullscreen) setFullscreen(false)
        val previous = player
        player = null
        previous?.release()
    }

    override fun handleBack(): Boolean {
        if (!fullscreen) return false
        setFullscreen(false)
        return true
    }

    private fun renderPlayer(
        media: ExtractedMedia,
        url: String,
        file: File? = null,
        growing: GrowingDownload? = null,
    ) {
        val body = column().apply { setPadding(dp(16), dp(8), dp(16), dp(12)) }
        container.addView(body, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val stage =
            FrameLayout(this).apply {
                setBackgroundResource(R.drawable.player_stage)
                clipToOutline = true
            }
        body.addView(stage, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val dataSource =
            OkHttpDataSource.Factory(PublicMediaResolver.http)
                .setDefaultRequestProperties(media.headers)
        val sourceFactory =
            if (growing != null)
                DefaultMediaSourceFactory(DataSource.Factory { GrowingFileDataSource(growing) })
            else DefaultMediaSourceFactory(DefaultDataSource.Factory(this, dataSource))
        val exo =
            ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10_000)
                .setSeekForwardIncrementMs(10_000)
                .setMediaSourceFactory(sourceFactory)
                .build()
        player = exo
        val playerView =
            activity.layoutInflater.inflate(R.layout.justwatch_player, stage, false) as PlayerView
        playerView.player = exo
        playerView.setShowRewindButton(true)
        playerView.setShowFastForwardButton(true)
        playerView.findViewById<View>(R.id.player_fullscreen).setOnClickListener {
            setFullscreen(!fullscreen)
        }
        stage.addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        val buffering = CircularProgressIndicator(this).apply { isIndeterminate = true }
        stage.addView(buffering, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        exo.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    buffering.visibility =
                        if (
                            playbackState == Player.STATE_BUFFERING ||
                                playbackState == Player.STATE_IDLE
                        )
                            View.VISIBLE
                        else View.GONE
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (player === exo && !activity.isFinishing && !activity.isDestroyed) {
                        val message = playbackError(error)
                        model.playbackFailed(message)
                    }
                }
            }
        )
        exo.setMediaItem(MediaItem.fromUri(media.url))
        exo.prepare()
        exo.playWhenReady = false
        space(body, 12)
        body.addView(
            text(getString(R.string.player_ready), 14f, muted = true).apply {
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
        )
        fullButton(
            body,
            action(getString(R.string.close_video), R.drawable.ic_close, outlined = true) {
                model.navigate(Screen.HOME)
            },
        )
    }

    private fun playbackError(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException)
                return getString(R.string.playback_http_error, cause.responseCode)
            cause = cause.cause
        }
        return getString(R.string.playback_failed, error.errorCodeName)
    }

    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        val body = (container.getChildAt(0) as? LinearLayout)
        if (body != null) {
            for (index in 1 until body.childCount) body.getChildAt(index).visibility =
                if (enabled) View.GONE else View.VISIBLE
            body.setPadding(
                if (enabled) 0 else dp(16),
                if (enabled) 0 else dp(8),
                if (enabled) 0 else dp(16),
                if (enabled) 0 else dp(12),
            )
        }
        fullscreenChanged(enabled)
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars())
            else show(WindowInsetsCompat.Type.systemBars())
        }
        container.findViewById<View>(R.id.player_fullscreen)?.contentDescription =
            if (enabled) getString(R.string.exit_fullscreen) else getString(R.string.fullscreen)
    }
}
