package com.proofread.fixer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast

/**
 * "Fix grammar" in the text-selection menu of other apps.
 * FormalTextActivity ("Make formal") and MoreTextActivity ("Proofread…", with a list of actions) extend it.
 * In editable fields the result replaces the selection; in read-only text it is copied instead.
 * AI results can be previewed (changes highlighted) before they replace your text.
 */
open class ProcessTextActivity : Activity() {

    /** The action this menu entry runs. Null means "ask the user to choose". */
    protected open val fixedAction: Action?
        get() = Action.FIX

    private lateinit var ui: Ui
    private lateinit var prefs: SharedPreferences
    private var text = ""
    private var readOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        prefs = Cfg.prefs(this)
        text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        if (text.isBlank()) {
            finish()
            return
        }
        val fixed = fixedAction
        if (fixed != null) start(fixed)
        else ui.pickAction(Action.values().toList(), { start(it) }, { cancel() })
    }

    private fun start(action: Action) {
        Toast.makeText(this, if (action == Action.FIX) "Fixing…" else "Working…", Toast.LENGTH_SHORT).show()
        Pipeline.execute(
            this, ui, prefs, action, text,
            onResult = { out ->
                val result = out.variants[0]
                if (out.note != null) Toast.makeText(this, out.note, Toast.LENGTH_LONG).show()
                if (action.usesAi && Cfg.previewAi(prefs)) preview(action, result) else deliver(result)
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                cancel()
            },
            onCancel = { cancel() }
        )
    }

    private fun preview(action: Action, result: String) {
        val buttons = ArrayList<DialogButton>()
        buttons.add(DialogButton(if (readOnly) "Copy" else "Replace", true) { deliver(result) })
        if (!readOnly) buttons.add(DialogButton("Copy only", false) { copyOnly(result) })
        buttons.add(DialogButton("Cancel", false) { cancel() })
        ui.showDialog(action.label, Diff.highlight(text, result, ui.diffColor()), buttons) { cancel() }
    }

    private fun copy(result: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Proofread", result))
    }

    private fun copyOnly(result: String) {
        copy(result)
        Toast.makeText(this, "Copied. Your original text was not changed.", Toast.LENGTH_LONG).show()
        cancel()
    }

    private fun deliver(result: String) {
        if (readOnly) {
            copy(result)
            Toast.makeText(this, "Done. The new text is copied. Paste it where you need it.", Toast.LENGTH_LONG).show()
            setResult(RESULT_CANCELED)
        } else {
            setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result))
        }
        finish()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
