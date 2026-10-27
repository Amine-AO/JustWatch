package com.justwatch.links

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    private val url =
        "https://github.com/Amine-AO/JustWatch/releases/download/v1.0.1/JustWatch-release.apk"

    private fun rejected(action: () -> Unit) {
        try {
            action()
            fail("Expected update rejection")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun metadataMustDescribeBoundedOfficialReleaseAssets() {
        val info = AppUpdateInfo(12, "1.0.1", url, "a".repeat(64), 1000)
        UpdatePolicy.validate(info)
        for (bad in
            listOf(
                info.copy(url = url.replace("https", "http")),
                info.copy(url = url.replace("github.com", "github.com.example.org")),
                info.copy(url = url.replace("Amine-AO", "someone-else")),
                info.copy(url = url + "?redirect=evil"),
                info.copy(sha256 = "bad"),
                info.copy(size = UpdatePolicy.MAX_APK_BYTES + 1),
                info.copy(versionName = "1.0.1-dev"),
            )) {
            rejected { UpdatePolicy.validate(bad) }
        }
    }

    @Test
    fun rejectsDowngradesWrongPackagesAndDifferentOrMissingSigningKeys() {
        fun verify(
            pkg: String? = "com.justwatch.links",
            installed: Long = 11,
            candidate: Long = 12,
            expected: Long? = 12,
            signers: Set<String> = setOf("permanent-key"),
        ) =
            UpdatePolicy.checkArchive(
                "com.justwatch.links",
                pkg,
                installed,
                candidate,
                expected,
                setOf("permanent-key"),
                signers,
            )
        verify()
        rejected { verify(pkg = "other.app") }
        rejected { verify(candidate = 11, expected = 11) }
        rejected { verify(candidate = 10, expected = 10) }
        rejected { verify(expected = 13) }
        rejected { verify(signers = setOf("another-key")) }
        rejected { verify(signers = emptySet()) }
        rejected { verify(signers = setOf("permanent-key", "another-key")) }
    }
}
