package com.asistans.app

import android.app.Application
import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Uygulama çökerse hatayı cihazda saklar; ana ekranda gösterilir. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val time = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale("tr")).format(Date())
                crashFile(this).writeText("$time  [${t.name}]\n$sw")
            } catch (_: Exception) {}
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        fun crashFile(ctx: Context) = File(ctx.filesDir, "last_crash.txt")
        fun lastCrash(ctx: Context): String? =
            crashFile(ctx).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }
}
