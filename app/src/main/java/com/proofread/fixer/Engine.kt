package com.proofread.fixer

import android.content.SharedPreferences

/** One entry point for every action, used by the app, the selection menu, the tile and the keyboard. */
object Engine {

    /** True if running this action would send text over the internet. */
    fun needsOnline(p: SharedPreferences, action: Action): Boolean = action.usesAi || Cfg.online(p)

    fun run(
        p: SharedPreferences,
        action: Action,
        text: String,
        options: Boolean = false,
        explain: Boolean = false,
        allowOnline: Boolean = true
    ): Output {
        val out: Output
        if (action == Action.FIX) {
            val fixed = GrammarFixer.fix(text, Cfg.online(p) && allowOnline, Cfg.langCode(p), Cfg.protectedTerms(p))
            out = Output(listOf(fixed), null, null)
        } else {
            if (!allowOnline) throw RewriteException("This action needs an internet connection.")
            out = Rewriter.run(p, action, text, options, explain)
        }
        HistoryStore.add(p, action.label, text, out.variants[0])
        return out
    }
}
