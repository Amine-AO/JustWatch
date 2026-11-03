package com.justwatch.links

import org.junit.Assert.*
import org.junit.Test

class LinkPolicyTest {
    @Test
    fun acceptsFacebookVariantsAndShortLinks() {
        for (host in listOf("facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch")) {
            assertNotNull(LinkPolicy.validated("https://$host/share/r/example"))
        }
        assertEquals("TikTok", LinkPolicy.platform("https://vm.tiktok.com/example/"))
        assertEquals("Instagram", LinkPolicy.platform("https://www.instagram.com/reel/example/"))
    }

    @Test
    fun extractsLinkFromWhatsAppShareText() {
        assertEquals(
            "https://www.facebook.com/share/r/example",
            LinkPolicy.sharedUrl("Watch this: https://www.facebook.com/share/r/example)."),
        )
        assertEquals(
            "https://vt.tiktok.com/example/",
            LinkPolicy.sharedUrl("https://vt.tiktok.com/example/"),
        )
    }

    @Test
    fun rejectsLookalikesCredentialsAndUnexpectedPorts() {
        for (url in
            listOf(
                "https://facebook.com.attacker.test/",
                "https://facebook.com@attacker.test/",
                "https://user:pass@facebook.com/",
                "https://facebook.com:8080/video",
                "file:///video",
                "not a URL",
            )) {
            assertNull(url, LinkPolicy.validated(url))
        }
    }

    @Test
    fun normalizesMissingSchemeAndHttpAcrossSupportedHosts() {
        for (host in
            listOf(
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
            )) {
            val path = "/video/123?token=a%2Fb%2Bc&next=http%3A%2F%2Fexample.com#part"
            val expected = "https://$host$path"
            for (prefix in listOf("", "http://", "https://", "HTTP://", "//")) {
                assertEquals(expected, LinkPolicy.validated("$prefix$host$path"))
            }
        }
    }

    @Test
    fun normalizesHttpInSharedTextWithoutChangingStandaloneQuery() {
        assertEquals(
            "https://www.facebook.com/share/r/example",
            LinkPolicy.validated("Watch this: http://www.facebook.com/share/r/example)."),
        )
        assertEquals(
            "https://instagram.com/reel/example?token=value!",
            LinkPolicy.validated("instagram.com/reel/example?token=value!"),
        )
    }

    @Test
    fun distinguishesMalformedLinksFromUnsupportedWebsites() {
        for (url in
            listOf(
                "",
                "not-a-link",
                "not a URL",
                "https:/instagram.com/reel/1",
                "javascript:https://instagram.com/reel/1",
                "file:///video",
                "ftp://instagram.com/",
                "https://user:pass@instagram.com/",
                "https://facebook.com:8080/video",
            )) {
            assertEquals(url, LinkPolicy.Result.Invalid, LinkPolicy.validate(url))
        }
        for (url in
            listOf(
                "example.com/video",
                "http://youtube.com/watch?v=1",
                "https://facebook.com.attacker.test/",
                "https://notinstagram.com/",
            )) {
            assertEquals(url, LinkPolicy.Result.Unsupported, LinkPolicy.validate(url))
            assertNull(LinkPolicy.validated(url))
        }
    }
}
