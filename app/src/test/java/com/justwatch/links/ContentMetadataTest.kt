package com.justwatch.links

import com.justwatch.links.data.ContentMetadata
import com.justwatch.links.data.FailureKind
import com.justwatch.links.data.MediaFailure
import com.justwatch.links.data.MediaResolution
import java.net.UnknownHostException
import org.junit.Assert.*
import org.junit.Test

class ContentMetadataTest {
    @Test
    fun mixedPostSelectsFirstVideoAndPreservesHeaders() {
        val result =
            ContentMetadata.extractor(
                """{"http_headers":{"User-Agent":"JustWatch-test"},"entries":[{"ext":"jpg","url":"https://cdn.test/image.jpg"},{"ext":"mp4","url":"https://cdn.test/first.mp4","http_headers":{"Referer":"https://www.instagram.com/"}},{"ext":"mp4","url":"https://cdn.test/second.mp4"}]}"""
            ) as MediaResolution.Video
        assertEquals("https://cdn.test/first.mp4", result.media.url)
        assertEquals("JustWatch-test", result.media.headers["User-Agent"])
        assertEquals("https://www.instagram.com/", result.media.headers["Referer"])
    }

    @Test
    fun emptyOrIncompleteMetadataDoesNotProveNonVideo() {
        listOf("{}", """{"entries":[]}""", """{"entries":[{"ext":"jpg"},{}]}""").forEach {
            assertTrue(ContentMetadata.extractor(it) is MediaResolution.Unresolved)
        }
    }

    @Test
    fun explicitImagesAreNonVideo() {
        assertEquals(
            MediaResolution.NonVideo,
            ContentMetadata.extractor("""{"entries":[{"ext":"jpg"},{"media_type":"photo"}]}"""),
        )
    }

    @Test
    fun unsafeOrAudioOnlyStreamsAreNotPlayed() {
        listOf(
                """{"url":"http://cdn.test/video.mp4"}""",
                """{"url":"https://user:password@cdn.test/video.mp4"}""",
                """{"url":"https://cdn.test/audio.m4a","vcodec":"none"}""",
            )
            .forEach { assertTrue(ContentMetadata.extractor(it) is MediaResolution.Unresolved) }
    }

    @Test
    fun unrelatedPageImageIsNotEvidenceAboutThePost() {
        assertTrue(
            ContentMetadata.linkedData(
                """{"@type":"ImageObject","url":"https://www.facebook.com/photo?id=2"}""",
                "https://www.facebook.com/photo?id=1",
            ) is MediaResolution.Unresolved
        )
        assertEquals(
            MediaResolution.NonVideo,
            ContentMetadata.linkedData(
                """{"@type":"ImageObject","url":"https://www.facebook.com/photo?id=1"}""",
                "https://www.facebook.com/photo?id=1",
            ),
        )
    }

    @Test
    fun noFormatsOrUnavailableErrorsRemainUnknown() {
        listOf(
                "No video formats found",
                "Requested content is not available",
                "No public video found",
                "Could not determine whether there is no video in this post",
            )
            .forEach { assertEquals(FailureKind.UNKNOWN, MediaFailure.from(Exception(it)).kind) }
        assertEquals(
            FailureKind.NON_VIDEO,
            MediaFailure.from(Exception("[Instagram] abc: There is no video in this post")).kind,
        )
        assertEquals(
            FailureKind.RESTRICTED,
            MediaFailure.from(Exception("Your IP address is blocked from accessing this post")).kind,
        )
    }

    @Test
    fun wrappedNetworkFailureIsRecognized() {
        assertEquals(
            FailureKind.CONNECTION,
            MediaFailure.from(Exception("Request failed", UnknownHostException("cdn.test"))).kind,
        )
    }

    @Test
    fun linkedDataPrefersVideoOverAnImageForTheSamePost() {
        val page = "https://www.facebook.com/posts/123"
        val result =
            ContentMetadata.linkedData(
                """[{"@type":"ImageObject","url":"$page"},{"@type":"VideoObject","url":"$page","contentUrl":"https://cdn.test/video.mp4"}]""",
                page,
            )
        assertTrue(result is MediaResolution.Video)
        assertTrue(
            ContentMetadata.linkedData(
                """[{"@type":"ImageObject","url":"$page"},{"@type":"VideoObject","url":"$page"}]""",
                page,
            ) is MediaResolution.Unresolved
        )
    }

    @Test
    fun partialImageCollectionsDoNotProveTheEntirePostIsNonVideo() {
        assertTrue(
            ContentMetadata.extractor("""{"playlist_count":2,"entries":[{"ext":"jpg"}]}""")
                is MediaResolution.Unresolved
        )
    }

    @Test
    fun videoEvidenceAcrossScriptsPreventsAPhotoOnlyClassification() {
        val page = "https://www.facebook.com/posts/123"
        assertTrue(
            ContentMetadata.linkedData(
                listOf(
                    """{"@type":"ImageObject","url":"$page"}""",
                    """{"@type":"VideoObject","url":"$page"}""",
                ),
                page,
            ) is MediaResolution.Unresolved
        )
    }
}
