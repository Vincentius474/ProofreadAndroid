package com.proofread.fixer

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Same rules as the web version. Call fix() from a background thread (it may use the network). */
object GrammarFixer {

    private val contractions = mapOf(
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "doesnt" to "doesn't", "didnt" to "didn't",
        "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't", "couldnt" to "couldn't",
        "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "thats" to "that's", "whats" to "what's",
        "theres" to "there's", "heres" to "here's", "youre" to "you're", "theyre" to "they're",
        "weve" to "we've", "youve" to "you've", "theyve" to "they've", "couldve" to "could've",
        "wouldve" to "would've", "shouldve" to "should've", "im" to "I'm", "ive" to "I've"
    )

    private val typos = mapOf(
        "teh" to "the", "recieve" to "receive", "recieved" to "received", "definately" to "definitely",
        "definitly" to "definitely", "seperate" to "separate", "alot" to "a lot", "wich" to "which",
        "becuase" to "because", "beacuse" to "because", "adress" to "address", "occured" to "occurred",
        "untill" to "until", "tommorow" to "tomorrow", "tomorow" to "tomorrow", "thier" to "their",
        "freind" to "friend", "wierd" to "weird", "accomodate" to "accommodate", "goverment" to "government",
        "enviroment" to "environment", "realy" to "really", "importent" to "important", "wright" to "write",
        "writting" to "writing", "beleive" to "believe", "buisness" to "business", "calender" to "calendar",
        "diffrent" to "different", "excellant" to "excellent", "finaly" to "finally", "grammer" to "grammar",
        "happend" to "happened", "immediatly" to "immediately", "knowlege" to "knowledge",
        "neccessary" to "necessary", "necesary" to "necessary", "occassion" to "occasion",
        "posible" to "possible", "prefered" to "preferred", "probly" to "probably", "refered" to "referred",
        "relevent" to "relevant", "succesful" to "successful", "suprise" to "surprise", "truely" to "truly",
        "wensday" to "Wednesday", "noone" to "no one", "infact" to "in fact", "incase" to "in case",
        "alright" to "all right"
    )

    private val noDouble = setOf("had", "that")

    private val q1 = Regex(
        "^(what|why|how|who|whom|whose|when|where|which)\\s+(is|are|was|were|do|does|did|can|could|would|will|shall|should|has|have|had|about)\\b",
        RegexOption.IGNORE_CASE
    )
    private val q2 = Regex(
        "^(is|are|was|were|do|does|did|can|could|would|will|shall|should|has|have|had)\\s+(you|we|they|he|she|it|i|there|this|that|anyone|anybody|someone)\\b",
        RegexOption.IGNORE_CASE
    )
    private val whStart = Regex("^(what|why|how|who|whom|whose|when|where|which)\\b", RegexOption.IGNORE_CASE)
    private val abbreviation = Regex("(^|\\W)(e\\.g|i\\.e|etc|vs|mr|mrs|ms|dr|prof|st)\\.$")

    private fun matchCase(orig: String, rep: String): String = when {
        orig.length > 1 && orig == orig.uppercase() -> rep.uppercase()
        orig.isNotEmpty() && orig[0].isUpperCase() -> rep.replaceFirstChar { it.uppercase() }
        else -> rep
    }

    fun fix(
        text: String,
        online: Boolean,
        lang: String = "en-ZA",
        protectedTerms: List<String> = emptyList()
    ): String {
        val (masked, originals) = mask(text, protectedTerms)
        val local = unmask(localFix(masked), originals)
        if (!online) return local
        return try {
            deepFix(local, lang, protectedTerms)
        } catch (e: Exception) {
            local
        }
    }

    /** Swaps protected words for placeholders so the rules cannot touch them. */
    private fun mask(text: String, terms: List<String>): Pair<String, List<String>> {
        if (terms.isEmpty()) return Pair(text, emptyList())
        val originals = ArrayList<String>()
        var t = text
        for (term in terms.sortedByDescending { it.length }) {
            val re = Regex("(?<!\\w)" + Regex.escape(term) + "(?!\\w)", RegexOption.IGNORE_CASE)
            t = t.replace(re) { m ->
                originals.add(m.value)
                "\uE000" + (originals.size - 1) + "\uE001"
            }
        }
        return Pair(t, originals)
    }

    private fun unmask(text: String, originals: List<String>): String {
        if (originals.isEmpty()) return text
        return Regex("\uE000(\\d+)\uE001").replace(text) { m ->
            originals.getOrNull(m.groupValues[1].toInt()) ?: m.value
        }
    }

    fun localFix(input: String): String {
        var t = input.replace("\r\n", "\n")
        t = t.replace(Regex("[ \\t]{2,}"), " ")
        t = t.replace(Regex("[ \\t]+\\n"), "\n").trim()
        t = t.replace(Regex("[ \\t]+([,.!?;:])"), "\$1")
        t = t.replace(Regex(",{2,}"), ",")
        t = t.replace(Regex("([A-Za-z])([,;:!?])(?=[A-Za-z])"), "\$1\$2 ")
        t = t.replace(Regex("([a-z])\\.(?=[A-Z][a-z])"), "\$1. ")
        t = t.replace(Regex("(?<![\\w.'’-])i(?=['’](?:m|ll|ve|d)\\b)"), "I")
        t = t.replace(Regex("(?<![\\w.'’-])i(?![\\w'’-]|\\.\\w)"), "I")

        t = t.replace(Regex("\\b(" + contractions.keys.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)) { m ->
            val rep = contractions[m.value.lowercase()] ?: m.value
            if (rep.startsWith("I")) rep else matchCase(m.value, rep)
        }
        t = t.replace(Regex("\\b(" + typos.keys.joinToString("|") + ")\\b", RegexOption.IGNORE_CASE)) { m ->
            val rep = typos[m.value.lowercase()] ?: m.value
            matchCase(m.value, rep)
        }
        t = t.replace(Regex("\\b([A-Za-z']+)([ \\t]+)\\1\\b", RegexOption.IGNORE_CASE)) { m ->
            val w = m.groupValues[1]
            if (w.lowercase() in noDouble) m.value else w
        }

        val special = Regex("^(hour|honest|honou?r|heir)")
        val vowelStart = Regex("^[aeiou]")
        val vowelException = Regex("^(uni|use|usu|uti|uk|eu|one|once|ubiq)")
        t = t.replace(Regex("\\b(a|an)([ \\t]+)([A-Za-z]+)", RegexOption.IGNORE_CASE)) { m ->
            val art = m.groupValues[1]
            val sp = m.groupValues[2]
            val word = m.groupValues[3]
            val w = word.lowercase()
            val isSpecial = special.containsMatchIn(w)
            val vowelSound = isSpecial || (vowelStart.containsMatchIn(w) && !vowelException.containsMatchIn(w))
            when {
                art.lowercase() == "a" && vowelSound -> matchCase(art, "an") + sp + word
                art.lowercase() == "an" && !vowelSound && !vowelStart.containsMatchIn(w) && !isSpecial ->
                    matchCase(art, "a") + sp + word
                else -> m.value
            }
        }

        val src = t
        t = src.replace(Regex("(^|\\n[ \\t]*|[.!?]+[\"'”’)\\]]*[ \\t]+)([a-z])")) { m ->
            val pre = m.groupValues[1]
            val ch = m.groupValues[2]
            val start = m.range.first
            val before = (src.substring(maxOf(0, start - 8), start) + pre).lowercase().trimEnd()
            if (abbreviation.containsMatchIn(before)) m.value else pre + ch.uppercase()
        }

        t = t.split("\n").joinToString("\n") { fixLine(it) }
        return t
    }

    private fun fixLine(line: String): String {
        if (line.isBlank()) return line
        val listish = Regex("^\\s*([-*•]|\\d+[.)])\\s").containsMatchIn(line)
        return line.replace(Regex("[^.!?\\n]+[.!?]*")) { m ->
            val seg = m.value
            val lead = seg.takeWhile { it.isWhitespace() }
            val rest = seg.substring(lead.length)
            val core = rest.trimEnd()
            if (core.isEmpty()) return@replace seg
            val trail = rest.substring(core.length)
            val endMatch = Regex("[.!?]+$").find(core)
            var end = endMatch?.value ?: ""
            val body = core.substring(0, core.length - end.length)
            val words = body.trim().split(Regex("\\s+")).size
            val isLast = m.range.last + 1 >= line.length
            val isQuestion = q1.containsMatchIn(body) || q2.containsMatchIn(body)
            if (isQuestion && (end.isEmpty() || end == ".")) {
                end = "?"
            } else if (end.isEmpty() && whStart.containsMatchIn(body)) {
                end = "?"
            } else if (end.isEmpty() && isLast && words >= 3 && !listish &&
                !Regex("[:;)\"”'’\\]]$").containsMatchIn(body)
            ) {
                end = "."
            }
            lead + body + end + trail
        }
    }

    /** LanguageTool public API: applies the first suggested fix for each issue. */
    private fun deepFix(text: String, lang: String, protectedTerms: List<String>): String {
        if (text.length > 19000) return text
        val conn = URL("https://api.languagetool.org/v2/check").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 8000
            conn.readTimeout = 10000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("Accept", "application/json")
            val body = "text=" + URLEncoder.encode(text, "UTF-8") + "&language=" + URLEncoder.encode(lang, "UTF-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return text
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val matches = json.optJSONArray("matches") ?: return text

            data class Fix(val offset: Int, val length: Int, val value: String)
            val fixes = ArrayList<Fix>()
            for (i in 0 until matches.length()) {
                val m = matches.getJSONObject(i)
                val reps = m.optJSONArray("replacements") ?: continue
                if (reps.length() == 0) continue
                val value = reps.getJSONObject(0).optString("value")
                val off = m.getInt("offset")
                val len = m.getInt("length")
                if (off < 0 || off + len > text.length) continue
                val original = text.substring(off, off + len)
                if (original == value) continue
                if (protectedTerms.any { original.contains(it, ignoreCase = true) }) continue
                fixes.add(Fix(off, len, value))
            }
            fixes.sortByDescending { it.offset }
            val sb = StringBuilder(text)
            var lastStart = Int.MAX_VALUE
            for (f in fixes) {
                if (f.offset + f.length > lastStart) continue
                sb.replace(f.offset, f.offset + f.length, f.value)
                lastStart = f.offset
            }
            return sb.toString()
        } finally {
            conn.disconnect()
        }
    }
}
