package com.proofread.fixer

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** The last few results, saved only on this phone. */
object HistoryStore {
    data class Entry(val time: Long, val action: String, val input: String, val output: String)

    private const val KEY = "history"
    private const val MAX = 20
    private const val MAX_CHARS = 4000

    fun load(p: SharedPreferences): List<Entry> = try {
        val arr = JSONArray(p.getString(KEY, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.optLong("t"), o.optString("a"), o.optString("i"), o.optString("o"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(p: SharedPreferences, action: String, input: String, output: String) {
        if (!Cfg.saveHistory(p)) return
        val entry = Entry(System.currentTimeMillis(), action, input.take(MAX_CHARS), output.take(MAX_CHARS))
        val list = (listOf(entry) + load(p)).take(MAX)
        val arr = JSONArray()
        for (e in list) {
            arr.put(JSONObject().put("t", e.time).put("a", e.action).put("i", e.input).put("o", e.output))
        }
        p.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(p: SharedPreferences) {
        p.edit().remove(KEY).apply()
    }
}
