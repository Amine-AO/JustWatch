package com.justwatch.links.ui.screens

import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.justwatch.links.R

internal open class ScreenUi(val activity: AppCompatActivity) : ContextWrapper(activity) {
    val container = FrameLayout(this)

    fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    fun color(id: Int) = ContextCompat.getColor(this, id)

    fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    fun space(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    fun text(value: String, size: Float = 16f, muted: Boolean = false, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color(if (muted) R.color.jw_on_surface_variant else R.color.jw_on_surface))
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(dp(3).toFloat(), 1f)
        }

    fun logo(size: Int) =
        ImageView(this).apply {
            setImageResource(R.drawable.ic_logo)
            contentDescription = null
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }

    fun action(label: String, icon: Int? = null, outlined: Boolean = false, click: () -> Unit) =
        MaterialButton(
                this,
                null,
                if (outlined) com.google.android.material.R.attr.materialButtonOutlinedStyle
                else com.google.android.material.R.attr.materialButtonStyle,
            )
            .apply {
                text = label
                isAllCaps = false
                cornerRadius = dp(28)
                minHeight = dp(56)
                insetTop = dp(2)
                insetBottom = dp(2)
                if (icon != null) {
                    setIconResource(icon)
                    iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                }
                setOnClickListener { click() }
            }

    fun headerButton(description: String, icon: Int, click: () -> Unit) =
        action("", icon, click = click).apply {
            contentDescription = description
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(48)
            iconSize = dp(24)
            iconPadding = 0
            insetTop = 0
            insetBottom = 0
            setPadding(dp(12), dp(12), dp(12), dp(12))
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            iconTint = ColorStateList.valueOf(color(R.color.jw_on_surface))
        }

    fun fixedPage(center: Boolean = false): LinearLayout =
        column().apply {
            setPadding(dp(24), dp(16), dp(24), dp(24))
            if (center) gravity = Gravity.CENTER_VERTICAL
            container.addView(this, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

    fun fullButton(parent: LinearLayout, button: View) {
        parent.addView(button, LinearLayout.LayoutParams(MATCH_PARENT, dp(60)))
    }

    fun card(parent: LinearLayout, tonal: Boolean = false): LinearLayout {
        val box =
            MaterialCardView(this).apply {
                radius = dp(24).toFloat()
                cardElevation = 0f
                strokeWidth = if (tonal) 0 else dp(1)
                setStrokeColor(color(R.color.jw_surface_variant))
                setCardBackgroundColor(
                    color(if (tonal) R.color.jw_surface_variant else R.color.jw_surface)
                )
            }
        val inside = column().apply { setPadding(dp(20), dp(20), dp(20), dp(20)) }
        box.addView(inside)
        parent.addView(box, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return inside
    }

    fun scrollPage(): LinearLayout {
        val scroll =
            ScrollView(this).apply {
                isFillViewport = true
                clipToPadding = false
            }
        val body = column().apply { setPadding(dp(24), dp(8), dp(24), dp(24)) }
        scroll.addView(body)
        container.addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return body
    }

    fun settingRow(
        parent: LinearLayout,
        title: String,
        description: String,
        icon: Int,
        click: () -> Unit,
    ): TextView {
        val row =
            LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(16), dp(8), dp(16))
                minimumHeight = dp(80)
                isClickable = true
                isFocusable = true
                val out = TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
                setOnClickListener { click() }
            }
        row.addView(
            ImageView(this).apply {
                setImageResource(icon)
                imageTintList = ColorStateList.valueOf(color(R.color.jw_primary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            },
            LinearLayout.LayoutParams(dp(24), dp(24)),
        )
        val labels = column().apply { setPadding(dp(18), 0, dp(8), 0) }
        labels.addView(text(title, 16f, bold = true))
        val detail = text(description, 13f, muted = true)
        labels.addView(detail)
        row.addView(labels, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        row.addView(
            ImageView(this).apply {
                setImageResource(R.drawable.ic_arrow)
                imageTintList = ColorStateList.valueOf(color(R.color.jw_on_surface_variant))
                contentDescription = null
            },
            LinearLayout.LayoutParams(dp(20), dp(20)),
        )
        parent.addView(row, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        return detail
    }

    fun section(parent: LinearLayout, title: String) {
        space(parent, 24)
        parent.addView(
            text(title, 13f, bold = true).apply { setTextColor(color(R.color.jw_primary)) }
        )
        space(parent, 8)
    }
}
