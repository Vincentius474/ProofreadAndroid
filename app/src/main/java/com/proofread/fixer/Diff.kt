package com.proofread.fixer

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan

/** Word-level comparison that highlights what changed in the new text. */
object Diff {
    fun highlight(before: String, after: String, color: Int): CharSequence {
        val sb = SpannableStringBuilder()
        val a = before.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tokens = Regex("\\s+|\\S+").findAll(after).map { it.value }.toList()
        val b = tokens.filter { !it[0].isWhitespace() }
        if (a.size.toLong() * b.size > 4_000_000L) {
            sb.append(after)
            return sb
        }
        val n = a.size
        val m = b.size
        val w = m + 1
        val dp = IntArray((n + 1) * w)
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i * w + j] = if (a[i] == b[j]) dp[(i + 1) * w + j + 1] + 1
                else maxOf(dp[(i + 1) * w + j], dp[i * w + j + 1])
            }
        }
        val keep = BooleanArray(m)
        var x = 0
        var y = 0
        while (x < n && y < m) {
            if (a[x] == b[y]) {
                keep[y] = true; x++; y++
            } else if (dp[(x + 1) * w + y] >= dp[x * w + y + 1]) {
                x++
            } else {
                y++
            }
        }
        var k = 0
        for (tok in tokens) {
            if (tok[0].isWhitespace()) {
                sb.append(tok)
                continue
            }
            val start = sb.length
            sb.append(tok)
            if (!keep[k]) {
                sb.setSpan(BackgroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            k++
        }
        return sb
    }

    @Suppress("unused")
    private val unused = Color.TRANSPARENT
}
