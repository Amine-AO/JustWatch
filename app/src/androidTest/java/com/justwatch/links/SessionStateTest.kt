package com.justwatch.links

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justwatch.links.data.SettingsRepository
import com.justwatch.links.data.UpdateRepository
import com.justwatch.links.data.WatchRepository
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchViewModel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionStateTest {
    @Test
    fun aNewViewModelRestoresTheSavedDraftAndDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync {
            val saved = SavedStateHandle()
            val store = ViewModelStore()
            fun create(handle: SavedStateHandle) =
                WatchViewModel(
                    WatchRepository(context),
                    UpdateRepository(context),
                    SettingsRepository(context),
                    handle,
                )
            try {
                val original = create(saved)
                store.put("original", original)
                original.draft("https://www.facebook.com/share/r/unfinished")
                original.navigate(Screen.SETTINGS)
                val restored =
                    create(
                        SavedStateHandle(
                            mapOf(
                                "screen" to saved.get<String>("screen"),
                                "draft" to saved.get<String>("draft"),
                                "url" to saved.get<String>("url"),
                            )
                        )
                    )
                store.put("restored", restored)
                assertEquals(Screen.SETTINGS, restored.state.value.screen)
                assertEquals(original.state.value.draft, restored.state.value.draft)
            } finally {
                store.clear()
            }
        }
    }
}
