package com.chuhe.quickmax

object CodeParser {
    private const val DASHES = "-－﹣‐‑‒–—―−~～_·・．。、:：|｜!！ーｰ"
    private val TRIPLE = Regex(
        "(?<![0-9A-Za-z])([0-9A-Za-z]{1,4})[- \\t]+([0-9A-Za-z]{1,3})[- \\t]+([0-9A-Za-z]{3,6})(?![0-9A-Za-z])"
    )
    private val DIGITS = Regex("^[0-9]{3,12}$")

    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                ch in '０'..'９' -> sb.append((ch.code - '０'.code + '0'.code).toChar())
                ch in 'Ａ'..'Ｚ' -> sb.append((ch.code - 'Ａ'.code + 'A'.code).toChar())
                ch in 'ａ'..'ｚ' -> sb.append((ch.code - 'ａ'.code + 'a'.code).toChar())
                DASHES.contains(ch) -> sb.append('-')
                ch == '\u00A0' || ch == '\t' || ch == '\r' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("-+"), "-")
    }

    private fun format(a: String, b: String, c: String): String =
        "${a.uppercase()}-${b.uppercase()}-${c.uppercase()}"

    fun parse(raw: String): List<String> {
        val text = normalize(raw)
        val found = LinkedHashSet<String>()
        for (m in TRIPLE.findAll(text)) {
            val code = format(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            if (plausible(code)) found.add(code)
        }
        return found.toList()
    }

    fun canonicalManual(input: String): String? {
        val s = normalize(input).trim()
        val m = TRIPLE.matchEntire(s)
        if (m != null) {
            val code = format(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            if (plausible(code)) return code
        }
        val digits = s.trim('-')
        if (DIGITS.matches(digits)) return digits
        return null
    }

    fun splitManual(raw: String): List<String> {
        val text = normalize(raw)
        val out = LinkedHashSet<String>()
        val ranges = mutableListOf<IntRange>()
        for (m in TRIPLE.findAll(text)) {
            val code = format(m.groupValues[1], m.groupValues[2], m.groupValues[3])
            if (plausible(code)) {
                out.add(code)
                ranges.add(m.range)
            }
        }
        val rest = StringBuilder(text)
        for (range in ranges.sortedByDescending { it.first }) {
            rest.replace(range.first, range.last + 1, " ")
        }
        for (token in rest.toString().split(Regex("[\\s,，;；、]+"))) {
            val digits = token.trim().trim('-')
            if (DIGITS.matches(digits)) out.add(digits)
        }
        return out.toList()
    }

    private fun plausible(code: String): Boolean {
        val parts = code.split('-')
        if (parts.size != 3) return true
        val joined = parts.joinToString("")
        if (joined.length > 12) return false
        if (joined.count { it.isDigit() } < 3) return false
        if (!parts[2].any { it.isDigit() }) return false
        val head = parts[0]
        if (head.startsWith("400") || head.startsWith("800") || head.startsWith("955")) return false
        return true
    }
}
