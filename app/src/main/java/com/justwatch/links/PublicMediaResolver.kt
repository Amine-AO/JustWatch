package com.justwatch.links

import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/** Best-effort extraction of public HTML video metadata, not an access-control bypass. */
object PublicMediaResolver {
    private val bootstrap = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build()
    private val doh = DnsOverHttps.Builder()
        .client(bootstrap)
        .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
        .bootstrapDnsHosts(
            InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
            InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1))
        ).build()
    val http: OkHttpClient = OkHttpClient.Builder()
        .dns(doh)
        .followRedirects(true)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    private fun unescape(s: String): String = s.replace("&amp;", "&")
        .replace("&#38;", "&").replace("&quot;", "\"")
        .replace("&#x2F;", "/").replace("\\u0026", "&")
        .replace("\\/", "/")

    private fun structuredVideo(html: String): String? {
        val keys = listOf("video_url", "playAddr", "downloadAddr", "playUrl")
        for (key in keys) {
            val keyPosition = html.indexOf(key)
            if (keyPosition < 0) continue
            val httpsStart = html.indexOf("https", keyPosition)
            if (httpsStart < 0 || httpsStart - keyPosition > 120) continue
            val stop = html.indexOf('"', httpsStart)
            if (stop < 0) continue
            val candidate = html.substring(httpsStart, stop).replace("\\u0026", "&").replace("\\/", "/")
            val uri = Uri.parse(candidate)
            if (uri.scheme == "https" && !uri.host.isNullOrBlank()) return candidate
        }
        return null
    }

    suspend fun resolve(url: String): Pair<String?, String> = kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) {
        var media: String? = null
        var status = "No public video URL found in page metadata."
        try {
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) JustWatch/0.3").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    status = "Source returned HTTP ${response.code}."
                } else {
                    val html = response.body?.byteStream()?.use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var remaining = 2_000_000
                        while (remaining > 0) {
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            remaining -= count
                        }
                        out.toString("UTF-8")
                    }.orEmpty()
                    val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
                    for (tag in tags) {
                        val attrs = Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.IGNORE_CASE)
                            .findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[3] }
                        val key = attrs["property"] ?: attrs["name"] ?: ""
                        if (key.lowercase() in setOf("og:video", "og:video:url", "og:video:secure_url", "twitter:player:stream")) {
                            val candidate = unescape(attrs["content"].orEmpty())
                            val uri = Uri.parse(candidate)
                            if (uri.scheme == "https" && !uri.host.isNullOrBlank()) {
                                media = candidate
                                status = "Public media URL discovered."
                                break
                            }
                        }
                    }
                    if (media == null) {
                        media = structuredVideo(html)
                        status = if (media != null) "Structured public video URL found." else "No public video found; HTML size: " + html.length
                    }
                }
            }
        } catch (e: Exception) {
            status = "Media resolution failed: ${e.javaClass.simpleName}"
        }
        media to status
    }
}
