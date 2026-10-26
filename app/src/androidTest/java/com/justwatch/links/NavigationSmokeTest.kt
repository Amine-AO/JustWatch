package com.justwatch.links

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.media3.ui.PlayerView
import androidx.media3.common.Player
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import android.widget.ScrollView
import android.net.Uri
import java.io.File
import org.junit.Assert.*
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Rule
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {
    @get:Rule val failureCapture = object : TestWatcher() {
        override fun failed(error: Throwable, description: Description) {
            runCatching { screenshot("failure-" + description.methodName) }
        }
    }

    private fun awaitDeleted(file: File) {
        val deadline = System.currentTimeMillis() + 5000
        while (file.exists() && System.currentTimeMillis() < deadline) Thread.sleep(25)
    }

    @Test fun playerAndCacheSurviveActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var file: File
            scenario.onActivity { activity ->
                file = File(File(activity.cacheDir, "justwatch-video-recreation").apply { mkdirs() }, "video.mp4")
                instrumentation.context.assets.open("player-fixture.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
                activity.showNativeVideo(ExtractedMedia(Uri.fromFile(file).toString()), "https://www.instagram.com/reel/recreation", file)
            }
            instrumentation.waitForIdleSync()
            scenario.recreate()
            onView(withText("Ready when you are. Press Play.")).check(matches(isDisplayed()))
            assertTrue("Recreation must retain the temporary file", file.exists())
            onView(withText("Close video")).perform(click())
            awaitDeleted(file)
            assertFalse("Close must remove the retained file", file.exists())
        }
    }

    @Before fun setLightTheme() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // A cold CI emulator can otherwise sleep while Gradle builds the test APK.
        for (command in listOf("settings put secure immersive_mode_confirmations confirmed", "am force-stop com.android.launcher3", "input keyevent KEYCODE_WAKEUP", "wm dismiss-keyguard")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            ).use { it.readBytes() }
        }
        instrumentation.targetContext
            .getSharedPreferences("justwatch", 0).edit()
            .putInt("theme", AppCompatDelegate.MODE_NIGHT_NO).putBoolean("link_wizard_seen", true).commit()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Allow a rendered frame after screen changes or theme recreation.
        Thread.sleep(400)
        // UTP uninstalls the app after tests. Shell-owned output survives cleanup.
        for (command in listOf("mkdir -p /data/local/tmp/justwatch-ui",
            "screencap -p /data/local/tmp/justwatch-ui/$name.png")) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            ).use { it.readBytes() }
        }
    }

    @Test fun settingsAreSeparateAndThemeSwitchSurvivesRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Enjoy the video. Keep your time.")).check(matches(isDisplayed()))
            onView(withText("Connection test")).check(doesNotExist())
            onView(withId(R.id.share_app_button)).check(matches(isDisplayed()))
            screenshot("01-watch-light")
            onView(withId(R.id.settings_button)).perform(click())
            onView(withText("Enable link opening")).check(matches(isDisplayed()))
            screenshot("02-settings-light")
            onView(withText("Enable link opening")).perform(click())
            onView(withText("Open Android settings")).check(matches(isDisplayed()))
            onView(withText("Not now")).perform(click())
            onView(withText("Theme")).perform(scrollTo(), click())
            onView(withText("Dark")).perform(click())
            onView(withText("Enable link opening")).check(matches(isDisplayed()))
            screenshot("03-settings-dark")
            onView(withContentDescription("Back")).perform(click())
            onView(withText("Enjoy the video. Keep your time.")).check(matches(isDisplayed()))
            screenshot("04-watch-dark")
            onView(withId(R.id.settings_button)).perform(click())
            onView(withText("Connection test")).perform(scrollTo(), click())
            onView(withText("Ready to test")).check(matches(isDisplayed()))
            screenshot("06-connection-test")
        }
    }

    @Test fun loadingHasWorkingCancelAndCapturesActualUi() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { it.showLoading("TikTok") }
            onView(withId(R.id.loading_title)).check(matches(withText("Loading your video...")))
            onView(withText("JustWatch | TikTok")).check(matches(isDisplayed()))
            onView(withContentDescription("Back")).check(matches(withEffectiveVisibility(Visibility.GONE)))
            screenshot("05-loading")
            onView(withText("Cancel")).perform(click())
            onView(withText("Enjoy the video. Keep your time.")).check(matches(isDisplayed()))
        }
    }

    @Test fun unsupportedSharedUrlReturnsToInputWithoutStartingPlayback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND)
            .putExtra(Intent.EXTRA_TEXT, "Shared video https://unsupported.example/video")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            onView(withText("Enjoy the video. Keep your time.")).check(matches(isDisplayed()))
            onView(withId(R.id.link_input)).check(matches(isDisplayed()))
        }
    }
    private fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { it.bufferedReader().readText() }

    @Test fun homeIsFixedAndInputStaysUsableWithKeyboard() {
        shell("settings put secure show_ime_with_hard_keyboard 1")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(isAssignableFrom(ScrollView::class.java)).check(doesNotExist())
            onView(withId(R.id.nav_settings)).check(doesNotExist())
            var top = 0
            scenario.onActivity {
                val position = IntArray(2)
                it.findViewById<View>(R.id.settings_button).getLocationOnScreen(position)
                top = position[1]
            }
            onView(withId(R.id.link_input)).perform(click())
            val until = System.currentTimeMillis() + 6000
            var visible = false
            while (!visible && System.currentTimeMillis() < until) {
                scenario.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                Thread.sleep(100)
            }
            assertTrue("Keyboard should be visible", visible)
            onView(withText("Watch")).check(matches(isDisplayed()))
            scenario.onActivity {
                val position = IntArray(2)
                it.findViewById<View>(R.id.settings_button).getLocationOnScreen(position)
                assertEquals("Header must stay at the top", top, position[1])
            }
            screenshot("07-keyboard")
            closeSoftKeyboard()
        }
    }

    @Test fun playerHasLargeWorkingPlayControlAndCleansUpOnClose() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var fixture: File? = null
            scenario.onActivity { activity ->
                val folder = File(activity.cacheDir, "justwatch-video-ui-fixture").apply { mkdirs() }
                val file = File(folder, "video.mp4")
                instrumentation.context.assets.open("player-fixture.mp4").use { input -> file.outputStream().use { input.copyTo(it) } }
                fixture = file
                activity.showNativeVideo(ExtractedMedia(Uri.fromFile(file).toString()), "https://www.instagram.com/reel/ui-fixture", file)
            }
            var ready = false
            val until = System.currentTimeMillis() + 10000
            while (!ready && System.currentTimeMillis() < until) {
                scenario.onActivity {
                    val view = it.findViewById<PlayerView>(R.id.video_view)
                    ready = view.player?.playbackState == Player.STATE_READY
                    assertFalse("Playback must wait for the user", view.player?.playWhenReady ?: false)
                }
                Thread.sleep(100)
            }
            assertTrue("Local fixture should prepare", ready)
            onView(withText("JustWatch | Instagram")).check(matches(isDisplayed()))
            onView(withContentDescription("Back")).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withText("Ready when you are. Press Play.")).check(matches(isDisplayed()))
            scenario.onActivity {
                assertTrue(it.findViewById<View>(androidx.media3.ui.R.id.exo_play_pause).width >= 88 * it.resources.displayMetrics.density - 1)
            }
            onView(withId(androidx.media3.ui.R.id.exo_rew)).check(matches(isDisplayed()))
            onView(withId(androidx.media3.ui.R.id.exo_ffwd)).check(matches(isDisplayed()))
            onView(withContentDescription("Fullscreen")).perform(click())
            onView(withText("Close video")).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withContentDescription("Exit fullscreen")).perform(click())
            onView(withText("Close video")).check(matches(isDisplayed()))
            screenshot("08-player")
            onView(withId(androidx.media3.ui.R.id.exo_play_pause)).perform(click())
            onView(withContentDescription("Pause")).check(matches(isDisplayed()))
            onView(withId(androidx.media3.ui.R.id.exo_play_pause)).perform(click())
            onView(withText("Close video")).perform(click())
            onView(withText("Enjoy the video. Keep your time.")).check(matches(isDisplayed()))
            awaitDeleted(fixture!!)
            assertFalse("Close must remove the temporary video", fixture!!.exists())
        }
    }

    @Test fun localVideoPlaysBeforeDownloadFinishesAndSeekWaitsForTail() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bytes = instrumentation.context.assets.open("progressive-fixture.mp4").use { it.readBytes() }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var session: GrowingDownload
            scenario.onActivity { activity ->
                val folder = File(activity.cacheDir, "justwatch-video-growing-fixture").apply { mkdirs() }
                val file = File(folder, "video.mp4").apply { writeBytes(bytes.copyOf(bytes.size * 3 / 4)) }
                session = GrowingDownload(file)
                assertTrue("Fixture must satisfy the same metadata/buffer gate used by downloads", session.canStart())
                activity.showNativeVideo(ExtractedMedia(Uri.fromFile(file).toString()), "https://www.tiktok.com/@fixture/video/1", file, session)
            }
            fun awaitState(condition: (Player) -> Boolean) {
                val until = System.currentTimeMillis() + 10000
                var matched = false
                while (!matched && System.currentTimeMillis() < until) {
                    scenario.onActivity { matched = condition(it.findViewById<PlayerView>(R.id.video_view).player!!) }
                    Thread.sleep(100)
                }
                assertTrue("Player condition should be met while complete=${session.complete}", matched)
            }
            awaitState { it.playbackState == Player.STATE_READY }
            assertFalse("Preparation must finish before the download", session.complete)
            scenario.onActivity { it.findViewById<PlayerView>(R.id.video_view).player!!.play() }
            awaitState { it.currentPosition > 500 }
            assertFalse("Playback must advance before download completion", session.complete)
            scenario.onActivity { it.findViewById<PlayerView>(R.id.video_view).player!!.seekTo(9500) }
            session.file.appendBytes(bytes.copyOfRange(bytes.size * 3 / 4, bytes.size))
            session.finish()
            awaitState { it.playbackState == Player.STATE_READY || it.playbackState == Player.STATE_ENDED }
            scenario.onActivity {
                val duration = it.findViewById<View>(androidx.media3.ui.R.id.exo_duration)
                val fullscreen = it.findViewById<View>(R.id.player_fullscreen)
                assertTrue("Fullscreen should be to the right of duration", fullscreen.left >= duration.right)
            }
            onView(withText("Close video")).perform(click())
            awaitDeleted(session.file)
            assertFalse("Close must delete the growing download", session.file.exists())
        }
    }

    @Test fun competingHandlerIsDetectedAndRoutingWorksAfterDisablingIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val own = context.packageName
        val other = "com.justwatch.links.fixture"
        val hosts = "facebook.com www.facebook.com instagram.com www.instagram.com"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                shell("pm set-app-links-allowed --user 0 --package $own true")
                shell("pm set-app-links-user-selection --user 0 --package $own false $hosts")
                shell("pm set-app-links --package $other 2 all")
                shell("pm set-app-links-allowed --user 0 --package $other true")
                val blocked = LinkAssociations.read(context)
                assertTrue("Diagnostics must identify the competing handler: " + blocked.details(), blocked.handlers.any { it.packageName == other })
                // Start a real web intent. resolve-activity only lists a resolver,
                // whereas ActivityStarter applies domain approval during launch.
                // Port 8080 keeps this a domain-routing check: JustWatch rejects
                // that port before starting extraction, so no network is needed.
                val launch = "am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d https://www.facebook.com:8080/share/r/justwatch"
                val competingRoute = shell(launch)
                assertTrue("The verified enabled handler should receive the link: $competingRoute", competingRoute.contains("$other/"))
                shell("am force-stop com.android.launcher3")
                shell("am start -W -n $own/.MainActivity")
                scenario.onActivity { it.showLinkWizard() }
                onView(withText("1. Free up your links")).check(matches(isDisplayed()))
                onView(withText("Manage Link test fixture")).perform(scrollTo()).check(matches(isDisplayed()))
                screenshot("wizard-conflict")
                scenario.onActivity { it.showLinkTroubleshooting() }
                onView(withText("Manage Link test fixture")).perform(scrollTo()).check(matches(isDisplayed()))
                screenshot("09-link-conflict")
                shell("pm set-app-links-allowed --user 0 --package $other false")
                shell("pm set-app-links-user-selection --user 0 --package $own true $hosts")
                val selected = LinkAssociations.read(context)
                assertTrue("Selection should persist after disabling the other handler", selected.enabled("facebook.com"))
                assertTrue(selected.enabled("instagram.com"))
                val resolution = shell(launch)
                assertTrue("Facebook should now launch JustWatch: $resolution", resolution.contains("$own/"))
                onView(withText("Enjoy the video. Keep your time.")).check(matches(isDisplayed()))
            } finally {
                shell("pm set-app-links-allowed --user 0 --package $other false")
                shell("pm set-app-links-user-selection --user 0 --package $own false $hosts")
            }
        }
    }

    @Test fun linkSettingsVisitCapturesBeforeAndAfterSelections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val own = context.packageName
        shell("pm set-app-links-user-selection --user 0 --package $own false www.instagram.com")
        shell("pm set-app-links-allowed --user 0 --package $own true")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                scenario.onActivity { it.showLinkTroubleshooting() }
                onView(withText("Manage JustWatch links")).perform(scrollTo(), click())
                assertTrue(context.getSharedPreferences("justwatch", 0).getBoolean("link_capture_pending", false))
                shell("pm set-app-links-user-selection --user 0 --package $own true www.instagram.com")
                shell("am force-stop com.android.launcher3")
                shell("am start -W -n $own/.MainActivity")
                val deadline = System.currentTimeMillis() + 10000
                while (context.getSharedPreferences("justwatch", 0).getBoolean("link_capture_pending", true) && System.currentTimeMillis() < deadline) Thread.sleep(100)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                val prefs = context.getSharedPreferences("justwatch", 0)
                assertFalse(prefs.getBoolean("link_capture_pending", true))
                assertTrue(prefs.getString("link_before", "")!!.contains("www.instagram.com: not enabled"))
                assertTrue(prefs.getString("link_after", "")!!.contains("www.instagram.com: enabled"))
                onView(withText("Copy link diagnostics")).perform(scrollTo()).check(matches(isDisplayed()))
                screenshot("10-link-report")
            } finally {
                shell("pm set-app-links-user-selection --user 0 --package $own false www.instagram.com")
            }
        }
    }

}

