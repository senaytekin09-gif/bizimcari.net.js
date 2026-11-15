package com.asistans.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class AiCallRecord(
    val time: Long,
    val number: String,
    val name: String?,
    val transcript: String,
    val summary: String
)

/** Asistanın yaptığı görüşmeleri cihazda saklar. */
object CallLogStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "ai_calls.json")

    @Synchronized
    fun add(ctx: Context, r: AiCallRecord) {
        val old = load(ctx)
        val list = JSONArray()
        list.put(
            JSONObject().put("time", r.time).put("number", r.number).put("name", r.name ?: "")
                .put("transcript", r.transcript).put("summary", r.summary)
        )
        for (i in 0 until minOf(old.length(), 199)) list.put(old.get(i))
        file(ctx).writeText(list.toString())
    }

    @Synchronized
    fun all(ctx: Context): List<AiCallRecord> {
        val arr = load(ctx)
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            if (!o.has("time")) return@mapNotNull null
            AiCallRecord(
                o.getLong("time"), o.optString("number"), o.optString("name").ifBlank { null },
                o.optString("transcript"), o.optString("summary")
            )
        }
    }

    @Synchronized
    fun clear(ctx: Context) { file(ctx).delete() }

    private fun load(ctx: Context): JSONArray = try {
        JSONArray(file(ctx).readText())
    } catch (_: Exception) { JSONArray() }
}
