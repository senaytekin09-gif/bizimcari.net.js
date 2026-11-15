package com.asistans.app

import android.content.Context

/** Basit ayar deposu. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("asistans", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("apiKey", "") ?: ""
        set(v) = sp.edit().putString("apiKey", v.trim()).apply()

    var model: String
        get() = sp.getString("model", "claude-haiku-5-5") ?: "claude-haiku-5-5"
        set(v) = sp.edit().putString("model", v.trim()).apply()

    var ownerName: String
        get() = sp.getString("ownerName", "") ?: ""
        set(v) = sp.edit().putString("ownerName", v.trim()).apply()

    /** Asistan gelen aramaları otomatik açsın mı */
    var autoAnswer: Boolean
        get() = sp.getBoolean("autoAnswer", false)
        set(v) = sp.edit().putBoolean("autoAnswer", v).apply()

    /** Kaç saniye çaldıktan sonra asistan açsın */
    var answerDelaySec: Int
        get() = sp.getInt("answerDelaySec", 15)
        set(v) = sp.edit().putInt("answerDelaySec", v.coerceIn(0, 60)).apply()

    /** Sadece rehberde kayıtlı olmayan numaralar için mi */
    var onlyUnknown: Boolean
        get() = sp.getBoolean("onlyUnknown", false)
        set(v) = sp.edit().putBoolean("onlyUnknown", v).apply()

    /** Arayanın rehberdeki adı yapay zekâya gönderilsin mi (varsayılan: hayır) */
    var shareContactName: Boolean
        get() = sp.getBoolean("shareContactName", false)
        set(v) = sp.edit().putBoolean("shareContactName", v).apply()

    var extraInstructions: String
        get() = sp.getString("extra", "") ?: ""
        set(v) = sp.edit().putString("extra", v).apply()

    var greeting: String
        get() = sp.getString("greeting", "") ?: ""
        set(v) = sp.edit().putString("greeting", v.trim()).apply()
}
