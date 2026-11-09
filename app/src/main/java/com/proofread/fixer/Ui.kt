package com.proofread.fixer

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class DialogButton(val label: String, val primary: Boolean, val onClick: () -> Unit)

private class MaxHeightScroll(context: Context, private val maxPx: Int) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxPx, View.MeasureSpec.AT_MOST))
    }
}

/** Shared look and feel: cards, buttons, fields and dialogs. */
class Ui(private val ctx: Context) {
    private val matchParent = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()
    fun col(id: Int): Int = ctx.getColor(id)

    /** Soft highlight used for changed words. */
    fun diffColor(): Int = (col(R.color.accent) and 0x00FFFFFF) or 0x55000000

    fun roundBg(fill: Int, stroke: Int?, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    fun ripple(content: GradientDrawable): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(0x33808080), content, null)

    fun params(top: Int = 0, width: Int = matchParent): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, wrap).apply { topMargin = dp(top) }

    fun card(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = roundBg(col(R.color.surface), col(R.color.line), 12)
        setPadding(dp(16), dp(14), dp(16), dp(16))
        layoutParams = params(top = 14)
    }

    fun sectionTitle(t: String): TextView = TextView(ctx).apply {
        text = t
        textSize = 13f
        letterSpacing = 0.06f
        isAllCaps = true
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(col(R.color.muted))
        setPadding(0, 0, 0, dp(8))
    }

    fun smallLabel(t: String): TextView = TextView(ctx).apply {
        text = t
        textSize = 13f
        setTextColor(col(R.color.muted))
        setPadding(0, dp(12), 0, dp(4))
    }

    fun bodyText(t: String): TextView = TextView(ctx).apply {
        text = t
        textSize = 15f
        setLineSpacing(0f, 1.15f)
        setTextColor(col(R.color.ink))
    }

    fun primaryButton(label: String): Button = Button(ctx).apply {
        this.text = label
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        setTextColor(col(R.color.on_primary))
        background = ripple(roundBg(col(R.color.primary), null, 10))
        minHeight = dp(48)
        minimumHeight = dp(48)
    }

    fun outlineButton(label: String): Button = Button(ctx).apply {
        this.text = label
        isAllCaps = false
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        setTextColor(col(R.color.primary))
        background = ripple(roundBg(col(R.color.surface), col(R.color.muted), 10))
        minHeight = dp(48)
        minimumHeight = dp(48)
    }

    fun field(hintText: String, value: String, secret: Boolean): EditText = EditText(ctx).apply {
        hint = hintText
        inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        else InputType.TYPE_CLASS_TEXT
        setText(value)
        textSize = 16f
        setTextColor(col(R.color.ink))
        setHintTextColor(col(R.color.muted))
        background = roundBg(col(R.color.field), col(R.color.line), 10)
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = params(top = 8)
    }

    // ---------- dialogs ----------

    fun showDialog(title: String, message: CharSequence?, buttons: List<DialogButton>, onCancel: () -> Unit): Dialog {
        val d = Dialog(ctx)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = roundBg(col(R.color.surface), col(R.color.line), 16)
            setPadding(dp(20), dp(18), dp(20), dp(16))
        }
        root.addView(TextView(ctx).apply {
            text = title
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setTextColor(col(R.color.ink))
        })
        if (message != null) {
            val tv = TextView(ctx).apply {
                text = message
                textSize = 17f
                typeface = Typeface.SERIF
                setLineSpacing(0f, 1.2f)
                setTextColor(col(R.color.ink))
                setTextIsSelectable(true)
                setPadding(0, dp(12), 0, dp(4))
            }
            val scroll = MaxHeightScroll(ctx, (ctx.resources.displayMetrics.heightPixels * 0.5f).toInt())
            scroll.addView(tv)
            root.addView(scroll, params())
        }
        for (b in buttons) {
            val btn = if (b.primary) primaryButton(b.label) else outlineButton(b.label)
            btn.setOnClickListener {
                d.dismiss()
                b.onClick()
            }
            root.addView(btn, params(top = 10))
        }
        d.setContentView(root)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout((ctx.resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        d.setOnCancelListener { onCancel() }
        d.show()
        return d
    }

    fun pickAction(actions: List<Action>, onPick: (Action) -> Unit, onCancel: () -> Unit) {
        val buttons = actions.map { a -> DialogButton(a.label, a == Action.FORMAL) { onPick(a) } } +
            DialogButton("Cancel", false, onCancel)
        showDialog("Choose an action", null, buttons, onCancel)
    }

    fun confirmSensitive(
        findings: List<String>,
        canGoOffline: Boolean,
        onSend: () -> Unit,
        onOffline: () -> Unit,
        onCancel: () -> Unit
    ) {
        val msg = "This text may contain ${findings.joinToString(", ")}. " +
            "Sending it online shares it with the grammar or AI service you chose."
        val buttons = ArrayList<DialogButton>()
        if (canGoOffline) {
            buttons.add(DialogButton("Fix offline only", true, onOffline))
            buttons.add(DialogButton("Send anyway", false, onSend))
            buttons.add(DialogButton("Cancel", false, onCancel))
        } else {
            buttons.add(DialogButton("Cancel", true, onCancel))
            buttons.add(DialogButton("Send anyway", false, onSend))
        }
        showDialog("Looks sensitive", msg, buttons, onCancel)
    }
}
