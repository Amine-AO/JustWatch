package com.justwatch.links

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel
import com.justwatch.links.ui.screens.AppScreen
import com.justwatch.links.ui.screens.DiagnosticsScreen
import com.justwatch.links.ui.screens.FailureScreen
import com.justwatch.links.ui.screens.HomeScreen
import com.justwatch.links.ui.screens.LinkSetupScreen
import com.justwatch.links.ui.screens.LoadingScreen
import com.justwatch.links.ui.screens.ScreenUi
import com.justwatch.links.ui.screens.SettingsScreen
import com.justwatch.links.ui.screens.UpdatePresenter
import com.justwatch.links.ui.screens.VideoScreen
import com.justwatch.links.ui.screens.WebScreen
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val model: WatchViewModel by viewModels { WatchViewModel.Factory(application) }
    private val ui by lazy { ScreenUi(this) }
    private lateinit var root: LinearLayout
    private lateinit var header: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerLogo: ImageView
    private lateinit var backButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private lateinit var content: FrameLayout
    private lateinit var updates: UpdatePresenter
    private var current: AppScreen? = null
    private var keyboardVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        delegate.localNightMode = model.state.value.theme
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dark =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        buildLayout()
        updates = UpdatePresenter(this, model)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            keyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            current?.layoutChanged(keyboardVisible)
            WindowInsetsCompat.CONSUMED
        }
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (current?.handleBack() != true) navigateBack()
                }
            },
        )
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { model.state.collect(::render) }
        }
        if (savedInstanceState == null) {
            acceptIntent(intent)
            if (
                model.state.value.screen == Screen.HOME &&
                    !model.state.value.wizardSeen &&
                    Build.VERSION.SDK_INT >= 31
            ) {
                showLinkWizard()
            }
        }
    }

    private fun buildLayout() =
        with(ui) {
            root =
                column().apply {
                    setBackgroundColor(color(R.color.jw_surface))
                    isFocusableInTouchMode = true
                }
            header =
                LinearLayout(this@MainActivity).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12), 0, dp(12), 0)
                }
            backButton = headerButton(getString(R.string.back), R.drawable.ic_back, ::navigateBack)
            header.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))
            headerLogo = logo(36)
            header.addView(
                headerLogo,
                LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                    marginStart = dp(4)
                    marginEnd = dp(12)
                },
            )
            headerTitle =
                text(getString(R.string.app_name), 21f, bold = true).apply {
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
            header.addView(headerTitle, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            settingsButton =
                headerButton(getString(R.string.settings), R.drawable.ic_settings) {
                        model.navigate(Screen.SETTINGS)
                    }
                    .apply { id = R.id.settings_button }
            header.addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
            root.addView(header, LinearLayout.LayoutParams(MATCH_PARENT, dp(64)))
            content = FrameLayout(this@MainActivity)
            root.addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                current?.layoutChanged(keyboardVisible)
            }
            setContentView(root)
        }

    private fun render(state: WatchState) {
        if (delegate.localNightMode != state.theme) delegate.localNightMode = state.theme
        if (current?.destination != state.screen) {
            current?.close()
            val screen = createScreen(state.screen)
            current = screen
            content.removeAllViews()
            content.addView(screen.container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(
                root.windowToken,
                0,
            )
            root.requestFocus()
        }
        renderHeader(state)
        current?.render(state)
        current?.layoutChanged(keyboardVisible)
        updates.render(state.updates)
        state.engineMessage?.let { message ->
            model.engineMessageShown()
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.extraction_engine))
                .setMessage(message.resolve(this))
                .setPositiveButton(getString(R.string.ok), null)
                .show()
        }
        state.settingsPackage?.let { target ->
            model.settingsOpened()
            launchLinkSettings(target)
        }
        state.copiedReport?.let { report ->
            model.reportCopied()
            getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(
                    ClipData.newPlainText(getString(R.string.justwatch_link_diagnostics), report)
                )
            Snackbar.make(root, getString(R.string.link_diagnostics_copied), Snackbar.LENGTH_SHORT)
                .show()
        }
    }

    private fun createScreen(screen: Screen): AppScreen =
        when (screen) {
            Screen.HOME -> HomeScreen(this, model)
            Screen.SETTINGS -> SettingsScreen(this, model)
            Screen.LINKS -> LinkSetupScreen(this, model)
            Screen.DIAGNOSTICS -> DiagnosticsScreen(this, model)
            Screen.LOADING -> LoadingScreen(this, model)
            Screen.PLAYER ->
                VideoScreen(this, model).apply {
                    fullscreenChanged = { enabled ->
                        header.visibility = if (enabled) View.GONE else View.VISIBLE
                    }
                }
            Screen.ERROR -> FailureScreen(this, model)
            Screen.WEB -> WebScreen(this, model)
        }

    private fun renderHeader(state: WatchState) {
        headerTitle.text =
            when (state.screen) {
                Screen.HOME -> getString(R.string.app_name)
                Screen.SETTINGS -> getString(R.string.settings)
                Screen.LINKS ->
                    if (state.wizardActive) getString(R.string.set_up_links)
                    else getString(R.string.link_troubleshooting)
                Screen.DIAGNOSTICS -> getString(R.string.connection_test)
                else -> getString(R.string.platform_title, LinkPolicy.platform(state.url))
            }
        val media = state.screen in setOf(Screen.LOADING, Screen.PLAYER, Screen.ERROR, Screen.WEB)
        backButton.visibility =
            if (state.screen != Screen.HOME && !media) View.VISIBLE else View.GONE
        headerLogo.visibility =
            if (state.screen == Screen.HOME || media) View.VISIBLE else View.GONE
        settingsButton.visibility = if (state.screen == Screen.HOME) View.VISIBLE else View.GONE
    }

    private fun navigateBack() {
        when (model.state.value.screen) {
            Screen.HOME -> finish()
            Screen.DIAGNOSTICS,
            Screen.LINKS -> model.navigate(Screen.SETTINGS)
            else -> model.navigate(Screen.HOME)
        }
    }

    internal fun showLinkWizard() = model.navigate(Screen.LINKS)

    internal fun showLinkTroubleshooting() = model.troubleshooting()

    internal fun showLoading(platform: String) = model.loading(platform)

    internal fun showNativeVideo(
        media: ExtractedMedia,
        url: String,
        file: File? = null,
        growing: GrowingDownload? = null,
    ) = model.preview(media, url, file, growing)

    internal fun appShareIntent() =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message))
        }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the original launcher intent for lifecycle observers.
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        val incoming =
            when (intent?.action) {
                Intent.ACTION_VIEW -> intent.dataString
                Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                else -> null
            } ?: return
        model.open(incoming)
    }

    private fun launchLinkSettings(targetPackage: String) {
        val data = Uri.parse("package:$targetPackage")
        val action =
            if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS
            else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        try {
            startActivity(Intent(action, data))
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, data))
            } catch (_: ActivityNotFoundException) {
                Snackbar.make(
                        root,
                        getString(R.string.link_settings_fallback),
                        Snackbar.LENGTH_LONG,
                    )
                    .show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.resumeInstall(packageManager.canRequestPackageInstalls())
        model.refresh()
        if (model.state.value.screen in setOf(Screen.HOME, Screen.SETTINGS))
            model.checkUpdates(false)
    }

    override fun onStop() {
        current?.onStop()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        current?.onStart()
    }

    override fun onDestroy() {
        updates.close()
        current?.close()
        super.onDestroy()
    }
}
