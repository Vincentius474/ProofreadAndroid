package com.proofread.fixer

/** Looks for text that probably should not be sent to an online service. */
object Sensitive {
    private val saId = Regex("(?<!\\d)\\d{13}(?!\\d)")
    private val cardLike = Regex("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)")
    private val passwordWord = Regex("(?i)\\b(password|passcode)\\b\\s*(?:is|=|:|-)?\\s*\\S+")
    private val pinWord = Regex("(?i)\\b(pin|cvv|otp)\\b\\s*(?:is|=|:|-)?\\s*\\d{3,8}\\b")
    private val accountNo = Regex("(?i)\\baccount\\s*(?:no\\.?|number|#)?\\s*[:\\-]?\\s*\\d{8,12}\\b")
    private val apiKey = Regex("\\b(gsk_[A-Za-z0-9]{20,}|AIza[0-9A-Za-z_\\-]{30,}|sk-[A-Za-z0-9_\\-]{20,})\\b")

    fun scan(text: String): List<String> {
        val found = LinkedHashSet<String>()
        if (apiKey.containsMatchIn(text)) found.add("an API key")
        if (passwordWord.containsMatchIn(text)) found.add("a password")
        if (pinWord.containsMatchIn(text)) found.add("a PIN or security code")
        if (accountNo.containsMatchIn(text)) found.add("a bank account number")

        val ids = HashSet<String>()
        for (m in saId.findAll(text)) {
            if (looksLikeSaId(m.value)) {
                ids.add(m.value)
                found.add("an ID number")
            }
        }
        for (m in cardLike.findAll(text)) {
            val digits = m.value.filter { it.isDigit() }
            if (digits in ids) continue
            if (digits.length in 13..19 && luhn(digits)) {
                found.add("a card number")
                break
            }
        }
        return found.toList()
    }

    private fun looksLikeSaId(d: String): Boolean {
        val month = d.substring(2, 4).toIntOrNull() ?: return false
        val day = d.substring(4, 6).toIntOrNull() ?: return false
        return month in 1..12 && day in 1..31
    }

    private fun luhn(digits: String): Boolean {
        var sum = 0
        var alt = false
        for (i in digits.length - 1 downTo 0) {
            var n = digits[i] - '0'
            if (alt) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alt = !alt
        }
        return sum % 10 == 0
    }
}
