package com.proofread.fixer

import android.content.Context
import android.content.SharedPreferences

/** Typed access to the app's saved settings. */
object Cfg {
    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("proofread", Context.MODE_PRIVATE)

    fun online(p: SharedPreferences): Boolean = p.getBoolean("online", true)
    fun provider(p: SharedPreferences): String = p.getString("provider", "Groq") ?: "Groq"
    fun style(p: SharedPreferences): String = p.getString("style", "Formal") ?: "Formal"
    fun spelling(p: SharedPreferences): String = p.getString("spelling", "South African") ?: "South African"
    fun langCode(p: SharedPreferences): String = when (spelling(p)) {
        "British" -> "en-GB"
        "American" -> "en-US"
        else -> "en-ZA"
    }
    fun rules(p: SharedPreferences): String = p.getString("rules", "").orEmpty().trim()
    fun protectedTerms(p: SharedPreferences): List<String> =
        p.getString("protected", "").orEmpty().split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    fun previewAi(p: SharedPreferences): Boolean = p.getBoolean("preview_ai", true)
    fun saveHistory(p: SharedPreferences): Boolean = p.getBoolean("save_history", true)
    fun quickAction(p: SharedPreferences): Action = Action.fromName(p.getString("quick_action", "FIX"))
}
