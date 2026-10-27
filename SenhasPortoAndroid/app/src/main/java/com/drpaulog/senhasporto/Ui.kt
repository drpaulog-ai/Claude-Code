package com.drpaulog.senhasporto

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

fun Context.color(id: Int): Int = getColor(id)

fun Context.text(value: String, sizeSp: Float = 15f, colorId: Int = R.color.text, bold: Boolean = false) =
    TextView(this).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color(colorId))
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

fun Context.roundedBackground(fillId: Int, strokeId: Int? = null) = GradientDrawable().apply {
    cornerRadius = dp(12).toFloat()
    setColor(color(fillId))
    if (strokeId != null) setStroke(dp(1), color(strokeId))
}

fun Context.openExternal(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, "Não há nenhuma app para abrir: $url", Toast.LENGTH_LONG).show()
    }
}

/**
 * Barra de título + conteúdo. Em Android 11+ a app desenha por baixo das barras do sistema
 * (obrigatório no Android 15), por isso aplica-se o espaço das barras como margem.
 */
fun Activity.scaffold(
    title: String,
    content: View,
    onBack: (() -> Unit)? = null,
    action: Pair<String, () -> Unit>? = null,
): View {
    val header = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
        minimumHeight = dp(56)
    }
    if (onBack != null) {
        header.addView(headerButton("←") { onBack() })
    }
    header.addView(
        text(title, 20f, R.color.on_brand, bold = true).apply {
            maxLines = 1
            setPadding(dp(8), 0, dp(8), 0)
        },
        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f),
    )
    if (action != null) {
        header.addView(headerButton(action.first) { action.second() })
    }

    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(color(R.color.brand))
        addView(header, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsets.CONSUMED
        }
    }
    return root
}

private fun Context.headerButton(label: String, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    setTextColor(color(R.color.on_brand))
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    background = null
    minWidth = dp(48)
    setOnClickListener { onClick() }
}
