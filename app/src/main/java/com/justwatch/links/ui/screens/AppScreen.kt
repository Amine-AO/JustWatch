package com.justwatch.links.ui.screens

import androidx.appcompat.app.AppCompatActivity
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal abstract class AppScreen(
    activity: AppCompatActivity,
    protected val model: WatchViewModel,
) : ScreenUi(activity) {
    abstract val destination: Screen

    abstract fun render(state: WatchState)

    open fun layoutChanged(keyboardVisible: Boolean) = Unit

    open fun onStart() = Unit

    open fun onStop() = Unit

    open fun close() = Unit

    open fun handleBack(): Boolean = false
}
