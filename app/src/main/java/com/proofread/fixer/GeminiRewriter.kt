package com.proofread.fixer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Google Gemini (free key). Call from a background thread. */
object GeminiRewriter {

    const val DEFAULT_MODEL = "gemini-flash-latest"

    fun keyFrom(prefs: SharedPreferences): String {
        val saved = SecureStore.get(prefs, "api_key").trim()
        return if (saved.isNotEmpty()) saved else BuildConfig.GEMINI_API_KEY.trim()
    }

    fun hasKey(prefs: SharedPreferences): Boolean = keyFrom(prefs).isNotEmpty()

    fun complete(prefs: SharedPreferences, system: String, text: String): String {
        val key = keyFrom(prefs)
        if (key.isEmpty()) throw RewriteException("No Gemini API key found.")
        val model = prefs.getString("model", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        return call(key, model, system, text)
    }

    private fun call(apiKey: String, model: String, system: String, text: String): String {
        val payload = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", text)))
                )
            )
            .put("generationConfig", JSONObject().put("temperature", 0.4))

        val modelName = model.trim().removePrefix("models/")
        var waitingForReply = false
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent")
            .openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", apiKey)
            conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            waitingForReply = true

            val code = conn.responseCode
            if (code != 200) {
                val errText = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (e: Exception) {
                    ""
                }
                val apiMessage = try {
                    JSONObject(errText).getJSONObject("error").getString("message")
                } catch (e: Exception) {
                    ""
                }
                val friendly = when (code) {
                    429 -> "Gemini free limit reached. Wait a minute and try again."
                    400, 403 -> "Gemini rejected the request (check your API key and model name). $apiMessage"
                    404 -> "Gemini model not found. Check the model name in the Proofread app. $apiMessage"
                    else -> "Gemini returned error $code. $apiMessage"
                }
                throw RewriteException(friendly.trim())
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val candidates = json.optJSONArray("candidates")
            if (candidates == null || candidates.length() == 0) {
                throw RewriteException("Gemini returned no result. The text may have been blocked.")
            }
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw RewriteException("Gemini returned no result.")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            val out = sb.toString().trim()
            if (out.isEmpty()) throw RewriteException("Gemini returned an empty answer.")
            return out
        } catch (e: java.net.SocketTimeoutException) {
            throw RewriteException(
                if (waitingForReply) "Gemini took too long to answer. Try again."
                else "Could not connect to Gemini (timed out). Check your internet connection."
            )
        } catch (e: java.net.UnknownHostException) {
            throw RewriteException("Cannot reach Gemini. Check your internet connection.")
        } catch (e: java.io.IOException) {
            throw RewriteException("Network problem: " + (e.message ?: "connection failed"))
        } finally {
            conn.disconnect()
        }
    }
}
