package com.proofread.fixer

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A simple keyboard with Fix, Formal and Undo buttons on top.
 * It is basic on purpose: no suggestions, no swipe typing. Use it when you want a fix button right at your fingertips.
 */
class ProofreadKeyboardService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var ui: Ui
    private lateinit var keysBox: LinearLayout
    private lateinit var statusView: TextView

    private var shift = false
    private var symbols = false
    private var undoSource: String? = null
    private var undoResultLength = 0

    private val letterRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    private val symbolRows = listOf("1234567890", "@#$%&-+()", "*\"':;!?")

    private val repeatDelete = object : Runnable {
        override fun run() {
            deleteOne()
            handler.postDelayed(this, 60)
        }
    }

    override fun onCreateInputView(): View {
        ui = Ui(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.col(R.color.bg))
            setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(8))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val barParams = { w: Int, weight: Float ->
            LinearLayout.LayoutParams(w, ui.dp(40), weight).apply { setMargins(ui.dp(2), 0, ui.dp(2), 0) }
        }
        bar.addView(toolButton("Fix") { runAction(Action.FIX) }, barParams(0, 1f))
        bar.addView(toolButton("Formal") { runAction(Action.FORMAL) }, barParams(0, 1f))
        bar.addView(toolButton("Undo") { undo() }, barParams(0, 1f))
        statusView = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 2
            setTextColor(ui.col(R.color.muted))
        }
        bar.addView(statusView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
        bar.addView(toolButton("🌐") { switchKeyboard() }, barParams(ui.dp(48), 0f))
        root.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        keysBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(keysBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        renderKeys()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (::keysBox.isInitialized) {
            symbols = false
            shift = false
            renderKeys()
        }
    }

    // ---------- key layout ----------

    private fun renderKeys() {
        keysBox.removeAllViews()
        val rows = if (symbols) symbolRows else letterRows
        addRow(rows[0].map { charKey(it) })
        addRow(rows[1].map { charKey(it) })

        val third = ArrayList<Pair<View, Float>>()
        if (!symbols) {
            val shiftKey = keyButton(if (shift) "⇧" else "⇧", highlighted = shift) {
                shift = !shift
                renderKeys()
            }
            third.add(Pair(shiftKey, 1.5f))
        }
        third.addAll(rows[2].map { charKey(it) })
        third.add(Pair(backspaceKey(), 1.5f))
        addRow(third)

        val bottom = ArrayList<Pair<View, Float>>()
        bottom.add(Pair(keyButton(if (symbols) "ABC" else "?123") { symbols = !symbols; renderKeys() }, 1.5f))
        bottom.add(Pair(keyButton(",") { typeText(",") }, 1f))
        bottom.add(Pair(keyButton("space") { typeText(" ") }, 4f))
        bottom.add(Pair(keyButton(".") { typeText(".") }, 1f))
        bottom.add(Pair(keyButton("⏎") { pressEnter() }, 1.5f))
        addRow(bottom)
    }

    private fun addRow(items: List<Pair<View, Float>>) {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((v, w) in items) {
            r.addView(v, LinearLayout.LayoutParams(0, ui.dp(46), w).apply {
                setMargins(ui.dp(2), ui.dp(3), ui.dp(2), ui.dp(3))
            })
        }
        keysBox.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun charKey(c: Char): Pair<View, Float> {
        val label = if (!symbols && shift) c.uppercaseChar().toString() else c.toString()
        val key = keyButton(label) {
            typeText(label)
            if (shift && !symbols) {
                shift = false
                renderKeys()
            }
        }
        return Pair(key, 1f)
    }

    private fun backspaceKey(): View {
        val b = keyButton("⌫") { }
        b.setOnClickListener(null)
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    deleteOne()
                    handler.postDelayed(repeatDelete, 400)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                }
            }
            true
        }
        return b
    }

    private fun keyButton(label: String, highlighted: Boolean = false, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = if (label.length > 1) 14f else 19f
            stateListAnimator = null
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(if (highlighted) ui.col(R.color.on_primary) else ui.col(R.color.ink))
            background = ui.ripple(
                ui.roundBg(
                    if (highlighted) ui.col(R.color.primary) else ui.col(R.color.surface),
                    ui.col(R.color.line), 8
                )
            )
            setOnClickListener { onClick() }
        }

    private fun toolButton(label: String, onClick: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        stateListAnimator = null
        setPadding(0, 0, 0, 0)
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setTextColor(ui.col(R.color.on_primary))
        background = ui.ripple(ui.roundBg(ui.col(R.color.primary), null, 8))
        setOnClickListener { onClick() }
    }

    // ---------- typing ----------

    private fun typeText(s: String) {
        currentInputConnection?.commitText(s, 1)
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) ic.commitText("", 1) else ic.deleteSurroundingText(1, 0)
    }

    private fun pressEnter() {
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendDefaultEditorAction(true)
        } else {
            typeText("\n")
        }
    }

    private fun switchKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------- fix / formal / undo ----------

    private fun status(msg: String) {
        if (!::statusView.isInitialized) return
        statusView.text = msg
        handler.removeCallbacks(clearStatus)
        handler.postDelayed(clearStatus, 4000)
    }

    private val clearStatus = Runnable { if (::statusView.isInitialized) statusView.text = "" }

    private fun runAction(action: Action) {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val hasSelection = selected.isNotEmpty()
        val before = if (hasSelection) "" else ic.getTextBeforeCursor(5000, 0)?.toString().orEmpty()
        val after = if (hasSelection) "" else ic.getTextAfterCursor(5000, 0)?.toString().orEmpty()
        val source = if (hasSelection) selected else before + after
        if (source.isBlank()) {
            status("Type some text first")
            return
        }

        val prefs = Cfg.prefs(this)
        val sensitive = Sensitive.scan(source).isNotEmpty()
        if (sensitive && action != Action.FIX) {
            status("Looks sensitive, so it was not sent. Use the app to send it anyway.")
            return
        }
        val allowOnline = !sensitive
        status("Working…")

        Thread {
            val res = runCatching { Engine.run(prefs, action, source, allowOnline = allowOnline) }
            handler.post {
                val out = res.getOrNull()
                if (out == null) {
                    status(res.exceptionOrNull()?.message ?: "Something went wrong.")
                    return@post
                }
                val now = currentInputConnection ?: return@post
                val result = out.variants[0]
                if (!hasSelection) now.deleteSurroundingText(before.length, after.length)
                now.commitText(result, 1)
                undoSource = source
                undoResultLength = result.length
                status(if (sensitive) "Done (offline only: text looked sensitive)" else "Done")
            }
        }.start()
    }

    private fun undo() {
        val original = undoSource
        val ic = currentInputConnection
        if (original == null || ic == null) {
            status("Nothing to undo")
            return
        }
        ic.deleteSurroundingText(undoResultLength, 0)
        ic.commitText(original, 1)
        undoSource = null
        status("Undone")
    }
}
