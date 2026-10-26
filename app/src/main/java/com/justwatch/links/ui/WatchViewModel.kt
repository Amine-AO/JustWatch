package com.justwatch.links.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.justwatch.links.AppUpdateInfo
import com.justwatch.links.BuildConfig
import com.justwatch.links.ExtractedMedia
import com.justwatch.links.GrowingDownload
import com.justwatch.links.LinkAssociations
import com.justwatch.links.LinkPolicy
import com.justwatch.links.data.MediaState
import com.justwatch.links.data.SettingsRepository
import com.justwatch.links.data.UpdateRepository
import com.justwatch.links.data.WatchRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class Screen { HOME, SETTINGS, LINKS, DIAGNOSTICS, LOADING, PLAYER, ERROR, WEB }

internal sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Offer(val info: AppUpdateInfo) : UpdateState
    data class Downloading(val percent: Int) : UpdateState
    data class Message(val text: String) : UpdateState
    data class Install(val file: File) : UpdateState
}

internal data class WatchState(
    val screen: Screen = Screen.HOME,
    val url: String = "",
    val media: MediaState = MediaState.Idle,
    val draft: String = "",
    val inputError: String? = null,
    val theme: Int,
    val wizardSeen: Boolean,
    val wizardActive: Boolean,
    val links: LinkAssociations.Report? = null,
    val comparison: String? = null,
    val diagnostics: String = "Ready to test",
    val testing: Boolean = false,
    val engineVersion: String = "",
    val updatingEngine: Boolean = false,
    val engineMessage: String? = null,
    val updates: UpdateState = UpdateState.Idle,
    val settingsPackage: String? = null,
    val copiedReport: String? = null,
)

internal class WatchViewModel(
    private val videos: WatchRepository,
    private val updates: UpdateRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(WatchState(theme = settings.theme,
        wizardSeen = settings.wizardSeen, wizardActive = settings.wizardActive))
    val state = mutable.asStateFlow()
    private var playback: Job? = null
    private var updateJob: Job? = null
    private var diagnosticJob: Job? = null
    private var refreshJob: Job? = null

    init { refresh() }

    fun draft(text: String) { mutable.update { if (it.draft == text) it else it.copy(draft = text, inputError = null) } }
    fun theme(mode: Int) {
        settings.setTheme(mode)
        mutable.update { it.copy(theme = mode) }
    }
    fun navigate(screen: Screen) {
        if (screen !in setOf(Screen.LOADING, Screen.PLAYER, Screen.ERROR)) stopPlayback()
        if (screen != Screen.DIAGNOSTICS) diagnosticJob?.cancel()
        if (screen == Screen.LINKS) settings.wizard(true)
        mutable.update { it.copy(screen = screen, wizardActive = settings.wizardActive, wizardSeen = settings.wizardSeen) }
    }
    fun troubleshooting() {
        stopPlayback()
        settings.wizard(false)
        mutable.update { it.copy(screen = Screen.LINKS, wizardActive = false, wizardSeen = true) }
    }
    fun open(raw: String, local: Boolean = false, progressive: Boolean = true) {
        val url = LinkPolicy.validated(LinkPolicy.sharedUrl(raw))
        if (url == null) {
            stopPlayback()
            mutable.update { it.copy(screen = Screen.HOME, inputError = "Use an HTTPS link from Instagram, TikTok or Facebook.") }
            return
        }
        stopPlayback()
        mutable.update { it.copy(screen = Screen.LOADING, url = url, media = MediaState.Loading(), inputError = null) }
        playback = viewModelScope.launch {
            videos.prepare(url, local, progressive).collect { media ->
                mutable.update {
                    it.copy(media = media, screen = when (media) {
                        is MediaState.Ready -> Screen.PLAYER
                        is MediaState.Failed -> Screen.ERROR
                        else -> Screen.LOADING
                    })
                }
            }
        }
    }
    fun playbackFailed(details: String) {
        val current = state.value
        val ready = current.media as? MediaState.Ready ?: return
        if (ready.growing != null || (ready.file == null && LinkPolicy.platform(current.url) == "Instagram")) {
            open(current.url, local = true, progressive = false)
        } else {
            playback?.cancel()
            mutable.update { it.copy(screen = Screen.ERROR, media = MediaState.Failed(details)) }
            viewModelScope.launch { videos.discard(ready) }
        }
    }
    fun preview(media: ExtractedMedia, url: String, file: File?, growing: GrowingDownload?) {
        stopPlayback()
        mutable.update { it.copy(screen = Screen.PLAYER, url = url, media = MediaState.Ready(media, file, growing)) }
    }
    fun loading(platform: String) {
        stopPlayback()
        mutable.update { it.copy(screen = Screen.LOADING, url = "https://${platform.lowercase()}.com/", media = MediaState.Loading()) }
    }
    private fun stopPlayback() {
        playback?.cancel()
        val ready = state.value.media as? MediaState.Ready
        ready?.growing?.cancel()
        if (ready != null) viewModelScope.launch { videos.discard(ready) }
        mutable.update { it.copy(media = MediaState.Idle) }
    }
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val report = settings.afterLinkSettings()
            val version = withContext(Dispatchers.IO) { settings.engineVersion }
            mutable.update { it.copy(links = report, comparison = settings.comparison, engineVersion = version) }
        }
    }
    fun linkSettings(packageName: String) {
        viewModelScope.launch {
            settings.beforeLinkSettings()
            mutable.update { it.copy(settingsPackage = packageName) }
        }
    }
    fun settingsOpened() { mutable.update { it.copy(settingsPackage = null) } }
    fun copyReport() {
        viewModelScope.launch { val text = settings.diagnostics(); mutable.update { it.copy(copiedReport = text) } }
    }
    fun reportCopied() { mutable.update { it.copy(copiedReport = null) } }
    fun diagnose() {
        if (diagnosticJob?.isActive == true) return
        diagnosticJob = viewModelScope.launch {
            mutable.update { it.copy(testing = true, diagnostics = "Checking DNS and HTTPS…") }
            try {
                val result = settings.diagnose()
                mutable.update { it.copy(testing = false, diagnostics = result) }
            } finally { mutable.update { it.copy(testing = false) } }
        }
    }
    fun updateEngine() {
        if (state.value.updatingEngine) return
        mutable.update { it.copy(updatingEngine = true) }
        viewModelScope.launch {
            try {
                val result = settings.updateEngine()
                val version = withContext(Dispatchers.IO) { settings.engineVersion }
                mutable.update { it.copy(engineMessage = result, engineVersion = version) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutable.update { it.copy(engineMessage = "Update failed. Your installed extractor was kept. ${error.message.orEmpty().take(160)}") }
            } catch (_: LinkageError) {
                mutable.update { it.copy(engineMessage = "The extractor runtime could not load on this device.") }
            } finally { mutable.update { it.copy(updatingEngine = false) } }
        }
    }
    fun engineMessageShown() { mutable.update { it.copy(engineMessage = null) } }
    fun checkUpdates(manual: Boolean) {
        if (BuildConfig.DEBUG) {
            if (manual) mutable.update { it.copy(updates = UpdateState.Message("Install the signed release APK to receive app updates.")) }
            return
        }
        if (updateJob?.isActive == true || state.value.updates !is UpdateState.Idle || !settings.shouldCheckUpdate(manual)) return
        updateJob = viewModelScope.launch {
            mutable.update { it.copy(updates = UpdateState.Checking) }
            try {
                val info = updates.latest()
                mutable.update { it.copy(updates = when {
                    info != null && info.versionCode > BuildConfig.VERSION_CODE -> UpdateState.Offer(info)
                    !manual -> UpdateState.Idle
                    info == null -> UpdateState.Message("No app releases are available for updating yet.")
                    else -> UpdateState.Message("JustWatch ${BuildConfig.VERSION_NAME} is up to date.")
                }) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                mutable.update { it.copy(updates = if (manual) UpdateState.Message("Couldn’t check for updates. Please try again later.") else UpdateState.Idle) }
            }
        }
    }
    fun dismissUpdate() { mutable.update { it.copy(updates = UpdateState.Idle) } }
    fun download(info: AppUpdateInfo) {
        updateJob = viewModelScope.launch {
            mutable.update { it.copy(updates = UpdateState.Downloading(0)) }
            try {
                updates.download(info).collect { event ->
                    mutable.update { it.copy(updates = when (event) {
                        is UpdateRepository.Download.Progress -> UpdateState.Downloading(event.percent)
                        is UpdateRepository.Download.Complete -> UpdateState.Install(event.file)
                    }) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(updates = UpdateState.Message("The update couldn’t be downloaded or verified. ${error.message.orEmpty().take(180)}")) }
            }
        }
    }
    fun cancelUpdate() { updateJob?.cancel(); dismissUpdate() }
    fun awaitInstallPermission(value: Boolean) { settings.resumeInstall(value) }
    fun resumeInstall(allowed: Boolean) {
        if (!settings.consumeInstall()) return
        if (!allowed) {
            mutable.update { it.copy(updates = UpdateState.Message("Update installation wasn’t allowed. You can try again from App updates in Settings.")) }
            return
        }
        updateJob = viewModelScope.launch {
            try {
                val file = updates.verifiedDownload()
                mutable.update { it.copy(updates = UpdateState.Install(file)) }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(updates = UpdateState.Message("This downloaded update is no longer valid. Check for updates again.")) } }
        }
    }
    override fun onCleared() {
        (state.value.media as? MediaState.Ready)?.growing?.cancel()
        (state.value.media as? MediaState.Ready)?.let(videos::cleanup)
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(WatchViewModel::class.java))
            return WatchViewModel(WatchRepository(app), UpdateRepository(app), SettingsRepository(app)) as T
        }
    }
}
