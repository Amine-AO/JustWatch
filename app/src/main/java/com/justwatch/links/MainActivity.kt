package com.justwatch.links

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.justwatch.links.data.MediaState
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.UpdateState
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel
import java.io.File
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val model: WatchViewModel by viewModels { WatchViewModel.Factory(application) }
    private var rendered: WatchState? = null
    private var updateProgress: com.google.android.material.progressindicator.LinearProgressIndicator? = null
    private var updateLabel: TextView? = null
    private var diagnosticResult: TextView? = null
    private var diagnosticButton: MaterialButton? = null
    private lateinit var root: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var headerLogo: ImageView
    private lateinit var backButton: MaterialButton
    private lateinit var settingsButton: MaterialButton
    private var homeHero: LinearLayout? = null
    private var imeVisible = false
    private var fullscreen = false
    private var homeLinkNotice: TextView? = null
    private var homeShare: MaterialButton? = null
    private lateinit var content: FrameLayout
    private var screen = Screen.HOME
    private var player: WebView? = null
    private var nativePlayer: ExoPlayer? = null
    private val sourceUrl: String get() = model.state.value.url
    private var linkEntry: TextInputEditText? = null
    private var linkLayout: TextInputLayout? = null
    private var draftLink = ""
    private var loadingIndicator: CircularProgressIndicator? = null
    private var loadingDetail: TextView? = null
    private var updateDialog: androidx.appcompat.app.AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = model.state.value.theme
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        root = column().apply {
            setBackgroundColor(color(R.color.jw_surface))
            isFocusableInTouchMode = true
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        backButton = headerButton("Back", R.drawable.ic_back) {
            if (screen == Screen.DIAGNOSTICS || screen == Screen.LINKS) showSettings() else showHome()
        }
        header.addView(backButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        headerLogo = logo(36)
        header.addView(headerLogo, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginStart = dp(4); marginEnd = dp(12) })
        headerTitle = text("JustWatch", 21f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, -2, 1f))
        settingsButton = headerButton("Settings", R.drawable.ic_settings) { showSettings() }.apply { id = R.id.settings_button }
        header.addView(settingsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(-1, dp(64)))
        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        content.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateHomeHero() }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            updateHomeHero()
            WindowInsetsCompat.CONSUMED
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (fullscreen) { setFullscreen(false); return }
                when (screen) {
                    Screen.HOME -> finish()
                    Screen.DIAGNOSTICS, Screen.LINKS -> showSettings()
                    else -> showHome()
                }
            }
        })
        draftLink = model.state.value.draft.ifEmpty { savedInstanceState?.getString("draft").orEmpty() }
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                model.state.collect(::render)
            }
        }
        if (savedInstanceState == null) {
            acceptIntent(intent)
            if (model.state.value.screen == Screen.HOME && !model.state.value.wizardSeen && Build.VERSION.SDK_INT >= 31) showLinkWizard()
        } else if (model.state.value.url.isEmpty()) {
            when (savedInstanceState.getString("screen")) {
                Screen.SETTINGS.name -> showSettings()
                Screen.LINKS.name -> if (model.state.value.wizardActive) showLinkWizard() else showLinkTroubleshooting()
                Screen.DIAGNOSTICS.name -> showDiagnostics()
                else -> savedInstanceState.getString("source")?.let(::openLink)
            }
        }
    }

    private fun render(state: WatchState) {
        val previous = rendered
        rendered = state
        if (previous?.theme != state.theme) delegate.localNightMode = state.theme
        val pageChanged = previous?.screen != state.screen
        val linksChanged = previous?.links != state.links || previous?.comparison != state.comparison
        if (previous?.url != state.url || (state.screen == Screen.SETTINGS && (previous?.engineVersion != state.engineVersion || previous?.updatingEngine != state.updatingEngine)) || pageChanged || (state.screen in setOf(Screen.SETTINGS, Screen.LINKS) && linksChanged) ||
            (state.screen == Screen.PLAYER && previous?.media != state.media) ||
            (state.screen == Screen.LINKS && previous?.wizardActive != state.wizardActive)) {
            when (state.screen) {
                Screen.HOME -> renderHome(state.inputError)
                Screen.SETTINGS -> renderSettings()
                Screen.LINKS -> if (state.wizardActive) renderLinkWizard() else renderLinkTroubleshooting()
                Screen.DIAGNOSTICS -> renderDiagnostics()
                Screen.LOADING -> renderLoading(LinkPolicy.platform(state.url))
                Screen.PLAYER -> (state.media as? MediaState.Ready)?.let { renderPlayer(it.media, state.url, it.file, it.growing) }
                Screen.ERROR -> renderFailure(state.url, (state.media as? MediaState.Failed)?.details.orEmpty())
                Screen.WEB -> renderViewer(state.url)
            }
        }
        if (state.screen == Screen.HOME && linksChanged) refreshLinkNotice()
        (state.media as? MediaState.Loading)?.let {
            if (it.progress == 0) {
                loadingIndicator?.isIndeterminate = true
                loadingDetail?.visibility = View.GONE
            } else {
                loadingIndicator?.setProgressCompat(it.progress, true)
                loadingDetail?.visibility = View.VISIBLE
                loadingDetail?.text = "${it.progress}%"
            }
        }
        linkLayout?.error = state.inputError
        diagnosticResult?.text = state.diagnostics
        diagnosticButton?.isEnabled = !state.testing
        if (state.updates != previous?.updates) renderUpdate(state.updates)
        state.engineMessage?.let { message ->
            model.engineMessageShown()
            MaterialAlertDialogBuilder(this).setTitle("Extraction engine").setMessage(message).setPositiveButton("OK", null).show()
        }
        state.settingsPackage?.let { target -> model.settingsOpened(); launchLinkSettings(target) }
        state.copiedReport?.let { report ->
            model.reportCopied()
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("JustWatch link diagnostics", report))
            Snackbar.make(root, "Link diagnostics copied", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showHome() = model.navigate(Screen.HOME)
    private fun showSettings() = model.navigate(Screen.SETTINGS)
    private fun showDiagnostics() = model.navigate(Screen.DIAGNOSTICS)
    internal fun showLinkWizard() = model.navigate(Screen.LINKS)
    internal fun showLinkTroubleshooting() = model.troubleshooting()
    private fun showViewer(url: String) = model.navigate(Screen.WEB)
    internal fun showLoading(platform: String) = model.loading(platform)
    internal fun showNativeVideo(media: ExtractedMedia, url: String, file: File? = null, growing: GrowingDownload? = null) =
        model.preview(media, url, file, growing)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep the original launcher intent for lifecycle observers.
        acceptIntent(intent)
    }

    private fun acceptIntent(intent: Intent?) {
        val incoming = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        } ?: return
        openLink(LinkPolicy.sharedUrl(incoming))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun space(parent: LinearLayout, height: Int) { parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun text(value: String, size: Float = 16f, muted: Boolean = false, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color(if (muted) R.color.jw_on_surface_variant else R.color.jw_on_surface))
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun logo(size: Int) = ImageView(this).apply {
        setImageResource(R.drawable.ic_logo)
        contentDescription = null
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }
    private fun action(label: String, icon: Int? = null, outlined: Boolean = false, click: () -> Unit) =
        MaterialButton(this, null, if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label
            isAllCaps = false
            cornerRadius = dp(28)
            minHeight = dp(56)
            insetTop = dp(2)
            insetBottom = dp(2)
            if (icon != null) { setIconResource(icon); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START }
            setOnClickListener { click() }
        }
    private fun headerButton(description: String, icon: Int, click: () -> Unit) = action("", icon, click = click).apply {
        contentDescription = description
        minWidth = 0
        minimumWidth = 0
        minHeight = dp(48)
        iconSize = dp(24)
        iconPadding = 0
        insetTop = 0; insetBottom = 0
        setPadding(dp(12), dp(12), dp(12), dp(12))
        backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        iconTint = ColorStateList.valueOf(color(R.color.jw_on_surface))
    }
    private fun fixedPage(center: Boolean = false): LinearLayout = column().apply {
        setPadding(dp(24), dp(16), dp(24), dp(24))
        if (center) gravity = Gravity.CENTER_VERTICAL
        content.addView(this, FrameLayout.LayoutParams(-1, -1))
    }
    private fun fullButton(parent: LinearLayout, button: View) {
        parent.addView(button, LinearLayout.LayoutParams(-1, dp(60)))
    }
    private fun card(parent: LinearLayout, tonal: Boolean = false): LinearLayout {
        val box = MaterialCardView(this).apply {
            radius = dp(24).toFloat()
            cardElevation = 0f
            strokeWidth = if (tonal) 0 else dp(1)
            setStrokeColor(color(R.color.jw_surface_variant))
            setCardBackgroundColor(color(if (tonal) R.color.jw_surface_variant else R.color.jw_surface))
        }
        val inside = column().apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }
        box.addView(inside)
        parent.addView(box, LinearLayout.LayoutParams(-1, -2))
        return inside
    }
    private fun scrollPage(): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val body = column().apply { setPadding(dp(24), dp(8), dp(24), dp(24)) }
        scroll.addView(body)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        return body
    }
    private fun releasePlayers() {
        if (fullscreen) setFullscreen(false)
        player?.destroy(); player = null
        val old = nativePlayer; nativePlayer = null; old?.release()
    }
    private fun beginPage(next: Screen, title: String, back: Boolean = false) {
        draftLink = linkEntry?.text?.toString() ?: draftLink
        model.draft(draftLink)
        linkEntry = null
        linkLayout = null
        releasePlayers()
        screen = next
        loadingIndicator = null
        loadingDetail = null
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
        root.requestFocus()
        content.removeAllViews()
        headerTitle.text = title
        homeHero = null
        homeShare = null
        homeLinkNotice = null
        if (next != Screen.DIAGNOSTICS) { diagnosticResult = null; diagnosticButton = null }
        val mediaScreen = next in listOf(Screen.LOADING, Screen.PLAYER, Screen.ERROR, Screen.WEB)
        backButton.visibility = if (back && !mediaScreen) View.VISIBLE else View.GONE
        headerLogo.visibility = if (next == Screen.HOME || mediaScreen) View.VISIBLE else View.GONE
        settingsButton.visibility = if (next == Screen.HOME) View.VISIBLE else View.GONE
    }

    private fun updateHomeHero() {
        homeShare?.visibility = if (imeVisible) View.GONE else View.VISIBLE
        homeHero?.visibility = if (imeVisible || (content.height > 0 && content.height < dp(380))) View.GONE else View.VISIBLE
    }

    private fun renderHome(message: String? = null) {
        beginPage(Screen.HOME, "JustWatch")
        val body = fixedPage().apply { id = R.id.home_page }
        val hero = column().apply { gravity = Gravity.BOTTOM }
        homeHero = hero
        hero.addView(text("Enjoy the video. Keep your time.", 30f, bold = true))
        space(hero, 10)
        hero.addView(text("Open the videos shared with you without the endless scrolling.", 17f, muted = true))
        space(hero, 28)
        body.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))
        val inputCard = card(body)
        val inputLayout = TextInputLayout(this).apply {
            hint = "Paste your link here..."
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat(), dp(16).toFloat())
        }
        val entry = TextInputEditText(inputLayout.context).apply {
            id = R.id.link_input
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setText(draftLink)
            textSize = 16f
            setPadding(dp(16), dp(16), dp(16), dp(16))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_GO
        }
        entry.doAfterTextChanged { model.draft(it?.toString().orEmpty()) }
        linkEntry = entry
        linkLayout = inputLayout
        inputLayout.addView(entry, LinearLayout.LayoutParams(-1, -2))
        inputCard.addView(inputLayout)
        if (message != null) inputLayout.error = message
        space(inputCard, 16)
        val actions = LinearLayout(this)
        val paste = action("Paste", R.drawable.ic_paste, outlined = true) {
            val clipboard = getSystemService(ClipboardManager::class.java)
            val pasted = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            if (pasted.isNullOrBlank()) Snackbar.make(root, "Your clipboard is empty", Snackbar.LENGTH_SHORT).show()
            else { entry.setText(LinkPolicy.sharedUrl(pasted)); inputLayout.error = null }
        }
        val watch = {
            val value = LinkPolicy.sharedUrl(entry.text.toString())
            openLink(value)
        }
        actions.addView(paste, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        actions.addView(action("Watch", R.drawable.ic_play) { watch() }, LinearLayout.LayoutParams(0, dp(56), 1f))
        inputCard.addView(actions)
        entry.setOnEditorActionListener { _, action, _ ->
            if (action == android.view.inputmethod.EditorInfo.IME_ACTION_GO) { watch(); true } else false
        }
        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        homeLinkNotice = text("", 14f, muted = true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.jw_warning))
            val alert = androidx.core.content.ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_alert)?.mutate()
            alert?.setTint(color(R.color.jw_warning))
            alert?.setBounds(0, 0, dp(24), dp(24))
            setCompoundDrawablesRelative(alert, null, null, null)
            compoundDrawablePadding = dp(10)
            setPadding(dp(8), dp(12), dp(8), dp(12))
            isClickable = true; isFocusable = true
            setOnClickListener { showLinkWizard() }
        }
        body.addView(homeLinkNotice, LinearLayout.LayoutParams(-1, -2))
        homeShare = action("Enjoying JustWatch? Share it with a friend.", R.drawable.ic_share, outlined = true) {
            startActivity(Intent.createChooser(appShareIntent(), "Share JustWatch"))
        }.apply {
            id = R.id.share_app_button
            textSize = 13f
            maxLines = 2
            strokeWidth = 0
            iconSize = dp(20)
            setPadding(dp(8), 0, dp(8), 0)
        }
        body.addView(homeShare, LinearLayout.LayoutParams(-1, dp(56)))
        refreshLinkNotice()
        root.requestFocus()
        updateHomeHero()
        model.checkUpdates(false)
    }

    internal fun appShareIntent() = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Watch the videos friends send you without getting lost in the feed. JustWatch opens shared Instagram, TikTok and Facebook videos, without needing their apps.\nDownload: https://github.com/Amine-AO/JustWatch")
    }

    private fun settingRow(parent: LinearLayout, title: String, description: String, icon: Int, click: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(16), dp(8), dp(16))
            minimumHeight = dp(80)
            isClickable = true; isFocusable = true
            val out = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
            setBackgroundResource(out.resourceId)
            setOnClickListener { click() }
        }
        row.addView(ImageView(this).apply { setImageResource(icon); imageTintList = ColorStateList.valueOf(color(R.color.jw_primary)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(24), dp(24)))
        val labels = column().apply { setPadding(dp(18), 0, dp(8), 0) }
        labels.addView(text(title, 16f, bold = true))
        labels.addView(text(description, 13f, muted = true))
        row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(ImageView(this).apply { setImageResource(R.drawable.ic_arrow); imageTintList = ColorStateList.valueOf(color(R.color.jw_on_surface_variant)); contentDescription = null }, LinearLayout.LayoutParams(dp(20), dp(20)))
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
    }
    private fun section(parent: LinearLayout, title: String) {
        space(parent, 24)
        parent.addView(text(title, 13f, bold = true).apply { setTextColor(color(R.color.jw_primary)) })
        space(parent, 8)
    }
    private fun themeName() = when (model.state.value.theme) {
        AppCompatDelegate.MODE_NIGHT_YES -> "Dark"
        AppCompatDelegate.MODE_NIGHT_NO -> "Light"
        else -> "Use device theme"
    }
    private fun renderSettings() {
        beginPage(Screen.SETTINGS, "Settings", back = true)
        val body = scrollPage()
        section(body, "OPEN SHARED LINKS")
        val links = card(body, tonal = true)
        links.addView(text("Make the next tap simpler", 20f, bold = true))
        space(links, 8)
        links.addView(text("Choose JustWatch for social links in Android settings. Your other links can stay in your browser.", 14f, muted = true))
        space(links, 12)
        links.addView(text(linkStatus(), 13f, bold = true))
        space(links, 16)
        fullButton(links, action("Enable link opening", R.drawable.ic_link) { explainLinkSetup() })
        settingRow(body, "Link troubleshooting", "If selections turn off or links open elsewhere", R.drawable.ic_info) { showLinkTroubleshooting() }
        settingRow(body, "Share to JustWatch", "Works without changing your default browser", R.drawable.ic_share) {
            MaterialAlertDialogBuilder(this).setTitle("Share one video")
                .setMessage("In WhatsApp or your browser, use Share on the link, then choose JustWatch. You can also copy the link and use Paste link on the Watch page.")
                .setPositiveButton("Got it", null).show()
        }
        section(body, "APPEARANCE")
        settingRow(body, "Theme", themeName(), R.drawable.ic_theme) { chooseTheme() }
        section(body, "APP UPDATES")
        settingRow(body, "Check for app updates", "JustWatch ${BuildConfig.VERSION_NAME} · Checked daily when you open the app", R.drawable.ic_network) { checkForAppUpdates(true) }
        section(body, "APP MAINTENANCE")
        settingRow(body, "Check for engine updates", if (model.state.value.updatingEngine) "Checking for updates…" else "Version ${model.state.value.engineVersion} · Stable releases", R.drawable.ic_network) {
            model.updateEngine()
        }
        settingRow(body, "Connection test", "Check independent DNS and HTTPS", R.drawable.ic_network) { showDiagnostics() }
        section(body, "ABOUT")
        body.addView(text("JustWatch ${BuildConfig.VERSION_NAME}", 16f, bold = true))
        space(body, 6)
        body.addView(text("Enjoy the video. Keep your time.", 14f, muted = true))
        space(body, 16)
        settingRow(body, "Created by Amine-AO", "github.com/Amine-AO", R.drawable.ic_link) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Amine-AO"))) }
            catch (_: ActivityNotFoundException) { Snackbar.make(root, "No browser is available", Snackbar.LENGTH_SHORT).show() }
        }
    }
    private fun checkForAppUpdates(manual: Boolean) = model.checkUpdates(manual)

    private fun showAppUpdateMessage(message: String) {
        MaterialAlertDialogBuilder(this).setTitle("App updates").setMessage(message).setPositiveButton("OK", null).show()
    }
    private fun renderUpdate(state: UpdateState) {
        if (state !is UpdateState.Downloading) {
            updateDialog?.dismiss()
            updateDialog = null
            updateProgress = null
            updateLabel = null
        }
        when (state) {
            is UpdateState.Message -> { model.dismissUpdate(); showAppUpdateMessage(state.text) }
            is UpdateState.Offer -> MaterialAlertDialogBuilder(this)
                .setTitle("JustWatch ${state.info.versionName} is available")
                .setMessage("Your version: ${BuildConfig.VERSION_NAME}\n\n" + state.info.notes.ifBlank { "A newer version of JustWatch is ready." })
                .setPositiveButton("Download update") { _, _ -> model.download(state.info) }
                .setNegativeButton("Later") { _, _ -> model.dismissUpdate() }
                .setOnCancelListener { model.dismissUpdate() }.show()
            is UpdateState.Downloading -> {
                if (updateDialog == null) {
                    val panel = column().apply { setPadding(dp(24), dp(12), dp(24), dp(12)) }
                    updateLabel = text("", 15f).also { panel.addView(it) }
                    space(panel, 16)
                    updateProgress = com.google.android.material.progressindicator.LinearProgressIndicator(this).apply { max = 100 }
                    panel.addView(updateProgress, LinearLayout.LayoutParams(-1, dp(8)))
                    updateDialog = MaterialAlertDialogBuilder(this).setTitle("Updating JustWatch")
                        .setView(panel).setCancelable(false).setNegativeButton("Cancel") { _, _ -> model.cancelUpdate() }.show()
                }
                updateLabel?.text = if (state.percent == 100) "Verifying update…" else "Downloading… ${state.percent}%"
                updateProgress?.setProgressCompat(state.percent, true)
            }
            is UpdateState.Install -> {
                model.dismissUpdate()
                if (!packageManager.canRequestPackageInstalls()) {
                    MaterialAlertDialogBuilder(this).setTitle("Allow JustWatch updates")
                        .setMessage("Android needs your permission for JustWatch to open downloaded app updates. Enable Allow from this source, then return here.")
                        .setPositiveButton("Open settings") { _, _ ->
                            model.awaitInstallPermission(true)
                            try { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                            catch (_: ActivityNotFoundException) {
                                model.awaitInstallPermission(false)
                                showAppUpdateMessage("Open Android Settings and allow JustWatch to install unknown apps, then check for the update again.")
                            }
                        }.setNegativeButton("Later", null).show()
                } else {
                    try { startActivity(AppUpdates.installIntent(this, state.file)) }
                    catch (_: ActivityNotFoundException) { showAppUpdateMessage("No Android package installer is available.") }
                }
            }
            else -> Unit
        }
    }

    private fun chooseTheme() {
        val modes = intArrayOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)
        val selected = modes.indexOf(model.state.value.theme).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this).setTitle("Theme")
            .setSingleChoiceItems(arrayOf("Use device theme", "Light", "Dark"), selected) { dialog, choice ->
                model.theme(modes[choice])
                dialog.dismiss()
                delegate.localNightMode = modes[choice]
            }.setNegativeButton("Cancel", null).show()
    }
    private fun linkStatus(): String = if (Build.VERSION.SDK_INT < 31) "Manage supported links in Android settings"
        else model.state.value.links?.summary() ?: "Checking supported links…"

    private fun renderLinkTroubleshooting() {
        beginPage(Screen.LINKS, "Link troubleshooting", back = true)
        val body = scrollPage()
        space(body, 12)
        body.addView(text("Keep shared links in JustWatch", 26f, bold = true))
        space(body, 12)
        body.addView(text("Enable the addresses in Android settings, then return here. JustWatch saves the state before and after your visit so we can investigate selections that turn off.", 15f, muted = true))
        space(body, 20)
        val report = model.state.value.links ?: return
        val status = card(body, tonal = true)
        status.addView(text(report.summary(), 15f, bold = true))
        space(status, 16)
        fullButton(status, action("Manage JustWatch links", R.drawable.ic_link) { openLinkSettings() })
        section(body, "OTHER ENABLED LINK HANDLERS")
        if (report.handlers.isEmpty()) {
            body.addView(text("No competing enabled handler was found among the apps Android lets JustWatch inspect. This does not prove there is no system conflict. Copy the report after returning from Android settings.", 14f, muted = true))
        }
        for (handler in report.handlers) {
            settingRow(body, "Manage ${handler.label}", handler.hosts.joinToString(), R.drawable.ic_link) {
                MaterialAlertDialogBuilder(this).setTitle("Disable link opening for ${handler.label}")
                    .setMessage("In this app’s Android settings, turn off Open supported links. Then return to Manage JustWatch links and select the addresses again.")
                    .setPositiveButton("Open app settings") { _, _ -> openLinkSettings(handler.packageName) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
        val comparison = model.state.value.comparison
        if (comparison != null) {
            section(body, "LAST SETTINGS VISIT")
            body.addView(text(model.state.value.comparison.orEmpty(), 14f, muted = true))
        }
        section(body, "IF IT STILL OPENS IN YOUR BROWSER")
        body.addView(text("Some apps send links directly to a browser. Share the link and choose JustWatch, or paste it on the home page. If your selections keep resetting, copy the diagnostics below so we can inspect the actual Android state.", 14f, muted = true))
        space(body, 16)
        fullButton(body, action("Copy link diagnostics", R.drawable.ic_paste, outlined = true) {
            model.copyReport()
        })
    }
    private fun explainLinkSetup() {
        MaterialAlertDialogBuilder(this).setTitle("Let JustWatch open shared links")
            .setMessage("On the next screen, enable Open supported links. Then use Add links or Supported web addresses to select the social addresses you want JustWatch to open.\n\nIf Facebook or Instagram already handles them, disable Open supported links for those apps first, including any Lite variants. Otherwise Android can reset your selection. Android requires you to make this choice.")
            .setPositiveButton("Open Android settings") { _, _ -> showLinkWizard() }
            .setNegativeButton("Not now", null).show()
    }
    private fun refreshLinkNotice() {
        if (Build.VERSION.SDK_INT < 31) { homeLinkNotice?.visibility = View.GONE; return }
        val report = model.state.value.links ?: return
        val missing = LinkAssociations.groups.values.flatten().filterNot { report.enabled(it) }
        homeLinkNotice?.apply {
            visibility = if (missing.isNotEmpty() || !report.allowed) View.VISIBLE else View.GONE
            text = if (model.state.value.wizardSeen)
                "Some links won’t open in JustWatch. Tap to fix." else "Set up links to open shared videos in JustWatch. Tap to start."
        }
    }

    private fun renderLinkWizard() {
        beginPage(Screen.LINKS, "Set up links", back = true)
        val body = scrollPage()
        val report = model.state.value.links ?: return
        val conflict = report.handlers.firstOrNull()
        body.addView(text(if (conflict != null) "1. Free up your links" else "2. Open links in JustWatch", 26f, bold = true))
        space(body, 16)
        if (conflict != null) {
            body.addView(text("${conflict.label} currently handles some links that JustWatch can open. Turn off its Open supported links setting, then return here. This changes link opening only.", 16f))
            space(body, 12)
            body.addView(text(conflict.hosts.joinToString("\n"), 14f, muted = true))
            space(body, 20)
            fullButton(body, action("Manage ${conflict.label}", R.drawable.ic_link) { openLinkSettings(conflict.packageName) })
            space(body, 12)
            body.addView(text("We’ll check again when you return and guide you through any remaining apps.", 14f, muted = true))
        } else {
            body.addView(text("Enable Open supported links for JustWatch, then select the Instagram, Facebook and TikTok addresses you want it to open. Return here when you’re done.", 16f))
            space(body, 20)
            body.addView(text(report.summary(), 15f, bold = true))
            space(body, 20)
            fullButton(body, action("Manage JustWatch links", R.drawable.ic_link) { openLinkSettings() })
        }
        space(body, 16)
        fullButton(body, action("Done for now", R.drawable.ic_check, outlined = true) { showHome() })
        space(body, 12)
        fullButton(body, action("Link diagnostics", R.drawable.ic_info, outlined = true) { showLinkTroubleshooting() })
    }

    private fun openLinkSettings(targetPackage: String = packageName) = model.linkSettings(targetPackage)

    private fun launchLinkSettings(targetPackage: String) {
        val data = Uri.parse("package:$targetPackage")
        val action = if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        try { startActivity(Intent(action, data)) }
        catch (_: ActivityNotFoundException) {
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, data)) }
            catch (_: ActivityNotFoundException) { Snackbar.make(root, "Open Android Settings → Apps → JustWatch → Set as default", Snackbar.LENGTH_LONG).show() }
        }
    }

    private fun renderDiagnostics() {
        beginPage(Screen.DIAGNOSTICS, "Connection test", back = true)
        val body = scrollPage()
        space(body, 16)
        body.addView(text("Check your connection", 26f, bold = true))
        space(body, 12)
        body.addView(text("Tests independent DNS and HTTPS access. This does not change your Android or router settings.", 15f, muted = true))
        space(body, 24)
        diagnosticResult = text(model.state.value.diagnostics, 14f, muted = true).apply { setTextIsSelectable(true) }
        card(body, tonal = true).addView(diagnosticResult)
        space(body, 20)
        diagnosticButton = action("Run connection test", R.drawable.ic_network) { model.diagnose() }.apply {
            isEnabled = !model.state.value.testing
        }
        fullButton(body, diagnosticButton!!)
    }

    private fun openLink(raw: String) = model.open(raw)

    private fun renderLoading(platform: String) {
        beginPage(Screen.LOADING, "JustWatch | $platform")
        val body = fixedPage(center = true).apply { gravity = Gravity.CENTER }
        loadingIndicator = CircularProgressIndicator(this).apply { id = R.id.loading_progress; isIndeterminate = true; indicatorSize = dp(56) }
        body.addView(loadingIndicator, LinearLayout.LayoutParams(dp(64), dp(64)))
        space(body, 24)
        body.addView(text("Loading your video...", 26f, bold = true).apply { id = R.id.loading_title; gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
        loadingDetail = text("", 15f, muted = true).apply { gravity = Gravity.CENTER; visibility = View.GONE }
        body.addView(loadingDetail, LinearLayout.LayoutParams(-1, -2))
        space(body, 28)
        body.addView(action("Cancel", R.drawable.ic_close, outlined = true) { showHome() }, LinearLayout.LayoutParams(dp(160), dp(56)))
    }
    private fun renderFailure(url: String, status: String) {
        beginPage(Screen.ERROR, "JustWatch | ${LinkPolicy.platform(url)}")
        val body = scrollPage().apply { gravity = Gravity.CENTER_VERTICAL }
        body.addView(logo(64))
        space(body, 24)
        body.addView(text("Couldn’t open this video", 28f, bold = true))
        space(body, 12)
        val message = if (status.contains("login", true) || status.contains("rate-limit", true))
            "The platform could not provide this video. It may need a login, be unavailable, or be rate-limited."
        else "This link couldn’t be prepared for playback. You can try again or try the restricted webpage."
        body.addView(text(message, 16f, muted = true))
        space(body, 24)
        fullButton(body, action("Try again", R.drawable.ic_play) { openLink(url) })
        fullButton(body, action("Try restricted webpage", R.drawable.ic_link, outlined = true) { showViewer(url) })
        space(body, 12)
        body.addView(text("Webpage playback may be blocked by your DNS settings.", 13f, muted = true))
        val details = text(status, 12f, muted = true).apply { visibility = View.GONE; setTextIsSelectable(true); typeface = Typeface.MONOSPACE }
        fullButton(body, action("Technical details", outlined = true) { details.visibility = if (details.visibility == View.GONE) View.VISIBLE else View.GONE })
        body.addView(details)
        fullButton(body, action("Close", R.drawable.ic_close, outlined = true) { showHome() })
    }
    private fun playbackError(error: PlaybackException): String {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return "The media server returned HTTP ${cause.responseCode}."
            cause = cause.cause
        }
        return "Playback failed: " + error.errorCodeName
    }
    private fun renderPlayer(media: ExtractedMedia, url: String, file: File? = null, growing: GrowingDownload? = null) {
        beginPage(Screen.PLAYER, "JustWatch | ${LinkPolicy.platform(url)}")
        val body = column().apply { setPadding(dp(16), dp(8), dp(16), dp(12)) }
        content.addView(body, FrameLayout.LayoutParams(-1, -1))
        val stage = FrameLayout(this).apply { setBackgroundResource(R.drawable.player_stage); clipToOutline = true }
        body.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f))
        val dataSource = OkHttpDataSource.Factory(PublicMediaResolver.http).setDefaultRequestProperties(media.headers)
        val sourceFactory = if (growing != null) DefaultMediaSourceFactory(androidx.media3.datasource.DataSource.Factory { GrowingFileDataSource(growing) })
            else DefaultMediaSourceFactory(DefaultDataSource.Factory(this, dataSource))
        val exo = ExoPlayer.Builder(this).setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000).setMediaSourceFactory(sourceFactory).build()
        nativePlayer = exo
        val playerView = layoutInflater.inflate(R.layout.justwatch_player, stage, false) as PlayerView
        playerView.player = exo
        playerView.setShowRewindButton(true)
        playerView.setShowFastForwardButton(true)
        playerView.findViewById<View>(R.id.player_fullscreen).setOnClickListener { setFullscreen(!fullscreen) }
        stage.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        val buffering = CircularProgressIndicator(this).apply { isIndeterminate = true }
        stage.addView(buffering, FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER))
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering.visibility = if (playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) View.VISIBLE else View.GONE
            }
            override fun onPlayerError(error: PlaybackException) {
                if (nativePlayer === exo && !isFinishing && !isDestroyed) {
                    val message = playbackError(error)
                    model.playbackFailed(message)
                }
            }
        })
        exo.setMediaItem(MediaItem.fromUri(media.url))
        exo.prepare()
        exo.playWhenReady = false
        space(body, 12)
        body.addView(text("Ready when you are. Press Play.", 14f, muted = true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2))
        fullButton(body, action("Close video", R.drawable.ic_close, outlined = true) { showHome() })
    }
    private fun setFullscreen(enabled: Boolean) {
        fullscreen = enabled
        val body = (content.getChildAt(0) as? LinearLayout)
        if (screen == Screen.PLAYER && body != null) {
            for (index in 1 until body.childCount) body.getChildAt(index).visibility = if (enabled) View.GONE else View.VISIBLE
            body.setPadding(if (enabled) 0 else dp(16), if (enabled) 0 else dp(8), if (enabled) 0 else dp(16), if (enabled) 0 else dp(12))
        }
        root.getChildAt(0).visibility = if (enabled) View.GONE else View.VISIBLE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (enabled) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
        }
        content.findViewById<View>(R.id.player_fullscreen)?.contentDescription = if (enabled) "Exit fullscreen" else "Fullscreen"
    }

    private fun renderViewer(url: String) {
        beginPage(Screen.WEB, "JustWatch | ${LinkPolicy.platform(url)}")
        val body = column()
        content.addView(body, FrameLayout.LayoutParams(-1, -1))
        body.addView(text("The webpage may include login prompts or recommendations.", 13f, muted = true).apply { setPadding(dp(16), dp(8), dp(16), dp(12)) })
        val web = WebView(this)
        player = web
        body.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true; setSupportMultipleWindows(false)
            allowFileAccess = false; allowContentAccess = false
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!request.isForMainFrame) return false
                val next = request.url
                if (next.toString() == url) return false
                if (LinkPolicy.validated(next.toString()) == null) return true
                val path = next.path.orEmpty()
                return when (LinkPolicy.platform(next.toString())) {
                    "TikTok" -> !path.contains("/video/") && !path.startsWith("/player/v1/")
                    "Instagram" -> listOf("/reel/", "/p/", "/tv/").none { path.startsWith(it) }
                    else -> listOf("/reel/", "/watch/", "/share/", "/videos/").none { path.startsWith(it) }
                }
            }
        }
        fullButton(body, action("Close video", R.drawable.ic_close, outlined = true) { showHome() })
        web.loadUrl(url)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", linkEntry?.text?.toString() ?: draftLink)
        outState.putString("screen", screen.name)
        if (screen == Screen.LOADING || screen == Screen.PLAYER) outState.putString("source", sourceUrl)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            model.resumeInstall(packageManager.canRequestPackageInstalls())
            model.refresh()
            if (model.state.value.screen in setOf(Screen.HOME, Screen.SETTINGS)) model.checkUpdates(false)
        }
    }
    override fun onStop() { nativePlayer?.pause(); player?.onPause(); super.onStop() }
    override fun onStart() { super.onStart(); player?.onResume() }
    override fun onDestroy() { updateDialog?.dismiss(); releasePlayers(); super.onDestroy() }
}


