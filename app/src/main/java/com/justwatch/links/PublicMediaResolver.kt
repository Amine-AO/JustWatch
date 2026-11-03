package com.justwatch.links

import com.justwatch.links.data.ContentMetadata
import com.justwatch.links.data.FailureKind
import com.justwatch.links.data.MediaFailure
import com.justwatch.links.data.MediaResolution
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps

/** Best-effort extraction of public HTML video metadata, not an access-control bypass. */
object PublicMediaResolver {
    private val bootstrap = OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).build()
    private val doh =
        DnsOverHttps.Builder()
            .client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1)),
                InetAddress.getByAddress(byteArrayOf(1, 0, 0, 1)),
            )
            .build()
    val http: OkHttpClient =
        OkHttpClient.Builder()
            .dns(doh)
            .followRedirects(true)
            .callTimeout(18, TimeUnit.SECONDS)
            .build()

    private fun unescape(s: String): String =
        s.replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")
            .replace("&#x2F;", "/")
            .replace("\\u0026", "&")
            .replace("\\/", "/")

    internal suspend fun resolve(url: String): MediaResolution =
        runInterruptible(Dispatchers.IO) {
            var media: ExtractedMedia? = null
            var status = "No public video URL found in page metadata."
            var outcome: MediaResolution? = null
            try {
                val request =
                    Request.Builder()
                        .url(url)
                        .header(
                            "User-Agent",
                            "Mozilla/5.0 (Android) JustWatch/${BuildConfig.VERSION_NAME}",
                        )
                        .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        status = "Source returned HTTP ${response.code}."
                        outcome =
                            MediaResolution.Unresolved(
                                MediaFailure(
                                    if (response.code in setOf(401, 403, 429))
                                        FailureKind.RESTRICTED
                                    else FailureKind.UNKNOWN,
                                    status,
                                )
                            )
                    } else {
                        var completePage = false
                        val html =
                            response.body
                                ?.byteStream()
                                ?.use { input ->
                                    val out = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    var remaining = 2_000_000
                                    while (remaining > 0) {
                                        val count =
                                            input.read(buffer, 0, minOf(buffer.size, remaining))
                                        if (count < 0) {
                                            completePage = true
                                            break
                                        }
                                        out.write(buffer, 0, count)
                                        remaining -= count
                                    }
                                    out.toString("UTF-8")
                                }
                                .orEmpty()
                        val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
                        for (tag in tags) {
                            val attrs =
                                Regex("([\\w:-]+)\\s*=\\s*([\"'])(.*?)\\2", RegexOption.IGNORE_CASE)
                                    .findAll(tag.value)
                                    .associate {
                                        it.groupValues[1].lowercase() to it.groupValues[3]
                                    }
                            val key = attrs["property"] ?: attrs["name"] ?: ""
                            if (
                                key.lowercase() in
                                    setOf(
                                        "og:video",
                                        "og:video:url",
                                        "og:video:secure_url",
                                        "twitter:player:stream",
                                    )
                            ) {
                                val candidate = unescape(attrs["content"].orEmpty())
                                val playable = ExtractedMedia.fromUrl(candidate)
                                if (playable != null) {
                                    media = playable
                                    status = "Public media URL discovered."
                                    break
                                }
                            }
                        }
                        if (media == null) {
                            val scripts =
                                Regex(
                                    "<script\\b[^>]*type=[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
                                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                                )
                            val result =
                                ContentMetadata.linkedData(
                                    scripts.findAll(html).map { it.groupValues[1] }.toList(),
                                    response.request.url.toString(),
                                )
                            outcome =
                                result.takeUnless {
                                    it == MediaResolution.NonVideo && !completePage
                                }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outcome = MediaResolution.Unresolved(MediaFailure.from(e))
            }
            media?.let { MediaResolution.Video(it) }
                ?: outcome
                ?: MediaResolution.Unresolved(MediaFailure(FailureKind.UNKNOWN, status))
        }
}
