package com.proofread.fixer

import android.app.Activity
import android.content.SharedPreferences

/**
 * Runs an action safely: asks first if the text looks sensitive, works off the main thread,
 * and reports back on the main thread. Exactly one callback is always called.
 */
object Pipeline {

    fun execute(
        activity: Activity,
        ui: Ui,
        prefs: SharedPreferences,
        action: Action,
        text: String,
        options: Boolean = false,
        explain: Boolean = false,
        onResult: (Output) -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (Engine.needsOnline(prefs, action)) {
            val findings = Sensitive.scan(text)
            if (findings.isNotEmpty()) {
                ui.confirmSensitive(
                    findings,
                    action == Action.FIX,
                    onSend = { launch(activity, prefs, action, text, options, explain, true, onResult, onError) },
                    onOffline = { launch(activity, prefs, action, text, options, explain, false, onResult, onError) },
                    onCancel = onCancel
                )
                return
            }
        }
        launch(activity, prefs, action, text, options, explain, true, onResult, onError)
    }

    private fun launch(
        activity: Activity,
        prefs: SharedPreferences,
        action: Action,
        text: String,
        options: Boolean,
        explain: Boolean,
        allowOnline: Boolean,
        onResult: (Output) -> Unit,
        onError: (String) -> Unit
    ) {
        Thread {
            val res = runCatching { Engine.run(prefs, action, text, options, explain, allowOnline) }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                val out = res.getOrNull()
                if (out != null) onResult(out)
                else onError(res.exceptionOrNull()?.message ?: "Something went wrong.")
            }
        }.start()
    }
}
