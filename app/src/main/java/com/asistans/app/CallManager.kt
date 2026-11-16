package com.asistans.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.core.app.NotificationCompat

/** Aktif aramayı ve asistan oturumunu yöneten merkez. */
object CallManager {
    private val main = Handler(Looper.getMainLooper())
    private const val CHANNEL = "calls"
    private const val NOTIF_ID = 42

    var service: AssistantInCallService? = null
    var call: Call? = null
        private set
    var number: String = ""
        private set
    var contactName: String? = null
        private set

    private var aiPending = false
    private var ai: AiConversation? = null
    private var autoAnswerTask: Runnable? = null

    /** Ekranın dinlediği durum metni */
    var onStatus: ((String) -> Unit)? = null
    var lastStatus: String = ""
        private set

    /** Görüşme boyunca ekranda kalan uyarı (ör. mikrofon/TTS sorunu) */
    var warning: String = ""
        private set

    fun status(s: String) {
        lastStatus = s
        main.post { onStatus?.invoke(s) }
    }

    fun warn(s: String) {
        warning = s
        main.post { onStatus?.invoke(lastStatus) }
    }

    // --- Zil sesi: varsayılan telefon uygulaması zili kendisi çalmak zorunda ---
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null

    private fun startRinging(ctx: Context) {
        stopRinging()
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = am.ringerMode
        if (mode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(ctx, RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_RINGTONE_URI
                ringtone = RingtoneManager.getRingtone(ctx, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    if (Build.VERSION.SDK_INT >= 28) isLooping = true
                    play()
                }
            } catch (_: Exception) {}
        }
        if (mode != AudioManager.RINGER_MODE_SILENT) {
            try {
                @Suppress("DEPRECATION")
                vibrator = (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.apply {
                    vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
                }
            } catch (_: Exception) {}
        }
    }

    fun stopRinging() {
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(c: Call, state: Int) {
            if (state != Call.STATE_RINGING) stopRinging()
            when (state) {
                Call.STATE_ACTIVE -> {
                    if (aiPending) {
                        aiPending = false
                        startAi()
                    } else status("Görüşme sürüyor")
                }
                Call.STATE_DISCONNECTED -> status("Arama bitti")
            }
        }
    }

    fun onCallAdded(ctx: Context, c: Call) {
        call = c
        number = c.details.handle?.schemeSpecificPart.orEmpty()
        contactName = ContactsHelper.nameFor(ctx, number)
        c.registerCallback(callback)
        warning = ""

        val incoming = c.state == Call.STATE_RINGING
        if (incoming) startRinging(ctx)
        status(if (incoming) "Gelen arama: ${contactName ?: number}" else "Aranıyor: ${contactName ?: number}")
        showCallUi(ctx, incoming)

        val p = Prefs(ctx)
        if (incoming && p.autoAnswer && p.apiKey.isNotBlank() && !(p.onlyUnknown && contactName != null)) {
            val task = Runnable {
                if (call === c && c.state == Call.STATE_RINGING) answerWithAi()
            }
            autoAnswerTask = task
            main.postDelayed(task, p.answerDelaySec * 1000L)
            status("Gelen arama: ${contactName ?: number} — ${p.answerDelaySec} sn sonra asistan açacak")
        }
    }

    fun onCallRemoved(ctx: Context, c: Call) {
        c.unregisterCallback(callback)
        if (call === c) {
            stopRinging()
            autoAnswerTask?.let { main.removeCallbacks(it) }
            autoAnswerTask = null
            ai?.finish(hangup = false)
            ai = null
            aiPending = false
            call = null
            status("Arama bitti")
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIF_ID)
        }
    }

    fun answerNormally() {
        cancelAuto()
        call?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Aramayı aç (gerekirse) ve konuşmayı asistana devret */
    fun answerWithAi() {
        cancelAuto()
        val c = call ?: return
        if (c.state == Call.STATE_ACTIVE) startAi()
        else {
            aiPending = true
            status("Asistan açıyor…")
            c.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    /** Asistanı durdurup görüşmeyi kullanıcıya bırak */
    fun takeOver() {
        ai?.finish(hangup = false)
        ai = null
        service?.setAudioRoute(CallAudioState.ROUTE_EARPIECE)
        status("Görüşme sende")
    }

    fun hangup() {
        cancelAuto()
        val c = call ?: return
        if (c.state == Call.STATE_RINGING) c.reject(false, null) else c.disconnect()
    }

    private fun cancelAuto() {
        stopRinging()
        autoAnswerTask?.let { main.removeCallbacks(it) }
        autoAnswerTask = null
    }

    private fun startAi() {
        val svc = service ?: return
        if (ai != null) return
        // Hoparlöre al: asistanın sesi karşıya, karşının sesi tanıyıcıya ulaşsın
        svc.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        status("Asistan konuşuyor")
        main.postDelayed({
            if (call?.state == Call.STATE_ACTIVE) {
                ai = AiConversation(svc.applicationContext, number, contactName) { hangup ->
                    ai = null
                    if (hangup) call?.disconnect()
                }.also { it.start() }
            }
        }, 900)
    }

    private fun showCallUi(ctx: Context, incoming: Boolean) {
        val i = Intent(ctx, CallActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Aramalar", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(ctx, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(if (incoming) "Gelen arama" else "Arama")
            .setContentText(contactName ?: number)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) {}
        try { ctx.startActivity(i) } catch (_: Exception) {}
    }
}
