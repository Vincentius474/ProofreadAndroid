package com.proofread.fixer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val styles = arrayOf("Formal", "Professional", "Simple")
    private val spellings = arrayOf("South African", "British", "American")
    private val providers = arrayOf("Groq", "Gemini")
    private val voiceRequest = 4711
    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var ui: Ui
    private lateinit var prefs: SharedPreferences
    private lateinit var input: EditText

    private fun spinner(items: Array<String>, selected: String?): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(items.indexOf(selected).coerceAtLeast(0))
    }

    private fun checkBox(label: String, checked: Boolean): CheckBox = CheckBox(this).apply {
        text = label
        textSize = 15f
        isChecked = checked
        setTextColor(ui.col(R.color.ink))
    }

    private fun copyToClipboard(text: CharSequence) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Proofread", text))
        Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
    }

    /** A card section that opens and closes when its title is tapped. */
    private fun collapsible(card: LinearLayout, title: String, startOpen: Boolean, content: View, onOpen: (() -> Unit)? = null) {
        val header = TextView(this).apply {
            textSize = 13f
            letterSpacing = 0.06f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ui.col(R.color.muted))
            setPadding(0, ui.dp(2), 0, ui.dp(2))
        }
        fun label(open: Boolean): String = title.uppercase() + (if (open) "  ▴" else "  ▾")
        header.text = label(startOpen)
        content.visibility = if (startOpen) View.VISIBLE else View.GONE
        header.setOnClickListener {
            val open = content.visibility != View.VISIBLE
            content.visibility = if (open) View.VISIBLE else View.GONE
            header.text = label(open)
            if (open) onOpen?.invoke()
        }
        card.addView(header)
        card.addView(content)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        prefs = Cfg.prefs(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(ui.col(R.color.bg))
            isFillViewport = true
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root, ViewGroup.LayoutParams(matchParent, wrap))

        // ---------- header ----------
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ui.col(R.color.navy))
            setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(22))
        }
        header.addView(TextView(this).apply {
            text = "Proofread"
            textSize = 30f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        })
        header.addView(View(this).apply {
            setBackgroundColor(ui.col(R.color.accent))
            layoutParams = LinearLayout.LayoutParams(ui.dp(44), ui.dp(3)).apply {
                topMargin = ui.dp(6)
                bottomMargin = ui.dp(8)
            }
        })
        header.addView(TextView(this).apply {
            text = "Clean grammar. Formal English."
            textSize = 15f
            setTextColor(0xFFB8C2D9.toInt())
        })
        root.addView(header, LinearLayout.LayoutParams(matchParent, wrap))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(28))
        }
        root.addView(body, LinearLayout.LayoutParams(matchParent, wrap))

        // ---------- card: your text ----------
        val writeCard = ui.card()
        writeCard.addView(ui.sectionTitle("Your text"))
        input = EditText(this).apply {
            hint = "Type, paste or dictate your text here"
            minLines = 5
            gravity = Gravity.TOP
            textSize = 18f
            typeface = Typeface.SERIF
            setLineSpacing(0f, 1.15f)
            setTextColor(ui.col(R.color.ink))
            setHintTextColor(ui.col(R.color.muted))
            background = ui.roundBg(ui.col(R.color.field), ui.col(R.color.line), 10)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
        }
        writeCard.addView(input, ui.params())

        val dictateButton = ui.outlineButton("🎤 Dictate")
        val clearButton = ui.outlineButton("Clear")
        val utilRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        utilRow.addView(dictateButton, LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = ui.dp(6) })
        utilRow.addView(clearButton, LinearLayout.LayoutParams(0, wrap, 1f).apply { leftMargin = ui.dp(6) })
        writeCard.addView(utilRow, ui.params(top = 10))

        writeCard.addView(ui.smallLabel("What should I do?"))
        val actionSpinner = spinner(
            Action.values().map { it.label }.toTypedArray(),
            Action.fromName(prefs.getString("last_action", "FORMAL")).label
        )
        writeCard.addView(actionSpinner)
        val optionsBox = checkBox("Give me 3 options (AI actions)", prefs.getBoolean("opt_options", false))
        val explainBox = checkBox("Explain what changed (AI actions)", prefs.getBoolean("opt_explain", false))
        writeCard.addView(optionsBox)
        writeCard.addView(explainBox)
        val runButton = ui.primaryButton("Run")
        writeCard.addView(runButton, ui.params(top = 10))
        body.addView(writeCard)

        // ---------- card: result ----------
        val resultCard = ui.card().apply { visibility = View.GONE }
        resultCard.addView(ui.sectionTitle("Result"))
        val resultBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultCard.addView(resultBody, ui.params())
        body.addView(resultCard)

        // ---------- result helpers ----------
        fun showMessage(msg: String, isError: Boolean) {
            resultCard.visibility = View.VISIBLE
            resultBody.removeAllViews()
            resultBody.addView(TextView(this).apply {
                text = msg
                textSize = 16f
                setLineSpacing(0f, 1.15f)
                setTextColor(if (isError) ui.col(R.color.accent) else ui.col(R.color.ink))
            })
        }

        fun variantView(original: String, variant: String, label: String?): View {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            if (label != null) box.addView(ui.smallLabel(label))
            val highlighted = Diff.highlight(original, variant, ui.diffColor())
            val tv = TextView(this).apply {
                textSize = 18f
                typeface = Typeface.SERIF
                setLineSpacing(0f, 1.2f)
                setTextColor(ui.col(R.color.ink))
                setTextIsSelectable(true)
                text = highlighted
            }
            box.addView(tv, ui.params())

            val copyBtn = ui.outlineButton("Copy")
            val editBtn = ui.outlineButton("Edit")
            val compareBtn = ui.outlineButton("Original")
            for (b in listOf(copyBtn, editBtn, compareBtn)) b.textSize = 14f
            var showingNew = true
            copyBtn.setOnClickListener { copyToClipboard(variant) }
            editBtn.setOnClickListener {
                input.setText(variant)
                input.setSelection(input.text.length)
                scroll.smoothScrollTo(0, 0)
            }
            compareBtn.setOnClickListener {
                showingNew = !showingNew
                tv.text = if (showingNew) highlighted else original
                compareBtn.text = if (showingNew) "Original" else "Changes"
            }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val gap = ui.dp(4)
            row.addView(copyBtn, LinearLayout.LayoutParams(0, wrap, 1f).apply { rightMargin = gap })
            row.addView(editBtn, LinearLayout.LayoutParams(0, wrap, 1f).apply { leftMargin = gap; rightMargin = gap })
            row.addView(compareBtn, LinearLayout.LayoutParams(0, wrap, 1f).apply { leftMargin = gap })
            box.addView(row, ui.params(top = 8))
            return box
        }

        fun showOutput(original: String, out: Output) {
            resultCard.visibility = View.VISIBLE
            resultBody.removeAllViews()
            out.variants.forEachIndexed { i, v ->
                resultBody.addView(
                    variantView(original, v, if (out.variants.size > 1) "Option ${i + 1}" else null),
                    ui.params(top = if (i == 0) 0 else 16)
                )
            }
            val changes = out.changes
            if (changes != null) {
                resultBody.addView(ui.smallLabel("Why these changes"))
                resultBody.addView(ui.bodyText(changes))
            }
            val note = out.note
            if (note != null) resultBody.addView(ui.smallLabel(note))
        }

        // ---------- card: history ----------
        val historyCard = ui.card()
        val historyList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun refreshHistory() {
            historyList.removeAllViews()
            val items = HistoryStore.load(prefs)
            if (items.isEmpty()) {
                historyList.addView(ui.bodyText("Nothing here yet. Your last 20 results appear here, saved only on this phone."))
                return
            }
            val fmt = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
            for (e in items) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, ui.dp(8), 0, ui.dp(8))
                    isClickable = true
                }
                row.addView(TextView(this).apply {
                    text = e.action + " · " + fmt.format(Date(e.time))
                    textSize = 12f
                    setTextColor(ui.col(R.color.muted))
                })
                row.addView(TextView(this).apply {
                    text = e.output.replace('\n', ' ').take(90)
                    textSize = 15f
                    setTextColor(ui.col(R.color.ink))
                })
                row.setOnClickListener {
                    input.setText(e.input)
                    showOutput(e.input, Output(listOf(e.output), null, null))
                    scroll.smoothScrollTo(0, 0)
                }
                historyList.addView(row, ui.params())
                historyList.addView(View(this).apply { setBackgroundColor(ui.col(R.color.line)) },
                    LinearLayout.LayoutParams(matchParent, ui.dp(1)))
            }
            val clearHistory = ui.outlineButton("Clear history")
            clearHistory.setOnClickListener {
                HistoryStore.clear(prefs)
                refreshHistory()
            }
            historyList.addView(clearHistory, ui.params(top = 12))
        }
        refreshHistory()
        collapsible(historyCard, "History", false, historyList) { refreshHistory() }
        body.addView(historyCard)

        // ---------- card: how to use ----------
        val howCard = ui.card()
        howCard.addView(ui.sectionTitle("Use it everywhere"))
        howCard.addView(ui.bodyText(
            "• Select text in any app, then tap Fix grammar, Make formal or Proofread… in the menu (look under ⋮ if needed).\n" +
                "• Share text to Proofread from any app using the Share button.\n" +
                "• Copy text, then use the \"Fix clipboard\" tile in your Quick Settings (swipe down, tap the pencil, drag the tile in), or long-press the app icon.\n" +
                "• Switch to the Proofread keyboard for Fix, Formal and Undo buttons while you type."
        ))
        body.addView(howCard)

        // ---------- card: settings ----------
        val settingsCard = ui.card()
        val firstRun = SecureStore.get(prefs, "groq_key").isBlank() &&
            SecureStore.get(prefs, "api_key").isBlank() &&
            BuildConfig.GROQ_API_KEY.isBlank() && BuildConfig.GEMINI_API_KEY.isBlank()
        val settingsBody = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val onlineSwitch = Switch(this).apply {
            text = "Online grammar check for \"Fix grammar\""
            textSize = 15f
            setTextColor(ui.col(R.color.ink))
            isChecked = Cfg.online(prefs)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("online", c).apply() }
            setPadding(0, ui.dp(12), 0, ui.dp(4))
        }
        val previewSwitch = Switch(this).apply {
            text = "Preview AI rewrites before replacing text"
            textSize = 15f
            setTextColor(ui.col(R.color.ink))
            isChecked = Cfg.previewAi(prefs)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("preview_ai", c).apply() }
            setPadding(0, ui.dp(8), 0, ui.dp(4))
        }
        val historySwitch = Switch(this).apply {
            text = "Save history on this phone"
            textSize = 15f
            setTextColor(ui.col(R.color.ink))
            isChecked = Cfg.saveHistory(prefs)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("save_history", c).apply() }
            setPadding(0, ui.dp(8), 0, ui.dp(4))
        }

        val providerSpinner = spinner(providers, Cfg.provider(prefs))
        val groqKeyBox = ui.field("Groq API key", SecureStore.get(prefs, "groq_key"), true)
        val groqModelBox = ui.field("Groq model", prefs.getString("groq_model", GroqRewriter.DEFAULT_MODEL).orEmpty(), false)
        val geminiKeyBox = ui.field("Gemini API key", SecureStore.get(prefs, "api_key"), true)
        val geminiModelBox = ui.field("Gemini model", prefs.getString("model", GeminiRewriter.DEFAULT_MODEL).orEmpty(), false)
        val styleSpinner = spinner(styles, Cfg.style(prefs))
        val spellingSpinner = spinner(spellings, Cfg.spelling(prefs))
        val rulesBox = ui.field("My rules, e.g. keep sentences short", Cfg.rules(prefs), false).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            gravity = Gravity.TOP
        }
        val protectedBox = ui.field("Words to never change (names, brands), separated by commas",
            prefs.getString("protected", "").orEmpty(), false).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            gravity = Gravity.TOP
        }
        val quickSpinner = spinner(Action.values().map { it.label }.toTypedArray(), Cfg.quickAction(prefs).label)
        val saveButton = ui.primaryButton("Save settings")
        val modelsButton = ui.outlineButton("Show available Groq models")
        val keyboardButton = ui.outlineButton("Set up the Proofread keyboard")

        settingsBody.addView(onlineSwitch)
        settingsBody.addView(previewSwitch)
        settingsBody.addView(historySwitch)
        settingsBody.addView(ui.smallLabel("AI provider (the other one is used automatically if it fails)"))
        settingsBody.addView(providerSpinner)
        settingsBody.addView(groqKeyBox)
        settingsBody.addView(groqModelBox)
        settingsBody.addView(geminiKeyBox)
        settingsBody.addView(geminiModelBox)
        settingsBody.addView(TextView(this).apply {
            text = "Groq keys: console.groq.com. Gemini keys: aistudio.google.com. Keys are stored encrypted on this phone. Your text is sent to the provider when you use an AI action. Free tiers may log it, so avoid sensitive text."
            textSize = 13f
            setTextColor(ui.col(R.color.muted))
            setPadding(0, ui.dp(8), 0, 0)
        })
        settingsBody.addView(ui.smallLabel("Tone for \"Make formal\" and emails"))
        settingsBody.addView(styleSpinner)
        settingsBody.addView(ui.smallLabel("English spelling"))
        settingsBody.addView(spellingSpinner)
        settingsBody.addView(ui.smallLabel("Your own rules"))
        settingsBody.addView(rulesBox)
        settingsBody.addView(ui.smallLabel("Protected words"))
        settingsBody.addView(protectedBox)
        settingsBody.addView(ui.smallLabel("Quick action for the clipboard tile and shortcut"))
        settingsBody.addView(quickSpinner)
        settingsBody.addView(saveButton, ui.params(top = 14))
        settingsBody.addView(modelsButton, ui.params(top = 8))
        settingsBody.addView(keyboardButton, ui.params(top = 8))
        collapsible(settingsCard, "Settings", firstRun, settingsBody)
        body.addView(settingsCard)

        // ---------- behaviour ----------
        fun saveSettings() {
            SecureStore.put(prefs, "groq_key", groqKeyBox.text.toString().trim())
            SecureStore.put(prefs, "api_key", geminiKeyBox.text.toString().trim())
            prefs.edit()
                .putString("provider", providers[providerSpinner.selectedItemPosition])
                .putString("groq_model", groqModelBox.text.toString().trim().ifBlank { GroqRewriter.DEFAULT_MODEL })
                .putString("model", geminiModelBox.text.toString().trim().ifBlank { GeminiRewriter.DEFAULT_MODEL })
                .putString("style", styles[styleSpinner.selectedItemPosition])
                .putString("spelling", spellings[spellingSpinner.selectedItemPosition])
                .putString("rules", rulesBox.text.toString().trim())
                .putString("protected", protectedBox.text.toString().trim())
                .putString("quick_action", Action.values()[quickSpinner.selectedItemPosition].name)
                .putString("last_action", Action.values()[actionSpinner.selectedItemPosition].name)
                .putBoolean("opt_options", optionsBox.isChecked)
                .putBoolean("opt_explain", explainBox.isChecked)
                .apply()
        }

        saveButton.setOnClickListener {
            saveSettings()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }

        fun runSelected() {
            val text = input.text.toString()
            if (text.isBlank()) {
                Toast.makeText(this, "Type some text first", Toast.LENGTH_SHORT).show()
                return
            }
            saveSettings()
            val action = Action.values()[actionSpinner.selectedItemPosition]
            val options = optionsBox.isChecked && action.usesAi
            val explain = explainBox.isChecked && action.usesAi
            runButton.isEnabled = false
            showMessage("Working…", false)
            Pipeline.execute(
                this, ui, prefs, action, text, options, explain,
                onResult = { out ->
                    runButton.isEnabled = true
                    showOutput(text, out)
                    refreshHistory()
                },
                onError = { msg ->
                    runButton.isEnabled = true
                    showMessage(msg, true)
                },
                onCancel = {
                    runButton.isEnabled = true
                    resultCard.visibility = View.GONE
                }
            )
        }
        runButton.setOnClickListener { runSelected() }

        clearButton.setOnClickListener {
            input.setText("")
            resultCard.visibility = View.GONE
        }
        dictateButton.setOnClickListener { dictate() }

        modelsButton.setOnClickListener {
            saveSettings()
            val key = GroqRewriter.keyFrom(prefs)
            if (key.isEmpty()) {
                showMessage("Paste your Groq API key first.", true)
                return@setOnClickListener
            }
            showMessage("Asking Groq…", false)
            Thread {
                val msg = try {
                    "Models available to your key (type one in the Groq model box):\n\n" +
                        GroqRewriter.listModels(key).joinToString("\n")
                } catch (e: Exception) {
                    e.message ?: "Something went wrong."
                }
                runOnUiThread { showMessage(msg, false) }
            }.start()
        }

        keyboardButton.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                Toast.makeText(this, "Turn on \"Proofread keyboard\", then pick it from the keyboard switcher.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Open Settings > System > Keyboard to enable it.", Toast.LENGTH_LONG).show()
            }
        }

        setContentView(scroll)
        handleIntent(intent)
    }

    // ---------- share-sheet and voice ----------

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        if (i.action == Intent.ACTION_SEND && i.type?.startsWith("text/") == true) {
            val shared = i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!shared.isNullOrBlank()) {
                input.setText(shared)
                input.setSelection(input.text.length)
                Toast.makeText(this, "Text received. Choose what to do with it.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun dictate() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Cfg.langCode(prefs))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        try {
            startActivityForResult(i, voiceRequest)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input isn't available on this phone.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == voiceRequest && resultCode == RESULT_OK) {
            val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull().orEmpty()
            if (said.isNotBlank()) {
                val current = input.text.toString()
                input.setText(if (current.isBlank()) said else current.trimEnd() + " " + said)
                input.setSelection(input.text.length)
            }
        }
    }
}
