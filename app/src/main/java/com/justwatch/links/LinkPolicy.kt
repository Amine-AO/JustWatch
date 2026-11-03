package com.justwatch.links

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

/** Shared URL policy for pasted text, Android VIEW intents and shares. */
object LinkPolicy {
    sealed interface Result {
        data class Valid(val url: String) : Result

        data object Invalid : Result

        data object Unsupported : Result
    }

    private val webUrl = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
    private val scheme = Regex("^[a-z][a-z0-9+.-]*:", RegexOption.IGNORE_CASE)
    private val hosts =
        setOf(
            "instagram.com",
            "www.instagram.com",
            "facebook.com",
            "www.facebook.com",
            "m.facebook.com",
            "fb.watch",
            "tiktok.com",
            "www.tiktok.com",
            "vm.tiktok.com",
            "vt.tiktok.com",
        )

    fun sharedUrl(text: String): String {
        val input = text.trim()
        // Preserve standalone URLs exactly, including signed query strings and fragments.
        if (input.none(Char::isWhitespace)) return input
        return webUrl.find(input)?.value?.trimEnd('.', ',', ')', ']', '!', '?') ?: input
    }

    fun validate(raw: String): Result {
        val input = sharedUrl(raw)
        val normalized =
            when {
                input.startsWith("https://", true) -> "https://" + input.substring(8)
                input.startsWith("http://", true) -> "https://" + input.substring(7)
                input.startsWith("//") -> "https:$input"
                scheme.containsMatchIn(input) -> return Result.Invalid
                else -> "https://$input"
            }
        val uri =
            try {
                URI(normalized)
            } catch (_: URISyntaxException) {
                return Result.Invalid
            }
        val host = uri.host?.lowercase(Locale.ROOT) ?: return Result.Invalid
        if (!host.contains('.') || uri.rawUserInfo != null || uri.port !in listOf(-1, 443))
            return Result.Invalid
        if (host !in hosts) return Result.Unsupported
        return Result.Valid(uri.toASCIIString())
    }

    fun validated(raw: String): String? = (validate(raw) as? Result.Valid)?.url

    fun platform(url: String): String {
        val host =
            try {
                URI(url).host.orEmpty().lowercase(Locale.ROOT)
            } catch (_: Exception) {
                ""
            }
        return when {
            host == "instagram.com" || host == "www.instagram.com" -> "Instagram"
            host in setOf("tiktok.com", "www.tiktok.com", "vm.tiktok.com", "vt.tiktok.com") ->
                "TikTok"
            else -> "Facebook"
        }
    }
}
