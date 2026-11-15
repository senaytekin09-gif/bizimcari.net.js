package com.asistans.app

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Küçük, kodla kurulan arayüz yardımcıları (XML'siz). */
fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.column(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(20), dp(24), dp(20), dp(24))
}

fun Context.label(text: String, size: Float = 15f, bold: Boolean = false) = TextView(this).apply {
    this.text = text
    textSize = size
    if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    setPadding(0, dp(10), 0, dp(4))
}

fun Context.button(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text
    isAllCaps = false
    setOnClickListener { onClick() }
}

fun LinearLayout.add(vararg views: View): LinearLayout { views.forEach { addView(it) }; return this }
