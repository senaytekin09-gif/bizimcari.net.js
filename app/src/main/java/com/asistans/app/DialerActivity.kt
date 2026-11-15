package com.asistans.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/** Varsayılan telefon uygulaması olmak için gereken basit tuş takımı. */
class DialerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 26f
            hint = "Numara"
            setText(intent?.data?.schemeSpecificPart.orEmpty())
        }
        setContentView(
            column().add(
                label("Ara", 22f, true),
                input,
                button("📞 Ara") { place(input.text.toString()) }
            )
        )
    }

    private fun place(num: String) {
        if (num.isBlank()) return
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CALL_PHONE), 1)
            return
        }
        try {
            getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", num.trim(), null), Bundle())
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Arama başlatılamadı: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
