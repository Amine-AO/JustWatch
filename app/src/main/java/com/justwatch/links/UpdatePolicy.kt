package com.justwatch.links

import java.net.URI

internal data class AppUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val url: String,
    val sha256: String,
    val size: Long,
    val notes: String = "",
)

internal object UpdatePolicy {
    const val MAX_APK_BYTES = 300L * 1024 * 1024

    fun trustedAsset(url: String, filename: String): Boolean =
        runCatching {
                val uri = URI(url)
                uri.scheme == "https" &&
                    uri.host == "github.com" &&
                    uri.port == -1 &&
                    uri.userInfo == null &&
                    uri.query == null &&
                    uri.fragment == null &&
                    Regex("/Amine-AO/JustWatch/releases/download/[^/]+/" + Regex.escape(filename))
                        .matches(uri.path)
            }
            .getOrDefault(false)

    fun validate(info: AppUpdateInfo) {
        require(info.versionCode > 0 && Regex("\\d+\\.\\d+\\.\\d+").matches(info.versionName)) {
            "Invalid app version"
        }
        require(info.size in 1..MAX_APK_BYTES) { "Invalid update size" }
        require(Regex("[a-fA-F0-9]{64}").matches(info.sha256)) { "Missing update checksum" }
        require(trustedAsset(info.url, "JustWatch-release.apk")) {
            "Unexpected update download address"
        }
    }

    fun checkArchive(
        expectedPackage: String,
        archivePackage: String?,
        installedCode: Long,
        archiveCode: Long,
        expectedCode: Long?,
        installedSigners: Set<String>,
        archiveSigners: Set<String>,
    ) {
        require(archivePackage == expectedPackage) { "This APK is not JustWatch" }
        require(archiveCode > installedCode) { "This update is not newer than the installed app" }
        require(expectedCode == null || archiveCode == expectedCode) {
            "Update version does not match its release"
        }
        require(installedSigners.isNotEmpty() && archiveSigners == installedSigners) {
            "Update signing key does not match the installed app"
        }
    }
}
