package com.proofread.fixer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Groq (free key, very fast, OpenAI-compatible). Call from a background thread. */
object GroqRewriter {

    const val DEFAULT_MODEL = "openai/gpt-oss-120b"

    private const val BASE = "https://api.groq.com/openai/v1"

    /** Thrown when Groq says the chosen model is not available on this account. */
    private class ModelNotFound(message: String) : Exception(message)

    private val preferred = listOf(
        "openai/gpt-oss-120b",
        "llama-3.3-70b-versatile",
        "openai/gpt-oss-20b",
        "llama-3.1-8b-instant"
    )
    private val notForChat = Regex("whisper|tts|guard|compound|embed|orpheus|playai|safeguard|prompt|distil")
    private val chatFamilies = Regex("llama|gpt-oss|qwen|gemma|kimi|mistral|deepseek")

    fun keyFrom(prefs: SharedPreferences): String {
        val saved = SecureStore.get(prefs, "groq_key").trim()
        return if (saved.isNotEmpty()) saved else BuildConfig.GROQ_API_KEY.trim()
    }

    fun hasKey(prefs: SharedPreferences): Boolean = keyFrom(prefs).isNotEmpty()

    fun complete(prefs: SharedPreferences, system: String, text: String): String {
        val key = keyFrom(prefs)
        if (key.isEmpty()) throw RewriteException("No Groq API key found.")
        val model = prefs.getString("groq_model", DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }
        return try {
            call(text, key, model, system)
        } catch (e: ModelNotFound) {
            // The saved model is gone: ask Groq what this account can use, pick one, and remember it.
            val alt = pickModel(key, model)
                ?: throw RewriteException(
                    "The model \"$model\" is not available on your Groq account. Tap \"Show available Groq models\" in the app and type one of them in the Groq model box."
                )
            val out = call(text, key, alt, system)
            prefs.edit().putString("groq_model", alt).apply()
            out
        }
    }

    private fun pickModel(key: String, exclude: String): String? {
        val ids = listModels(key).filter { it != exclude }
        for (p in preferred) if (p in ids) return p
        return ids.firstOrNull { chatFamilies.containsMatchIn(it) && !notForChat.containsMatchIn(it) }
    }

    /** Lists the model IDs available to this API key. */
    fun listModels(apiKey: String): List<String> {
        val conn = URL("$BASE/models").openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20000
            conn.readTimeout = 30000
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("User-Agent", "Proofread/1.0 (Android)")
            val code = conn.responseCode
            if (code == 401) throw RewriteException("Groq rejected the API key. Check that you pasted it correctly.")
            if (code != 200) throw RewriteException("Groq returned error $code while listing models.")
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val data = json.optJSONArray("data") ?: return emptyList()
            val out = ArrayList<String>()
            for (i in 0 until data.length()) out.add(data.getJSONObject(i).optString("id"))
            return out.filter { it.isNotBlank() }.sorted()
        } catch (e: java.net.SocketTimeoutException) {
            throw RewriteException("Timed out while contacting Groq. Check your internet connection.")
        } catch (e: java.net.UnknownHostException) {
            throw RewriteException("Cannot reach Groq. Check your internet connection.")
        } catch (e: java.io.IOException) {
            throw RewriteException("Network problem: " + (e.message ?: "connection failed"))
        } finally {
            conn.disconnect()
        }
    }

    private fun call(text: String, apiKey: String, model: String, system: String): String {
        val payload = JSONObject()
            .put("model", model.trim())
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", text))
            )
            .put("temperature", 0.4)
        if (model.contains("gpt-oss")) payload.put("reasoning_effort", "low")

        var waitingForReply = false
        val conn = URL("$BASE/chat/completions").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.setRequestProperty("User-Agent", "Proofread/1.0 (Android)")
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
                if (code == 404 || errText.contains("model_not_found")) throw ModelNotFound(apiMessage)
                val friendly = when (code) {
                    401 -> "Groq rejected the API key. Check that you pasted it correctly."
                    429 -> "Groq free limit reached. Wait a minute and try again."
                    400 -> "Groq rejected the request. $apiMessage"
                    else -> "Groq returned error $code. $apiMessage"
                }
                throw RewriteException(friendly.trim())
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) throw RewriteException("Groq returned no result.")
            val out = choices.getJSONObject(0).optJSONObject("message")?.optString("content").orEmpty().trim()
            if (out.isEmpty()) throw RewriteException("Groq returned an empty answer.")
            return out
        } catch (e: java.net.SocketTimeoutException) {
            throw RewriteException(
                if (waitingForReply) "Groq took too long to answer. Try again."
                else "Could not connect to Groq (timed out). Check your internet connection or try mobile data instead of Wi-Fi."
            )
        } catch (e: java.net.UnknownHostException) {
            throw RewriteException("Cannot reach Groq. Check your internet connection.")
        } catch (e: java.io.IOException) {
            throw RewriteException("Network problem: " + (e.message ?: "connection failed"))
        } finally {
            conn.disconnect()
        }
    }
}
