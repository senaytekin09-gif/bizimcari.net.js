package com.asistans.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Msg(val role: String, val text: String)

/** Anthropic Messages API istemcisi. */
object ClaudeClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun send(
        apiKey: String,
        model: String,
        system: String,
        messages: List<Msg>,
        maxTokens: Int = 300
    ): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", maxTokens)
            .put("system", system)
            .put("messages", arr)
            .toString()
            .toRequestBody("application/json".toMediaType())

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(body)
            .build()

        http.newCall(req).execute().use { resp ->
            val txt = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException("Claude API ${resp.code}: $txt")
            val content = JSONObject(txt).getJSONArray("content")
            buildString {
                for (i in 0 until content.length()) {
                    val c = content.getJSONObject(i)
                    if (c.optString("type") == "text") append(c.optString("text"))
                }
            }.trim()
        }
    }
}
