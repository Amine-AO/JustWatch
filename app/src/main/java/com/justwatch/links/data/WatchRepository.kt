package com.justwatch.links.data

import android.content.Context
import com.justwatch.links.ExtractedMedia
import com.justwatch.links.GrowingDownload
import com.justwatch.links.LinkPolicy
import com.justwatch.links.LocalExtractor
import com.justwatch.links.PublicMediaResolver
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

internal sealed interface MediaState {
    data object Idle : MediaState

    data class Loading(val progress: Int = 0) : MediaState

    data class Ready(
        val media: ExtractedMedia,
        val file: File? = null,
        val growing: GrowingDownload? = null,
    ) : MediaState

    data class Failed(val details: String) : MediaState
}

internal class WatchRepository(private val context: Context) {
    fun cleanup(media: MediaState.Ready) {
        cleanupScope.launch { discard(media) }
    }

    private companion object {
        val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    fun prepare(url: String, local: Boolean, progressive: Boolean): Flow<MediaState> =
        flow {
                emit(MediaState.Loading())
                try {
                    if (local || LinkPolicy.platform(url) == "TikTok") {
                        var ready: MediaState.Ready? = null
                        LocalExtractor.download(
                                context,
                                url,
                                progressive && LinkPolicy.platform(url) == "TikTok",
                            )
                            .collect { event ->
                                when (event) {
                                    is LocalExtractor.Download.Progress ->
                                        if (ready == null) emit(MediaState.Loading(event.percent))
                                    is LocalExtractor.Download.Playable -> {
                                        val playable =
                                            MediaState.Ready(
                                                ExtractedMedia(
                                                    event.session.file.toURI().toString()
                                                ),
                                                event.session.file,
                                                event.session,
                                            )
                                        ready = playable
                                        emit(playable)
                                    }
                                    is LocalExtractor.Download.Complete ->
                                        if (ready == null) {
                                            emit(
                                                MediaState.Ready(
                                                    ExtractedMedia(event.file.toURI().toString()),
                                                    event.file,
                                                )
                                            )
                                        }
                                }
                            }
                    } else {
                        var reason = "No public video found."
                        val media =
                            if (LinkPolicy.platform(url) == "Instagram") {
                                try {
                                    val (media, status) = LocalExtractor.resolve(context, url)
                                    reason = status
                                    media
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Exception) {
                                    reason = error.message.orEmpty()
                                    null
                                }
                            } else null
                        val resolved =
                            media
                                ?: PublicMediaResolver.resolve(url).let { (link, status) ->
                                    reason += "\n$status"
                                    link?.let(::ExtractedMedia)
                                }
                        emit(resolved?.let { MediaState.Ready(it) } ?: MediaState.Failed(reason))
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    emit(MediaState.Failed(error.message ?: error.javaClass.simpleName))
                } catch (error: LinkageError) {
                    emit(MediaState.Failed("The extractor runtime could not load on this device."))
                }
            }
            .flowOn(Dispatchers.IO)

    suspend fun discard(media: MediaState.Ready) =
        runInterruptible(Dispatchers.IO) {
            media.growing?.cancel()
            media.file?.parentFile?.deleteRecursively()
            Unit
        }
}
