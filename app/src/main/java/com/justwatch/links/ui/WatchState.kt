package com.justwatch.links.ui

import com.justwatch.links.AppUpdateInfo
import com.justwatch.links.LinkAssociations
import com.justwatch.links.data.MediaState
import java.io.File

internal enum class Screen {
    HOME,
    SETTINGS,
    LINKS,
    DIAGNOSTICS,
    LOADING,
    PLAYER,
    ERROR,
    WEB,
}

internal sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class Offer(val info: AppUpdateInfo) : UpdateState

    data class Downloading(val percent: Int) : UpdateState

    data class Message(val text: UiText) : UpdateState

    data class Install(val file: File) : UpdateState
}

internal data class WatchState(
    val screen: Screen = Screen.HOME,
    val url: String = "",
    val media: MediaState = MediaState.Idle,
    val draft: String = "",
    val inputError: UiText? = null,
    val theme: Int,
    val wizardSeen: Boolean,
    val wizardActive: Boolean,
    val links: LinkAssociations.Report? = null,
    val comparison: String? = null,
    val diagnostics: String = "",
    val testing: Boolean = false,
    val engineVersion: String = "",
    val updatingEngine: Boolean = false,
    val engineMessage: UiText? = null,
    val updates: UpdateState = UpdateState.Idle,
    val settingsPackage: String? = null,
    val copiedReport: String? = null,
)
