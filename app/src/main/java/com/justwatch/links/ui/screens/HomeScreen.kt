package com.justwatch.links.ui.screens

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.justwatch.links.LinkAssociations
import com.justwatch.links.R
import com.justwatch.links.ui.Screen
import com.justwatch.links.ui.WatchState
import com.justwatch.links.ui.WatchViewModel

internal class HomeScreen(activity: AppCompatActivity, model: WatchViewModel) :
    AppScreen(activity, model) {
    override val destination = Screen.HOME
    private lateinit var hero: LinearLayout
    private lateinit var share: MaterialButton
    private lateinit var notice: TextView
    private lateinit var entry: TextInputEditText
    private lateinit var inputLayout: TextInputLayout

    init {
        build()
    }

    override fun render(state: WatchState) {
        if (entry.text.toString() != state.draft) {
            entry.setText(state.draft)
            entry.setSelection(state.draft.length)
        }
        inputLayout.error = state.inputError?.resolve(this)
        refreshLinkNotice()
    }

    override fun layoutChanged(keyboardVisible: Boolean) {
        share.visibility = if (keyboardVisible) View.GONE else View.VISIBLE
        hero.visibility =
            if (keyboardVisible || (container.height > 0 && container.height < dp(380))) View.GONE
            else View.VISIBLE
    }

    private fun build() {
        val body = fixedPage().apply { id = R.id.home_page }
        val hero = column().apply { gravity = Gravity.BOTTOM }
        this.hero = hero
        hero.addView(text(getString(R.string.home_headline), 30f, bold = true))
        space(hero, 10)
        hero.addView(text(getString(R.string.home_description), 17f, muted = true))
        space(hero, 28)
        body.addView(hero, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        val inputCard = card(body)
        val inputLayout =
            TextInputLayout(this).apply {
                hint = getString(R.string.link_hint)
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                    dp(16).toFloat(),
                )
            }
        val entry =
            TextInputEditText(inputLayout.context).apply {
                id = R.id.link_input
                isSaveEnabled = false
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
                setText(model.state.value.draft)
                textSize = 16f
                setPadding(dp(16), dp(16), dp(16), dp(16))
                imeOptions = EditorInfo.IME_ACTION_GO
            }
        entry.doAfterTextChanged { model.draft(it?.toString().orEmpty()) }
        this.entry = entry
        this.inputLayout = inputLayout
        inputLayout.addView(entry, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        inputCard.addView(inputLayout)
        space(inputCard, 16)
        val actions = LinearLayout(this)
        val paste =
            action(getString(R.string.paste), R.drawable.ic_paste, outlined = true) {
                val clipboard = getSystemService(ClipboardManager::class.java)
                val pasted =
                    clipboard.primaryClip
                        ?.takeIf { it.itemCount > 0 }
                        ?.getItemAt(0)
                        ?.coerceToText(this)
                        ?.toString()
                if (pasted.isNullOrBlank())
                    Snackbar.make(
                            container,
                            getString(R.string.clipboard_empty),
                            Snackbar.LENGTH_SHORT,
                        )
                        .show()
                else {
                    model.paste(pasted)
                }
            }
        val watch = { model.open(model.state.value.draft) }
        actions.addView(paste, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginEnd = dp(8) })
        actions.addView(
            action(getString(R.string.watch), R.drawable.ic_play) { watch() },
            LinearLayout.LayoutParams(0, dp(56), 1f),
        )
        inputCard.addView(actions)
        entry.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) {
                watch()
                true
            } else false
        }
        body.addView(View(this), LinearLayout.LayoutParams(1, 0, 1f))
        notice =
            text("", 14f, muted = true).apply {
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.jw_warning))
                val alert = ContextCompat.getDrawable(activity, R.drawable.ic_alert)?.mutate()
                alert?.setTint(color(R.color.jw_warning))
                alert?.setBounds(0, 0, dp(24), dp(24))
                setCompoundDrawablesRelative(alert, null, null, null)
                compoundDrawablePadding = dp(10)
                setPadding(dp(8), dp(12), dp(8), dp(12))
                isClickable = true
                isFocusable = true
                setOnClickListener { model.navigate(Screen.LINKS) }
            }
        body.addView(notice, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        share =
            action(getString(R.string.share_app_prompt), R.drawable.ic_share, outlined = true) {
                    startActivity(
                        Intent.createChooser(appShareIntent(), getString(R.string.share_justwatch))
                    )
                }
                .apply {
                    id = R.id.share_app_button
                    textSize = 13f
                    maxLines = 2
                    strokeWidth = 0
                    iconSize = dp(20)
                    setPadding(dp(8), 0, dp(8), 0)
                }
        body.addView(share, LinearLayout.LayoutParams(MATCH_PARENT, dp(56)))

        container.requestFocus()
    }

    private fun refreshLinkNotice() {
        if (Build.VERSION.SDK_INT < 31) {
            notice.visibility = View.GONE
            return
        }
        val report = model.state.value.links ?: return
        val missing = LinkAssociations.groups.values.flatten().filterNot { report.enabled(it) }
        notice.apply {
            visibility = if (missing.isNotEmpty() || !report.allowed) View.VISIBLE else View.GONE
            text =
                if (model.state.value.wizardSeen) getString(R.string.link_setup_warning)
                else getString(R.string.link_setup_prompt)
        }
    }

    internal fun appShareIntent() =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, getString(R.string.share_message))
        }
}
