package com.asistans.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var roleStatus: TextView
    private lateinit var a11yStatus: TextView
    private lateinit var crashBox: LinearLayout
    private lateinit var logList: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var testTts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val apiKey = field("Claude API anahtarı (sk-ant-…)", prefs.apiKey, password = true)
        val model = field("Model", prefs.model)
        val owner = field("Adın (asistan seni böyle anar)", prefs.ownerName)
        val delay = field("Kaç saniye çaldıktan sonra açsın", prefs.answerDelaySec.toString(), number = true)
        val greeting = field("Karşılama cümlesi (boş = varsayılan)", prefs.greeting, multi = true)
        val extra = field("Asistana ek talimatlar (ör. iş saatlerim 9-18)", prefs.extraInstructions, multi = true)

        val auto = switch("Gelen aramaları asistan otomatik açsın", prefs.autoAnswer)
        val onlyUnknown = switch("Sadece rehberde kayıtlı olmayanlar için", prefs.onlyUnknown)
        val share = switch("Arayanın rehberdeki adını yapay zekâya gönder", prefs.shareContactName)

        roleStatus = label("", 14f)
        a11yStatus = label("", 14f)
        crashBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        logList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val root = column().add(
            label("Asistans", 26f, true),
            crashBox,
            roleStatus,
            button("1) Varsayılan telefon uygulaması yap") { requestDialerRole() },
            button("2) İzinleri ver") { requestPerms() },
            a11yStatus,
            button("3) Arama sesi erişimini aç (Erişilebilirlik)") { openA11y() },
            label(
                "Android, görüşme sırasında mikrofonu yalnızca erişilebilirlik hizmetlerine verir. " +
                    "Ayarlar'da 'Asistans arama sesi'ni açın. Seçenek gri ise: Ayarlar → Uygulamalar → Asistans → sağ üst ⋮ → " +
                    "'Kısıtlanmış ayarlara izin ver', sonra tekrar deneyin.", 13f
            ),
            label("Ayarlar", 18f, true),
            apiKey, model, owner, delay, greeting, extra, auto, onlyUnknown, share,
            button("💾 Kaydet") {
                prefs.apiKey = apiKey.text.toString()
                prefs.model = model.text.toString().ifBlank { "claude-haiku-5-5" }
                prefs.ownerName = owner.text.toString()
                prefs.answerDelaySec = delay.text.toString().toIntOrNull() ?: 15
                prefs.greeting = greeting.text.toString()
                prefs.extraInstructions = extra.text.toString()
                prefs.autoAnswer = auto.isChecked
                prefs.onlyUnknown = onlyUnknown.isChecked
                prefs.shareContactName = share.isChecked
                Toast.makeText(this, "Kaydedildi", Toast.LENGTH_SHORT).show()
            },
            button("🔑 API anahtarını test et") {
                testApi(apiKey.text.toString().trim(), model.text.toString().trim().ifBlank { "claude-haiku-5-5" })
            },
            button("🔊 Türkçe sesi test et") { testVoice(owner.text.toString()) },
            button("📞 Tuş takımı") { startActivity(Intent(this, DialerActivity::class.java)) },
            label("Asistanın görüşmeleri", 18f, true),
            button("Kayıtları temizle") { CallLogStore.clear(this); renderLog() },
            logList
        )
        setContentView(ScrollView(this).apply { addView(root) })
        requestPerms()
    }

    override fun onResume() {
        super.onResume()
        val rm = getSystemService(RoleManager::class.java)
        roleStatus.text = if (rm.isRoleHeld(RoleManager.ROLE_DIALER))
            "✅ Varsayılan telefon uygulaması" else "⚠️ Henüz varsayılan telefon uygulaması değil — asistan aramaları açamaz"
        a11yStatus.text = when {
            Build.VERSION.SDK_INT < 33 -> "⚠️ Android 13 altı: görüşme sırasında arayanın sesi alınamayabilir"
            CallAudioAccessibilityService.isEnabled(this) -> "✅ Arama sesi erişimi açık"
            else -> "⚠️ Arama sesi erişimi kapalı — asistan arayanı duyamaz"
        }
        renderCrash()
        renderLog()
    }

    override fun onDestroy() {
        testTts?.shutdown(); testTts = null
        scope.cancel()
        super.onDestroy()
    }

    private fun openA11y() {
        try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        catch (_: Exception) { Toast.makeText(this, "Erişilebilirlik ayarları açılamadı", Toast.LENGTH_LONG).show() }
    }

    private fun renderCrash() {
        crashBox.removeAllViews()
        val crash = App.lastCrash(this) ?: return
        val t = label("❗ Uygulama son çalışmada çöktü. Ayrıntı için dokunun.", 14f).apply {
            setTextColor(0xFFB3261E.toInt())
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Hata kaydı").setMessage(crash)
                    .setPositiveButton("Kopyala") { _, _ ->
                        val cm = getSystemService(android.content.ClipboardManager::class.java)
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Asistans hata", crash))
                        Toast.makeText(this@MainActivity, "Kopyalandı", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Sil") { _, _ -> App.crashFile(this@MainActivity).delete(); renderCrash() }
                    .show()
            }
        }
        crashBox.addView(t)
    }

    private fun testApi(key: String, model: String) {
        if (key.isBlank()) { Toast.makeText(this, "Önce API anahtarını girin", Toast.LENGTH_SHORT).show(); return }
        Toast.makeText(this, "Deneniyor…", Toast.LENGTH_SHORT).show()
        scope.launch {
            val msg = try {
                val r = ClaudeClient.send(key, model, "Kısa cevap ver.", listOf(Msg("user", "Sadece 'Tamam' yaz.")), maxTokens = 10)
                "✅ Çalışıyor ($model): $r"
            } catch (e: Exception) {
                "❌ ${e.message?.take(400)}"
            }
            AlertDialog.Builder(this@MainActivity).setTitle("API testi").setMessage(msg).setPositiveButton("Tamam", null).show()
        }
    }

    private fun testVoice(name: String) {
        testTts?.shutdown()
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(this) { status ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (status != TextToSpeech.SUCCESS) {
                    Toast.makeText(this, "Metin okuma motoru başlatılamadı", Toast.LENGTH_LONG).show(); return@post
                }
                val r = engine.setLanguage(Locale("tr", "TR"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "Türkçe ses yüklü değil: Ayarlar → Metin okuma → Türkçe ses verisini indirin", Toast.LENGTH_LONG).show()
                    try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) } catch (_: Exception) {}
                    return@post
                }
                val who = name.ifBlank { "telefon sahibi" }
                engine.speak("Merhaba, ben $who'in yapay zekâ asistanıyım.", TextToSpeech.QUEUE_FLUSH, null, "test")
            }
        }
        testTts = engine
    }

    private fun renderLog() {
        logList.removeAllViews()
        val fmt = SimpleDateFormat("dd.MM HH:mm", Locale("tr"))
        val records = CallLogStore.all(this)
        if (records.isEmpty()) logList.addView(label("Henüz kayıt yok.", 14f))
        records.forEach { r ->
            val t = label("${fmt.format(Date(r.time))} • ${r.name ?: r.number}\n${r.summary}", 14f)
            t.setOnClickListener {
                AlertDialog.Builder(this).setTitle(r.name ?: r.number)
                    .setMessage(r.transcript).setPositiveButton("Tamam", null).show()
            }
            logList.addView(t)
        }
    }

    private fun requestDialerRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            Toast.makeText(this, "Zaten varsayılan", Toast.LENGTH_SHORT).show(); return
        }
        startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER), 7)
    }

    private fun requestPerms() {
        val p = mutableListOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
        )
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(p.toTypedArray(), 3)
    }

    private fun field(hint: String, value: String, password: Boolean = false, number: Boolean = false, multi: Boolean = false) =
        EditText(this).apply {
            this.hint = hint
            setText(value)
            inputType = when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER
                multi -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
        }

    private fun switch(text: String, checked: Boolean) = Switch(this).apply {
        this.text = text
        isChecked = checked
        setPadding(0, dp(8), 0, dp(8))
    }
}
