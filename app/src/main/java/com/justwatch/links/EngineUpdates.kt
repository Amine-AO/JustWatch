package com.justwatch.links

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Request
import org.json.JSONObject

/** Uses the app's independent DNS. All processing continues to run on the phone. */
internal data class EngineUpdateResult(val version: String, val updated: Boolean)

internal object EngineUpdates {
    fun store(context: Context) =
        EngineStore(
            File(
                context.noBackupFilesDir,
                "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}",
            )
        )

    fun version(context: Context) =
        EngineStore.version(store(context).target) ?: EngineStore.BUNDLED_VERSION

    private val http by lazy {
        PublicMediaResolver.http.newBuilder().callTimeout(90, TimeUnit.SECONDS).build()
    }

    private fun download(url: String, target: File, limit: Long) {
        val request = Request.Builder().url(url).header("User-Agent", "JustWatch-Android").build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Update server returned HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "Empty update response" }
            check(body.contentLength() <= limit) { "Update response is too large" }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        check(total <= limit) { "Update response is too large" }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    fun update(context: Context, smokeCheck: (String) -> Unit): EngineUpdateResult {
        val engine = store(context)
        val metadata = File.createTempFile("engine-release", ".json", context.cacheDir)
        val sums = File.createTempFile("engine-checksums", ".txt", context.cacheDir)
        try {
            download(
                "https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest",
                metadata,
                1024 * 1024,
            )
            val release = JSONObject(metadata.readText())
            val latest = release.getString("tag_name")
            check(
                Regex("\\d{4}\\.\\d{2}\\.\\d{2}").matches(latest) &&
                    !release.optBoolean("prerelease")
            ) {
                context.getString(R.string.unexpected_stable_release)
            }
            if (latest <= version(context))
                return EngineUpdateResult(version(context), updated = false)
            val base = "https://github.com/yt-dlp/yt-dlp/releases/download/$latest"
            download("$base/SHA2-256SUMS", sums, 128 * 1024)
            val digest =
                sums
                    .readLines()
                    .map { it.trim().split(Regex("\\s+")) }
                    .firstOrNull { it.size == 2 && it[1].removePrefix("*") == "yt-dlp" }
                    ?.first()
            check(digest != null && Regex("[a-fA-F0-9]{64}").matches(digest)) {
                context.getString(R.string.missing_release_checksum)
            }
            download("$base/yt-dlp", engine.staged, 32L * 1024 * 1024)
            check(EngineStore.digest(engine.staged).equals(digest, ignoreCase = true)) {
                context.getString(R.string.extractor_checksum_mismatch)
            }
            check(EngineStore.version(engine.staged) == latest) {
                context.getString(R.string.extractor_version_mismatch)
            }
            engine.install { smokeCheck(latest) }
            return EngineUpdateResult(latest, updated = true)
        } finally {
            metadata.delete()
            sums.delete()
            engine.staged.delete()
        }
    }
}
