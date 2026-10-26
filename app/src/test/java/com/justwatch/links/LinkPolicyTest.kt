package com.justwatch.links

import org.junit.Assert.*
import org.junit.Test

class LinkPolicyTest {
    @Test fun acceptsFacebookVariantsAndShortLinks() {
        for (host in listOf("facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch")) {
            assertNotNull(LinkPolicy.validated("https://$host/share/r/example"))
        }
        assertEquals("TikTok", LinkPolicy.platform("https://vm.tiktok.com/example/"))
        assertEquals("Instagram", LinkPolicy.platform("https://www.instagram.com/reel/example/"))
    }

    @Test fun extractsLinkFromWhatsAppShareText() {
        assertEquals("https://www.facebook.com/share/r/example",
            LinkPolicy.sharedUrl("Watch this: https://www.facebook.com/share/r/example)."))
        assertEquals("https://vt.tiktok.com/example/", LinkPolicy.sharedUrl("https://vt.tiktok.com/example/"))
    }

    @Test fun rejectsLookalikesCredentialsAndUnexpectedPorts() {
        for (url in listOf("http://facebook.com/video", "https://facebook.com.attacker.test/",
            "https://facebook.com@attacker.test/", "https://user:pass@facebook.com/",
            "https://facebook.com:8080/video", "file:///video", "not a URL")) {
            assertNull(url, LinkPolicy.validated(url))
        }
    }
}
