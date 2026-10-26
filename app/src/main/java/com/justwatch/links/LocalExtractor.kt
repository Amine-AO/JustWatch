package com.justwatch.links

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object LocalExtractor {
    // The embedded runtime and engine files are shared, so updates and extraction must serialize.
    private val runtime = Mutex()
    private var initialized = false

    sealed interface Download {
        data class Progress(val percent: Int) : Download
        data class Playable(val session: GrowingDownload) : Download
        data class Complete(val file: File) : Download
    }

    private fun initialize(app: Context) {
        if (initialized) return
        YoutubeDL.getInstance().init(app)
        val engine = EngineUpdates.store(app)
        engine.recover()
        val installed = EngineStore.version(engine.target)
        if (installed == null || installed < EngineStore.BUNDLED_VERSION) {
            app.resources.openRawResource(R.raw.justwatch_ytdlp).use { input ->
                engine.staged.outputStream().use { input.copyTo(it) }
            }
            engine.install { checkEngine(EngineStore.BUNDLED_VERSION) }
        }
        app.cacheDir.listFiles()?.filter { it.name.startsWith("justwatch-video-") }
            ?.forEach { it.deleteRecursively() }
        initialized = true
    }

    private fun checkEngine(expected: String) {
        val request = YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") }
        check(YoutubeDL.getInstance().execute(request).out.trim() == expected) { "Updated extractor could not run on this device" }
    }

    private fun request(url: String) = YoutubeDLRequest(url).apply {
        addOption("-f", "best[ext=mp4]/best")
        addOption("--no-playlist")
        addOption("--playlist-items", "1")
        addOption("--socket-timeout", "12")
        addOption("--retries", "1")
        addOption("--extractor-retries", "1")
        addOption("--no-warnings")
    }

    private fun <T> throughProxy(request: YoutubeDLRequest, action: () -> T): T =
        ExtractionProxy { host ->
            ExtractionProxy.connectPublic(host) { PublicMediaResolver.http.dns.lookup(it) }
        }.use { proxy ->
            request.addOption("--proxy", proxy.url)
            action()
        }


    suspend fun updateEngine(context: Context): String = runtime.withLock {
        runInterruptible(Dispatchers.IO) {
            initialize(context.applicationContext)
            EngineUpdates.update(context.applicationContext, ::checkEngine)
        }
    }

    suspend fun resolve(context: Context, url: String): Pair<ExtractedMedia?, String> = runtime.withLock {
        runInterruptible(Dispatchers.IO) {
            initialize(context.applicationContext)
            val request = request(url)
            val info = throughProxy(request) { YoutubeDL.getInstance().getInfo(request) }
            ExtractedMedia.fromInfo(info) to "Extraction engine returned no direct HTTPS stream."
        }
    }

    fun download(context: Context, url: String, progressive: Boolean): Flow<Download> = channelFlow {
        runtime.withLock {
            runInterruptible(Dispatchers.IO) { downloadBlocking(context.applicationContext, url, progressive) }
        }
    }

    private fun ProducerScope<Download>.downloadBlocking(context: Context, url: String, progressive: Boolean) {
        initialize(context)
        val folder = File(context.cacheDir, "justwatch-video-${java.util.UUID.randomUUID()}")
        check(folder.mkdirs()) { "Could not create video cache" }
        val session = GrowingDownload(File(folder, "video.mp4"))
        var complete = false
        try {
            var announced = false
            val request = request(url).apply {
                addOption("--output", File(folder, "video.%(ext)s").absolutePath)
                addOption("--no-part")
                addOption("--newline")
                addOption("--no-mtime")
                addOption("--max-filesize", "200M")
            }
            throughProxy(request) {
                YoutubeDL.getInstance().execute(request, callback = { percent, _, _ ->
                    session.signal()
                    if (!announced && progressive && session.canStart()) {
                        announced = true
                        trySendBlocking(Download.Playable(session)).getOrThrow()
                    }
                    trySend(Download.Progress(percent.toInt().coerceIn(0, 100)))
                })
            }
            val file = folder.listFiles()?.singleOrNull { it.isFile && it.name.startsWith("video.") && it.length() > 0 }
                ?: error("Extractor returned no video file")
            session.finish()
            complete = true
            trySendBlocking(Download.Complete(file)).getOrThrow()
        } finally {
            if (!complete) {
                session.finish(IOException("Video download stopped"))
                session.cancel()
                folder.deleteRecursively()
            }
        }
    }
}
