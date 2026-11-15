package com.asistans.app

import android.os.Bundle
import android.telecom.Call
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Arama sırasında görünen ekran. */
class CallActivity : AppCompatActivity() {
    private lateinit var who: TextView
    private lateinit var status: TextView
    private lateinit var buttons: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        who = label("", 26f, true)
        status = label("", 16f)
        buttons = column().apply { setPadding(0, dp(24), 0, 0) }
        setContentView(column().add(who, status, buttons))
    }

    override fun onResume() {
        super.onResume()
        CallManager.onStatus = { refresh() }
        refresh()
    }

    override fun onPause() {
        super.onPause()
        CallManager.onStatus = null
    }

    private fun refresh() {
        val call = CallManager.call
        if (call == null || call.state == Call.STATE_DISCONNECTED) {
            status.text = "Arama bitti"
            status.postDelayed({ finish() }, 800)
            return
        }
        who.text = CallManager.contactName ?: CallManager.number
        status.text = CallManager.lastStatus
        buttons.removeAllViews()
        if (call.state == Call.STATE_RINGING) {
            buttons.add(
                button("📞 Aç") { CallManager.answerNormally() },
                button("🤖 Asistan cevaplasın") { CallManager.answerWithAi() },
            )
        } else if (call.state == Call.STATE_ACTIVE) {
            buttons.add(
                button("🤖 Asistana devret") { CallManager.answerWithAi() },
                button("🙋 Görüşmeyi devral") { CallManager.takeOver() },
            )
        }
        buttons.add(button("⛔ Kapat") { CallManager.hangup() })
    }
}
