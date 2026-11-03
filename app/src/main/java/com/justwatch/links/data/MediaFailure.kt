package com.justwatch.links.data

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal enum class FailureKind {
    NON_VIDEO,
    CONNECTION,
    RESTRICTED,
    PLAYBACK,
    ENGINE,
    UNKNOWN,
}

internal data class MediaFailure(val kind: FailureKind, val details: String = "") {
    companion object {
        private val nonVideo =
            Regex(
                "(?:^|\\n)(?:ERROR:\\s*)?(?:\\[(?:Instagram|facebook)\\][^\\n]*?:\\s*)?(?:There is no video in this post|This post does not contain a video|No video in this post)[.!]?\\s*(?:$|\\n)",
                RegexOption.IGNORE_CASE,
            )

        fun from(error: Throwable): MediaFailure {
            val causes = generateSequence(error) { it.cause }.take(12).toList()
            val details = causes.mapNotNull { it.message }.distinct().joinToString("\n")
            val text = details.lowercase()
            val kind =
                when {
                    causes.any {
                        it is UnknownHostException ||
                            it is ConnectException ||
                            it is SocketTimeoutException ||
                            it is SSLException
                    } -> FailureKind.CONNECTION
                    // These are explicit extractor outcomes, not a guess based on missing formats.
                    nonVideo.containsMatchIn(details) -> FailureKind.NON_VIDEO
                    listOf(
                            "login required",
                            "login is required",
                            "ip address is blocked",
                            "rate-limit reached",
                            "too many requests",
                        )
                        .any(text::contains) -> FailureKind.RESTRICTED
                    else -> FailureKind.UNKNOWN
                }
            return MediaFailure(kind, details.ifBlank { error.javaClass.simpleName })
        }
    }
}
