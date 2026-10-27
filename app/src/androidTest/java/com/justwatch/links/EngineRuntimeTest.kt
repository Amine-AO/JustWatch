package com.justwatch.links

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EngineRuntimeTest {
    @Test
    fun officialEngineRunsAndRejectedUpdateRestoresIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        YoutubeDL.getInstance().init(context)
        val store = EngineUpdates.store(context)
        store.recover()
        context.resources.openRawResource(R.raw.justwatch_ytdlp).use { input ->
            store.staged.outputStream().use { input.copyTo(it) }
        }
        fun runtimeVersion(): String =
            YoutubeDL.getInstance()
                .execute(YoutubeDLRequest(emptyList<String>()).apply { addOption("--version") })
                .out
                .trim()
        store.install { assertEquals(EngineStore.BUNDLED_VERSION, runtimeVersion()) }
        val digest = EngineStore.digest(store.target)
        store.target.copyTo(store.staged)
        try {
            store.install { error("Simulated incompatible runtime") }
            fail("Expected rejection")
        } catch (_: IllegalStateException) {}
        assertEquals(digest, EngineStore.digest(store.target))
        assertEquals(EngineStore.BUNDLED_VERSION, runtimeVersion())
        assertFalse(store.staged.exists())
    }
}
