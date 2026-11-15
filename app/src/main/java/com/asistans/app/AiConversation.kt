package com.asistans.app

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Konuşma döngüsü: Konuş (TTS) → Dinle (STT) → Claude'a sor → Konuş …
 * Arama hoparlördeyken çalışır.
 */
class AiConversation(
    private val ctx: Context,
    private val number: String,
    private val contactName: String?,
    private val onFinished: (hangup: Boolean) -> Unit
) {
    private val prefs = Prefs(ctx)
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val history = mutableListOf<Msg>()
    private val transcript = StringBuilder()

    private var tts: TextToSpeech? = null
    private var stt: SpeechRecognizer? = null
    private var finished = false
    private var hangupAfterSpeech = false
    private var silentTurns = 0
    private var turns = 0
    private val maxTurns = 12

    private val ownerName get() = prefs.ownerName.ifBlank { "telefon sahibi" }

    private fun systemPrompt(): String = buildString {
        append(
            """
            Sen $ownerName adlı kişinin telefon asistanısın ve şu an onun yerine bir telefon görüşmesi yapıyorsun.
            $ownerName şu an telefona çıkamıyor.
            Kurallar:
            - Her zaman Türkçe, doğal, sıcak ve KISA konuş (en fazla 2-3 kısa cümle). Söylediklerin sesli okunacak; liste, emoji, madde işareti, markdown kullanma.
            - Yapay zekâ asistanı olduğunu gizleme; sorulursa açıkça söyle.
            - Amacın: arayanın kim olduğunu ve ne istediğini öğrenmek, mesajını almak, genel bilgi sorularına bildiğin kadarıyla cevap vermek.
            - $ownerName adına söz verme, randevu kesinleştirme, para/ödeme konusunda taahhütte bulunma; "kendisine ileteceğim" de.
            - $ownerName hakkında kişisel bilgi (adres, konum, program, şifre, hesap bilgisi vb.) asla paylaşma.
            - Bilmediğin bir şeyi uydurma.
            - Görüşme doğal olarak bittiğinde (mesaj alındı, vedalaşıldı) kibarca vedalaş ve cevabının EN SONUNA [KAPAT] yaz.
            """.trimIndent()
        )
        if (prefs.shareContactName && contactName != null) {
            append("\nArayan kişi rehberde \"$contactName\" olarak kayıtlı.")
        }
        if (prefs.extraInstructions.isNotBlank()) {
            append("\nEk talimatlar:\n").append(prefs.extraInstructions)
        }
    }

    fun start() {
        boostVolume()
        tts = TextToSpeech(ctx) { status ->
            if (status != TextToSpeech.SUCCESS) { finish(hangup = false); return@TextToSpeech }
            tts?.language = Locale("tr", "TR")
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    main.post {
                        if (finished) return@post
                        if (hangupAfterSpeech) finish(hangup = true) else listen()
                    }
                }
                @Deprecated("eski API")
                override fun onError(id: String?) { main.post { if (!finished) listen() } }
            })
            val greet = prefs.greeting.ifBlank {
                "Merhaba, ben $ownerName'in yapay zekâ asistanıyım. Kendisi şu an telefona çıkamıyor. Kiminle görüşüyorum ve nasıl yardımcı olabilirim?"
            }
            history.add(Msg("user", "(Arama başladı. Arayanı karşıla.)"))
            history.add(Msg("assistant", greet))
            say(greet)
        }
    }

    private fun say(text: String) {
        val clean = text.replace("[KAPAT]", "").trim()
        if (text.contains("[KAPAT]")) hangupAfterSpeech = true
        transcript.append("Asistan: ").append(clean).append('\n')
        CallManager.status("Asistan: $clean")
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "u${System.nanoTime()}")
    }

    private fun listen() {
        if (finished) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            CallManager.status("Bu cihazda konuşma tanıma yok")
            finish(hangup = false); return
        }
        stt?.destroy()
        stt = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) onSilence() else onHeard(text)
                }
                override fun onError(error: Int) {
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> onSilence()
                        else -> main.postDelayed({ listen() }, 500)
                    }
                }
                override fun onReadyForSpeech(p: Bundle?) { CallManager.status("Dinliyor…") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "tr-TR")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            })
        }
    }

    private fun onSilence() {
        if (finished) return
        silentTurns++
        when {
            silentTurns >= 3 -> { hangupAfterSpeech = true; say("Sesinizi alamıyorum. Mesajınızı daha sonra iletebilirsiniz, iyi günler.") }
            silentTurns == 2 -> say("Beni duyuyor musunuz?")
            else -> listen()
        }
    }

    private fun onHeard(text: String) {
        silentTurns = 0
        turns++
        transcript.append("Arayan: ").append(text).append('\n')
        CallManager.status("Arayan: $text")
        history.add(Msg("user", text))
        if (turns >= maxTurns) {
            hangupAfterSpeech = true
            say("Mesajınızı aldım, $ownerName'e ileteceğim. İyi günler.")
            return
        }
        CallManager.status("Düşünüyor…")
        scope.launch {
            val reply = try {
                ClaudeClient.send(prefs.apiKey, prefs.model, systemPrompt(), history)
            } catch (e: Exception) {
                CallManager.status("Hata: ${e.message}")
                "Kusura bakmayın, bir sorun oluştu. Mesajınızı $ownerName'e ileteceğim. [KAPAT]"
            }
            if (finished) return@launch
            history.add(Msg("assistant", reply.ifBlank { "Anlayamadım, tekrar eder misiniz?" }))
            say(reply.ifBlank { "Anlayamadım, tekrar eder misiniz?" })
        }
    }

    private fun boostVolume() {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        listOf(AudioManager.STREAM_VOICE_CALL, AudioManager.STREAM_MUSIC).forEach {
            try { am.setStreamVolume(it, am.getStreamMaxVolume(it), 0) } catch (_: Exception) {}
        }
    }

    /** Oturumu kapatır, özeti çıkarıp kaydeder. */
    fun finish(hangup: Boolean) {
        if (finished) return
        finished = true
        main.post {
            stt?.destroy(); stt = null
            tts?.stop(); tts?.shutdown(); tts = null
        }
        onFinished(hangup)
        saveRecord()
    }

    private fun saveRecord() {
        val text = transcript.toString()
        if (text.isBlank()) return
        val appCtx = ctx.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            val summary = try {
                ClaudeClient.send(
                    prefs.apiKey, prefs.model,
                    "Aşağıdaki telefon görüşmesini Türkçe olarak 1-3 cümlede özetle: arayan kim, ne istedi, geri dönüş gerekiyor mu.",
                    listOf(Msg("user", text)), maxTokens = 200
                )
            } catch (_: Exception) { "(özet alınamadı)" }
            CallLogStore.add(appCtx, AiCallRecord(System.currentTimeMillis(), number, contactName, text, summary))
        }
    }
}
