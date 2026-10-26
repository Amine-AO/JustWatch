package com.justwatch.links

import com.fasterxml.jackson.databind.ObjectMapper
import com.yausername.youtubedl_android.mapper.VideoInfo
import org.junit.Assert.*
import org.junit.Test

class ExtractedMediaTest {
    private fun parse(json: String) = ExtractedMedia.fromInfo(
        ObjectMapper().readValue(json, VideoInfo::class.java)
    )

    @Test fun preservesSignedUrlAndPlaybackHeaders() {
        val media = parse("""{
            "url": "https://cdn.example/video.mp4?token=abc&expires=123",
            "http_headers": {"User-Agent": "Extractor UA", "Referer": "https://www.instagram.com/"},
            "unknown_extractor_field": true
        }""")!!
        assertEquals("https://cdn.example/video.mp4?token=abc&expires=123", media.url)
        assertEquals("Extractor UA", media.headers["User-Agent"])
        assertEquals("https://www.instagram.com/", media.headers["Referer"])
    }

    @Test fun acceptsStreamWithoutOptionalHeaders() {
        assertEquals(emptyMap<String, String>(), parse("""{"url":"https://cdn.example/video.mp4"}""")!!.headers)
    }

    @Test fun refusesMissingOrUnsafeDirectStreams() {
        assertNull(parse("""{"entries":[]}"""))
        for (url in listOf("http://cdn.example/video.mp4", "file:///video.mp4",
            "https:///missing-host", "https://user:pass@cdn.example/video.mp4", "not a URL")) {
            assertNull(url, parse("""{"url":"$url"}"""))
        }
    }
}
