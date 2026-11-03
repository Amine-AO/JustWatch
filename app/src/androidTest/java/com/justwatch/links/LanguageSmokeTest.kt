package com.justwatch.links

import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.justwatch.links.ui.AppLanguages
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageSmokeTest {
    @Before
    fun skipSetupWizard() {
        InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getSharedPreferences("justwatch", 0)
            .edit()
            .putBoolean("link_wizard_seen", true)
            .commit()
    }

    @Test
    fun languagesRecreateUiWithoutLosingDraftAndOnlyArabicUsesRtl() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val previous = AppCompatDelegate.getApplicationLocales()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                val draft = "https://www.instagram.com/p/draft"
                onView(withId(R.id.link_input)).perform(replaceText(draft))
                for (tag in AppLanguages.tags) {
                    scenario.onActivity {
                        AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tag)
                        )
                    }
                    val expected = Locale.forLanguageTag(tag)
                    val ready = AtomicBoolean(false)
                    val deadline = System.currentTimeMillis() + 10000
                    while (!ready.get() && System.currentTimeMillis() < deadline) {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity {
                            val actual = it.resources.configuration.locales[0]
                            ready.set(
                                actual.language == expected.language &&
                                    (expected.script.isEmpty() || actual.script == expected.script)
                            )
                        }
                        if (!ready.get()) Thread.sleep(50)
                    }
                    assertEquals("Locale was not applied: $tag", true, ready.get())
                    onView(withId(R.id.link_input)).check(matches(withText(draft)))
                    scenario.onActivity {
                        val direction =
                            if (expected.language == "ar") View.LAYOUT_DIRECTION_RTL
                            else View.LAYOUT_DIRECTION_LTR
                        assertEquals(
                            direction,
                            it.findViewById<View>(android.R.id.content).layoutDirection,
                        )
                        assertEquals(
                            View.TEXT_DIRECTION_LTR,
                            it.findViewById<View>(R.id.link_input).textDirection,
                        )
                    }
                }
                scenario.onActivity {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }
                instrumentation.waitForIdleSync()
                assertEquals(true, AppCompatDelegate.getApplicationLocales().isEmpty)
            } finally {
                scenario.onActivity { AppCompatDelegate.setApplicationLocales(previous) }
                instrumentation.waitForIdleSync()
            }
        }
    }
}
