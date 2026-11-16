package com.asistans.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.max

/**
 * Mikrofonu bu uygulamanın kendisi kaydeder ve sesi bir boruya (pipe) yazar.
 * Borunun okuma ucu konuşma tanımaya verilir (Android 13+ EXTRA_AUDIO_SOURCE).
 *
 * Neden: Telefon görüşmesi sırasında Android mikrofonu yalnızca aramaya ve
 * erişilebilirlik hizmetlerine verir. Google konuşma tanıma kendi başına
 * mikrofonu açarsa sessizlik alır. Bu uygulamanın erişilebilirlik hizmeti
 * açıkken ise mikrofonu kendimiz okuyabiliriz.
 */
class MicStream private constructor(
    private val rec: AudioRecord,
    private val readSide: ParcelFileDescriptor,
    private val writeSide: ParcelFileDescriptor
) {
    @Volatile private var running = true

    /** Kayıt boyunca duyulan en yüksek ses seviyesi (0 = tamamen sessiz). */
    @Volatile var peak = 0
        private set

    /** Konuşma tanımaya verilecek okuma ucu */
    val fd: ParcelFileDescriptor get() = readSide

    private fun start() {
        rec.startRecording()
        thread(name = "asistans-mic", isDaemon = true) {
            val out = ParcelFileDescriptor.AutoCloseOutputStream(writeSide)
            val buf = ShortArray(1600) // 100 ms @ 16 kHz
            val bytes = ByteArray(buf.size * 2)
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var p = peak
                    for (i in 0 until n) {
                        val s = buf[i].toInt()
                        p = max(p, abs(s))
                        bytes[2 * i] = (s and 0xFF).toByte()
                        bytes[2 * i + 1] = ((s shr 8) and 0xFF).toByte()
                    }
                    peak = p
                    out.write(bytes, 0, n * 2)
                }
            } catch (_: IOException) {
                // Konuşma tanıma okumayı bıraktı (boru kapandı) — normal bitiş
            } finally {
                running = false
                try { rec.stop() } catch (_: Exception) {}
                rec.release()
                try { out.close() } catch (_: Exception) {}
            }
        }
    }

    fun close() {
        running = false
        try { readSide.close() } catch (_: Exception) {}
    }

    companion object {
        const val SAMPLE_RATE = 16000

        /** Kayıt başlatılamazsa null döner. */
        @SuppressLint("MissingPermission")
        fun open(ctx: Context): MicStream? {
            if (ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return null
            val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (min <= 0) return null
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, 6400) * 2
                )
            } catch (_: Exception) { return null }
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return null }
            val pipe = try { ParcelFileDescriptor.createPipe() } catch (_: IOException) { rec.release(); return null }
            return MicStream(rec, pipe[0], pipe[1]).also {
                try { it.start() } catch (_: Exception) { it.close(); rec.release(); return null }
            }
        }
    }
}
