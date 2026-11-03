package com.justwatch.links.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.justwatch.links.ExtractedMedia
import java.net.URI

internal sealed interface MediaResolution {
    data class Video(val media: ExtractedMedia) : MediaResolution

    data object NonVideo : MediaResolution

    data class Unresolved(val failure: MediaFailure) : MediaResolution
}

internal object ContentMetadata {
    private val mapper = ObjectMapper()

    fun extractor(json: String): MediaResolution = inspect(mapper.readTree(json), 0)

    private fun inspect(
        node: JsonNode,
        depth: Int,
        inheritedHeaders: Map<String, String> = emptyMap(),
    ): MediaResolution {
        if (depth > 4) return unknown()
        val headers =
            inheritedHeaders +
                node.path("http_headers").fields().asSequence().associate {
                    it.key to it.value.asText()
                }
        val entries = node.path("entries")
        if (entries.isArray) {
            val results = entries.take(50).map { inspect(it, depth + 1, headers) }
            return results.filterIsInstance<MediaResolution.Video>().firstOrNull()
                ?: if (
                    entries.size() < 50 &&
                        node.path("playlist_count").asInt(entries.size()) <= entries.size() &&
                        results.isNotEmpty() &&
                        results.all { it == MediaResolution.NonVideo }
                )
                    MediaResolution.NonVideo
                else unknown()
        }
        if (
            node.path("ext").asText() in setOf("jpg", "jpeg", "png", "webp") ||
                node.path("media_type").asText() in setOf("image", "photo", "text")
        )
            return MediaResolution.NonVideo
        val url = node.path("url").asText("")
        val media = ExtractedMedia.fromUrl(url, headers)
        if (media != null && node.path("vcodec").asText() != "none")
            return MediaResolution.Video(media)
        return unknown()
    }

    fun linkedData(json: String, pageUrl: String): MediaResolution =
        linkedData(listOf(json), pageUrl)

    fun linkedData(documents: List<String>, pageUrl: String): MediaResolution {
        val nodes =
            documents.flatMap { json ->
                val root = runCatching { mapper.readTree(json) }.getOrNull()
                when {
                    root == null -> emptyList()
                    root.isArray -> root.toList()
                    else -> listOf(root) + root.path("@graph").toList()
                }
            }
        var sawImage = false
        var sawVideo = false
        for (node in nodes) {
            val identity =
                node.path("url").asText(node.path("mainEntityOfPage").path("@id").asText(""))
            if (!samePage(identity, pageUrl)) continue
            val type = node.path("@type").asText()
            if (type == "VideoObject") {
                sawVideo = true
                val url = node.path("contentUrl").asText("")
                ExtractedMedia.fromUrl(url)?.let {
                    return MediaResolution.Video(it)
                }
            }
            if (type == "ImageObject") sawImage = true
        }
        return if (sawImage && !sawVideo) MediaResolution.NonVideo else unknown()
    }

    private fun samePage(first: String, second: String): Boolean {
        fun identity(raw: String): Pair<String, String>? {
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val host =
                uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
            // Query parameters can identify different Facebook posts at the same path.
            return host to (uri.path.orEmpty().trimEnd('/') + "?" + uri.rawQuery.orEmpty())
        }
        return identity(first) != null && identity(first) == identity(second)
    }

    private fun unknown() =
        MediaResolution.Unresolved(
            MediaFailure(FailureKind.UNKNOWN, "No confirmed playable video or non-video metadata.")
        )
}
