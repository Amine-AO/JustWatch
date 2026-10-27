package com.justwatch.links

import java.net.URI
import java.util.Locale

/** Shared URL policy for pasted text, Android VIEW intents and shares. */
object LinkPolicy {
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

    fun sharedUrl(text: String): String =
        Regex("https://[^\\s<>]+", RegexOption.IGNORE_CASE)
            .find(text)
            ?.value
            ?.trimEnd('.', ',', ')', ']', '!', '?') ?: text.trim()

    fun validated(raw: String): String? {
        val uri =
            try {
                URI(raw.trim())
            } catch (_: Exception) {
                return null
            }
        if (
            !uri.scheme.equals("https", true) ||
                uri.host?.lowercase(Locale.ROOT) !in hosts ||
                uri.rawUserInfo != null ||
                uri.port !in listOf(-1, 443)
        )
            return null
        return uri.toASCIIString()
    }

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
