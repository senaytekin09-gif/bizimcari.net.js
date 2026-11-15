package com.asistans.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var prefs: Prefs
    private lateinit var roleStatus: TextView
    private lateinit var logList: LinearLayout

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
        logList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val root = column().add(
            label("Asistans", 26f, true),
            roleStatus,
            button("1) Varsayılan telefon uygulaması yap") { requestDialerRole() },
            button("2) İzinleri ver") { requestPerms() },
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
        renderLog()
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
