package com.proofread.fixer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

/**
 * Fixes whatever is on the clipboard and puts the result back on it.
 * Started by the Quick Settings tile and the app shortcut. It must be on screen to read the clipboard,
 * so it is a see-through activity that closes itself.
 */
class ClipboardFixActivity : Activity() {

    private var started = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || started) return
        started = true

        val ui = Ui(this)
        val prefs = Cfg.prefs(this)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        } else {
            ""
        }
        if (text.isBlank()) {
            Toast.makeText(this, "Copy some text first, then try again.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val action = Cfg.quickAction(prefs)
        Toast.makeText(this, action.label + "…", Toast.LENGTH_SHORT).show()
        Pipeline.execute(
            this, ui, prefs, action, text,
            onResult = { out ->
                cm.setPrimaryClip(ClipData.newPlainText("Proofread", out.variants[0]))
                Toast.makeText(this, "Done. The new text is on your clipboard.", Toast.LENGTH_LONG).show()
                finish()
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                finish()
            },
            onCancel = { finish() }
        )
    }
}
