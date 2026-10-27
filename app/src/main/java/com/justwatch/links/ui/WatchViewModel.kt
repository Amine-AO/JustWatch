package com.justwatch.links.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.justwatch.links.AppUpdateInfo
import com.justwatch.links.BuildConfig
import com.justwatch.links.ExtractedMedia
import com.justwatch.links.GrowingDownload
import com.justwatch.links.LinkPolicy
import com.justwatch.links.R
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

internal class WatchViewModel(
    private val videos: WatchRepository,
    private val updates: UpdateRepository,
    private val settings: SettingsRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val mutable =
        MutableStateFlow(
            WatchState(
                theme = settings.theme,
                wizardSeen = settings.wizardSeen,
                wizardActive = settings.wizardActive,
                draft = savedState["draft"] ?: "",
                screen =
                    savedState.get<String>("screen")?.let { value ->
                        Screen.entries.firstOrNull { it.name == value }
                    } ?: Screen.HOME,
                url = savedState["url"] ?: "",
            )
        )
    val state = mutable.asStateFlow()

    private fun update(transform: (WatchState) -> WatchState) {
        mutable.update(transform)
        val current = mutable.value
        savedState["screen"] = current.screen.name
        savedState["draft"] = current.draft
        savedState["url"] = current.url
    }

    private var playback: Job? = null
    private var updateJob: Job? = null
    private var diagnosticJob: Job? = null
    private var refreshJob: Job? = null

    init {
        val restored = state.value
        if (restored.screen in setOf(Screen.LOADING, Screen.PLAYER, Screen.ERROR, Screen.WEB)) {
            if (restored.url.isNotEmpty()) open(restored.url) else navigate(Screen.HOME)
        }
        refresh()
    }

    fun draft(text: String) {
        update { if (it.draft == text) it else it.copy(draft = text, inputError = null) }
    }

    fun paste(text: String) = draft(LinkPolicy.sharedUrl(text))

    fun theme(mode: Int) {
        settings.setTheme(mode)
        update { it.copy(theme = mode) }
    }

    fun navigate(screen: Screen) {
        if (screen !in setOf(Screen.LOADING, Screen.PLAYER, Screen.ERROR)) stopPlayback()
        if (screen != Screen.DIAGNOSTICS) diagnosticJob?.cancel()
        if (screen == Screen.LINKS) settings.wizard(true)
        update {
            it.copy(
                screen = screen,
                wizardActive = settings.wizardActive,
                wizardSeen = settings.wizardSeen,
            )
        }
    }

    fun troubleshooting() {
        stopPlayback()
        settings.wizard(false)
        update { it.copy(screen = Screen.LINKS, wizardActive = false, wizardSeen = true) }
    }

    fun open(raw: String, local: Boolean = false, progressive: Boolean = true) {
        val url = LinkPolicy.validated(LinkPolicy.sharedUrl(raw))
        if (url == null) {
            stopPlayback()
            update { it.copy(screen = Screen.HOME, inputError = UiText(R.string.invalid_link)) }
            return
        }
        stopPlayback()
        update {
            it.copy(
                screen = Screen.LOADING,
                url = url,
                media = MediaState.Loading(),
                inputError = null,
            )
        }
        playback =
            viewModelScope.launch {
                videos.prepare(url, local, progressive).collect { media ->
                    update {
                        it.copy(
                            media = media,
                            screen =
                                when (media) {
                                    is MediaState.Ready -> Screen.PLAYER
                                    is MediaState.Failed -> Screen.ERROR
                                    else -> Screen.LOADING
                                },
                        )
                    }
                }
            }
    }

    fun playbackFailed(details: String) {
        val current = state.value
        val ready = current.media as? MediaState.Ready ?: return
        if (
            ready.growing != null ||
                (ready.file == null && LinkPolicy.platform(current.url) == "Instagram")
        ) {
            open(current.url, local = true, progressive = false)
        } else {
            playback?.cancel()
            update { it.copy(screen = Screen.ERROR, media = MediaState.Failed(details)) }
            viewModelScope.launch { videos.discard(ready) }
        }
    }

    fun preview(media: ExtractedMedia, url: String, file: File?, growing: GrowingDownload?) {
        stopPlayback()
        update {
            it.copy(
                screen = Screen.PLAYER,
                url = url,
                media = MediaState.Ready(media, file, growing),
            )
        }
    }

    fun loading(platform: String) {
        stopPlayback()
        update {
            it.copy(
                screen = Screen.LOADING,
                url = "https://${platform.lowercase()}.com/",
                media = MediaState.Loading(),
            )
        }
    }

    private fun stopPlayback() {
        playback?.cancel()
        val ready = state.value.media as? MediaState.Ready
        ready?.growing?.cancel()
        if (ready != null) viewModelScope.launch { videos.discard(ready) }
        update { it.copy(media = MediaState.Idle) }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob =
            viewModelScope.launch {
                val report = settings.afterLinkSettings()
                val version = withContext(Dispatchers.IO) { settings.engineVersion }
                update {
                    it.copy(
                        links = report,
                        comparison = settings.comparison,
                        engineVersion = version,
                    )
                }
            }
    }

    fun linkSettings(packageName: String) {
        viewModelScope.launch {
            settings.beforeLinkSettings()
            update { it.copy(settingsPackage = packageName) }
        }
    }

    fun settingsOpened() {
        update { it.copy(settingsPackage = null) }
    }

    fun copyReport() {
        viewModelScope.launch {
            val text = settings.diagnostics()
            update { it.copy(copiedReport = text) }
        }
    }

    fun reportCopied() {
        update { it.copy(copiedReport = null) }
    }

    fun diagnose() {
        if (diagnosticJob?.isActive == true) return
        diagnosticJob =
            viewModelScope.launch {
                update { it.copy(testing = true, diagnostics = "") }
                try {
                    val result = settings.diagnose()
                    update { it.copy(testing = false, diagnostics = result) }
                } finally {
                    update { it.copy(testing = false) }
                }
            }
    }

    fun updateEngine() {
        if (state.value.updatingEngine) return
        update { it.copy(updatingEngine = true) }
        viewModelScope.launch {
            try {
                val result = settings.updateEngine()
                val version = withContext(Dispatchers.IO) { settings.engineVersion }
                update {
                    it.copy(
                        engineMessage =
                            UiText(
                                if (result.updated) R.string.engine_updated
                                else R.string.engine_up_to_date,
                                listOf(result.version),
                            ),
                        engineVersion = version,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                update {
                    it.copy(
                        engineMessage =
                            UiText(
                                R.string.engine_update_failed,
                                listOf(error.message.orEmpty().take(160)),
                            )
                    )
                }
            } catch (_: LinkageError) {
                update { it.copy(engineMessage = UiText(R.string.engine_unavailable)) }
            } finally {
                update { it.copy(updatingEngine = false) }
            }
        }
    }

    fun engineMessageShown() {
        update { it.copy(engineMessage = null) }
    }

    fun checkUpdates(manual: Boolean) {
        if (BuildConfig.DEBUG) {
            if (manual)
                update {
                    it.copy(updates = UpdateState.Message(UiText(R.string.updates_require_release)))
                }
            return
        }
        if (
            updateJob?.isActive == true ||
                state.value.updates !is UpdateState.Idle ||
                !settings.shouldCheckUpdate(manual)
        )
            return
        updateJob =
            viewModelScope.launch {
                update { it.copy(updates = UpdateState.Checking) }
                try {
                    val info = updates.latest()
                    update {
                        it.copy(
                            updates =
                                when {
                                    info != null && info.versionCode > BuildConfig.VERSION_CODE ->
                                        UpdateState.Offer(info)
                                    !manual -> UpdateState.Idle
                                    info == null ->
                                        UpdateState.Message(UiText(R.string.no_releases))
                                    else ->
                                        UpdateState.Message(
                                            UiText(
                                                R.string.app_up_to_date,
                                                listOf(BuildConfig.VERSION_NAME),
                                            )
                                        )
                                }
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    update {
                        it.copy(
                            updates =
                                if (manual)
                                    UpdateState.Message(UiText(R.string.update_check_failed))
                                else UpdateState.Idle
                        )
                    }
                }
            }
    }

    fun dismissUpdate() {
        update { it.copy(updates = UpdateState.Idle) }
    }

    fun download(info: AppUpdateInfo) {
        updateJob =
            viewModelScope.launch {
                update { it.copy(updates = UpdateState.Downloading(0)) }
                try {
                    updates.download(info).collect { event ->
                        update {
                            it.copy(
                                updates =
                                    when (event) {
                                        is UpdateRepository.Download.Progress ->
                                            UpdateState.Downloading(event.percent)
                                        is UpdateRepository.Download.Complete ->
                                            UpdateState.Install(event.file)
                                    }
                            )
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    update {
                        it.copy(
                            updates =
                                UpdateState.Message(
                                    UiText(
                                        R.string.update_download_failed,
                                        listOf(error.message.orEmpty().take(180)),
                                    )
                                )
                        )
                    }
                }
            }
    }

    fun cancelUpdate() {
        updateJob?.cancel()
        dismissUpdate()
    }

    fun awaitInstallPermission(value: Boolean) {
        settings.resumeInstall(value)
    }

    fun resumeInstall(allowed: Boolean) {
        if (!settings.consumeInstall()) return
        if (!allowed) {
            update {
                it.copy(updates = UpdateState.Message(UiText(R.string.update_install_denied)))
            }
            return
        }
        updateJob =
            viewModelScope.launch {
                try {
                    val file = updates.verifiedDownload()
                    update { it.copy(updates = UpdateState.Install(file)) }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    update {
                        it.copy(
                            updates = UpdateState.Message(UiText(R.string.update_cache_invalid))
                        )
                    }
                }
            }
    }

    override fun onCleared() {
        (state.value.media as? MediaState.Ready)?.growing?.cancel()
        (state.value.media as? MediaState.Ready)?.let(videos::cleanup)
        super.onCleared()
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass.isAssignableFrom(WatchViewModel::class.java))
            return WatchViewModel(
                WatchRepository(app),
                UpdateRepository(app),
                SettingsRepository(app),
                extras.createSavedStateHandle(),
            )
                as T
        }
    }
}
