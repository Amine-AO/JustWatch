package com.justwatch.links.ui.screens

import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.justwatch.links.R
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class DiagnosticsScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.DIAGNOSTICS
    private lateinit var result: TextView
    private lateinit var runButton: MaterialButton

    init {
        build()
    }

    override fun render(state: WatchState) {
        result.text =
            if (state.testing) getString(R.string.checking_connection)
            else state.diagnostics.ifEmpty { getString(R.string.ready_to_test) }
        runButton.isEnabled = !state.testing
    }

    private fun build() {
        val body = scrollPage()
        space(body, 16)
        body.addView(text(getString(R.string.check_your_connection), 26f, bold = true))
        space(body, 12)
        body.addView(text(getString(R.string.connection_test_description), 15f, muted = true))
        space(body, 24)
        result =
            text(
                    model.state.value.diagnostics.ifEmpty { getString(R.string.ready_to_test) },
                    14f,
                    muted = true,
                )
                .apply { setTextIsSelectable(true) }
        card(body, tonal = true).addView(result)
        space(body, 20)
        runButton =
            action(getString(R.string.run_connection_test), R.drawable.ic_network) {
                    model.diagnose()
                }
                .apply { isEnabled = !model.state.value.testing }
        fullButton(body, runButton)
    }
}
