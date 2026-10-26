package com.justwatch.links

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdatesTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun publicReleaseMetadataMustMatchItsActualAsset() {
        val url = "https://github.com/Amine-AO/JustWatch/releases/download/v1.0.1/JustWatch-release.apk"
        val data = JSONObject().put("packageName", "com.justwatch.links").put("versionCode", 12)
            .put("versionName", "1.0.1").put("apkUrl", url).put("sha256", "a".repeat(64)).put("size", 1234)
        assertEquals(12L, AppUpdates.parseMetadata(data.toString(), url, 1234, "Release notes").versionCode)
        try { AppUpdates.parseMetadata(data.toString(), url, 4321, ""); fail("Size mismatch must fail") }
        catch (_: IllegalArgumentException) { }
        data.put("packageName", "different.app")
        try { AppUpdates.parseMetadata(data.toString(), url, 1234, ""); fail("Wrong package must fail") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun installerUsesRestrictedContentUriAndOnlyReadAccess() {
        val file = AppUpdates.downloaded(context).apply { parentFile!!.mkdirs(); writeText("fixture") }
        try {
            val intent = AppUpdates.installIntent(context, file)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals("application/vnd.android.package-archive", intent.type)
            assertEquals("content", intent.data!!.scheme)
            assertEquals("${context.packageName}.updates", intent.data!!.authority)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(0, intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try {
                FileProvider.getUriForFile(context, "${context.packageName}.updates", File(context.cacheDir, "private-outside.apk"))
                fail("Files outside the update directory must not be exposed")
            } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
    @Test fun sameVersionAndInvalidArchivesAreRejectedBeforeInstallation() {
        try { AppUpdates.verifyArchive(context, File(context.applicationInfo.sourceDir)); fail("Reinstall must be rejected") }
        catch (error: IllegalArgumentException) { assertTrue(error.message!!.contains("not newer")) }
        val bad = File(context.cacheDir, "invalid-apk-test").apply { writeText("not an APK") }
        try {
            try { AppUpdates.verifyArchive(context, bad); fail("Invalid APK must be rejected") }
            catch (_: IllegalArgumentException) { }
        } finally { bad.delete() }
    }
}
