package com.proofread.fixer

import android.content.SharedPreferences

class RewriteException(message: String) : Exception(message)

/** What every action returns. */
data class Output(val variants: List<String>, val changes: String?, val note: String?)

/** Runs an AI action on the preferred provider and falls back to the other one if it fails. */
object Rewriter {

    fun run(prefs: SharedPreferences, action: Action, text: String, options: Boolean, explain: Boolean): Output {
        val system = Prompts.build(action, prefs, options, explain)
        val primary = Cfg.provider(prefs)
        val order = if (primary == "Gemini") listOf("Gemini", "Groq") else listOf("Groq", "Gemini")

        var firstError: RewriteException? = null
        for (name in order) {
            val hasKey = if (name == "Gemini") GeminiRewriter.hasKey(prefs) else GroqRewriter.hasKey(prefs)
            if (!hasKey) continue
            try {
                val raw = if (name == "Gemini") GeminiRewriter.complete(prefs, system, text)
                else GroqRewriter.complete(prefs, system, text)
                val note = if (firstError != null) "Used $name because $primary was unavailable." else null
                return parse(raw, options, note)
            } catch (e: RewriteException) {
                if (firstError == null) firstError = e
            }
        }
        throw firstError ?: RewriteException("No API key found. Open the Proofread app and add a free Groq or Gemini key.")
    }

    private fun parse(raw: String, options: Boolean, note: String?): Output {
        var body = raw.trim()
        var changes: String? = null
        val idx = body.indexOf(Prompts.CHANGES_MARK)
        if (idx >= 0) {
            changes = body.substring(idx + Prompts.CHANGES_MARK.length).trim().ifEmpty { null }
            body = body.substring(0, idx).trim()
        }
        val variants = if (options) {
            body.split(Regex("(?m)^\\s*" + Regex.escape(Prompts.SEPARATOR) + "\\s*$"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } else {
            listOf(body)
        }
        if (variants.isEmpty() || variants[0].isEmpty()) throw RewriteException("The AI returned an empty answer.")
        return Output(variants.take(3), changes, note)
    }
}
