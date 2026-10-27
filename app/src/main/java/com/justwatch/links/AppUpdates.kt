package com.justwatch.links

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InterruptedIOException
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject

/** Only public release assets are used. Downloaded APKs must match our installed signing key. */
internal object AppUpdates {
    private const val RELEASES = "https://api.github.com/repos/Amine-AO/JustWatch/releases/latest"
    private val http by lazy {
        PublicMediaResolver.http
            .newBuilder()
            .callTimeout(10, TimeUnit.MINUTES)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    class Operation {
        @Volatile
        var cancelled = false
            private set

        @Volatile private var call: Call? = null

        fun cancel() {
            cancelled = true
            call?.cancel()
        }

        fun bind(next: Call) {
            call = next
            if (cancelled) next.cancel()
        }

        fun check() {
            if (cancelled || Thread.currentThread().isInterrupted)
                throw InterruptedIOException("Update cancelled")
        }
    }

    private fun text(url: String, operation: Operation, missingIsEmpty: Boolean = false): String? {
        operation.check()
        val call =
            http.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", "JustWatch-Android")
                    .header("Accept", "application/vnd.github+json")
                    .build()
            )
        operation.bind(call)
        call.execute().use { response ->
            if (missingIsEmpty && response.code == 404) return null
            check(response.isSuccessful) { "Update server returned HTTP ${response.code}" }
            val body = checkNotNull(response.body)
            require(body.contentLength() <= 1024 * 1024) { "Update information is too large" }
            val data =
                body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        operation.check()
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(output.size() + count <= 1024 * 1024) {
                            "Update information is too large"
                        }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            operation.check()
            return data.toString(Charsets.UTF_8)
        }
    }

    fun latest(operation: Operation): AppUpdateInfo? {
        val raw = text(RELEASES, operation, missingIsEmpty = true) ?: return null
        val release = JSONObject(raw)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val assets = release.getJSONArray("assets")
        var metadataUrl: String? = null
        var apkUrl: String? = null
        var apkSize = 0L
        for (index in 0 until assets.length()) {
            val asset = assets.getJSONObject(index)
            when (asset.optString("name")) {
                "update.json" -> metadataUrl = asset.getString("browser_download_url")
                "JustWatch-release.apk" -> {
                    apkUrl = asset.getString("browser_download_url")
                    apkSize = asset.getLong("size")
                }
            }
        }
        // Older manually uploaded releases may not support in-app updating yet.
        if (metadataUrl == null || apkUrl == null) return null
        require(UpdatePolicy.trustedAsset(metadataUrl, "update.json")) {
            "Unexpected release metadata address"
        }
        return parseMetadata(
            checkNotNull(text(metadataUrl, operation)),
            apkUrl,
            apkSize,
            release.optString("body"),
        )
    }

    internal fun parseMetadata(
        raw: String,
        apkUrl: String,
        apkSize: Long,
        notes: String,
    ): AppUpdateInfo {
        val json = JSONObject(raw)
        require(json.getString("packageName") == "com.justwatch.links") {
            "Unexpected app in release metadata"
        }
        val info =
            AppUpdateInfo(
                json.getLong("versionCode"),
                json.getString("versionName"),
                json.getString("apkUrl"),
                json.getString("sha256"),
                json.getLong("size"),
                notes.take(2000),
            )
        UpdatePolicy.validate(info)
        require(info.url == apkUrl && info.size == apkSize) {
            "Release assets do not match update metadata"
        }
        return info
    }

    fun download(
        context: Context,
        info: AppUpdateInfo,
        operation: Operation,
        progress: (Int) -> Unit,
    ): File {
        UpdatePolicy.validate(info)
        val directory = File(context.cacheDir, "app-updates").apply { mkdirs() }
        val staging = File(directory, "${UUID.randomUUID()}.part")
        try {
            operation.check()
            val call =
                http.newCall(
                    Request.Builder()
                        .url(info.url)
                        .header("User-Agent", "JustWatch-Android")
                        .build()
                )
            operation.bind(call)
            call.execute().use { response ->
                check(response.isSuccessful) { "APK download returned HTTP ${response.code}" }
                val body = checkNotNull(response.body)
                require(body.contentLength() == -1L || body.contentLength() == info.size) {
                    "Unexpected APK download size"
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var lastProgress = -1
                body.byteStream().use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            operation.check()
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            require(total <= info.size) { "APK download exceeds expected size" }
                            output.write(buffer, 0, count)
                            digest.update(buffer, 0, count)
                            val percent = (total * 100 / info.size).toInt()
                            if (percent != lastProgress) {
                                lastProgress = percent
                                progress(percent)
                            }
                        }
                    }
                }
                require(total == info.size) { "APK download was incomplete" }
                require(hex(digest.digest()).equals(info.sha256, true)) {
                    "APK checksum does not match the release"
                }
            }
            operation.check()
            verifyArchive(context, staging, info.versionCode, info.versionName)
            operation.check()
            val target = File(directory, "update.apk")
            check(!target.exists() || target.delete()) {
                "Could not replace the previous update download"
            }
            check(staging.renameTo(target)) { "Could not save the update download" }
            return target
        } finally {
            staging.delete()
        }
    }

    @Suppress("DEPRECATION")
    fun verifyArchive(
        context: Context,
        file: File,
        expectedCode: Long? = null,
        expectedName: String? = null,
    ) {
        val flags =
            if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        val installed = manager.getPackageInfo(context.packageName, flags)
        val candidate =
            requireNotNull(manager.getPackageArchiveInfo(file.absolutePath, flags)) {
                "Downloaded file is not a valid APK"
            }
        require(expectedName == null || candidate.versionName == expectedName) {
            "Update version name does not match its release"
        }
        UpdatePolicy.checkArchive(
            context.packageName,
            candidate.packageName,
            code(installed),
            code(candidate),
            expectedCode,
            signers(installed),
            signers(candidate),
        )
        require((candidate.applicationInfo?.minSdkVersion ?: 0) <= Build.VERSION.SDK_INT) {
            "This update needs a newer Android version"
        }
    }

    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo) =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> {
        val signatures =
            if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
        return signatures
            .orEmpty()
            .map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }
            .toSet()
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    fun removeObsoleteDownload(context: Context) {
        val file = downloaded(context)
        if (!file.exists()) return
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        if (info == null || code(info) <= BuildConfig.VERSION_CODE) file.delete()
    }

    fun downloaded(context: Context) = File(context.cacheDir, "app-updates/update.apk")

    fun installIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
