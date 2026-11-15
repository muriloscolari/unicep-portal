package me.muriloscolari.unicep.ui.util

import me.muriloscolari.unicep.platform.PlatformBridge

private data class ParsedUtcDateTime(
    val year: Int,
    val month: Int,
    val day: Int,
    val epochMillis: Long
)

/**
 * Formata timestamps ISO-8601 (ex: "2026-11-14T22:42:16Z") em tempo relativo amigável.
 */
fun formatRelativeTime(isoString: String, nowMillis: Long = PlatformBridge.currentTimeMillis()): String {
    val parsed = parseIso8601(isoString) ?: return isoString
    val diffMs = (nowMillis - parsed.epochMillis).coerceAtLeast(0L)
    val diffSec = diffMs / 1000L
    val diffMin = diffSec / 60L
    val diffHour = diffMin / 60L
    val diffDay = diffHour / 24L

    return when {
        diffSec < 60L -> "agora"
        diffMin < 60L -> "há $diffMin min"
        diffHour < 24L -> "há $diffHour h"
        diffDay == 1L -> "ontem"
        diffDay < 7L -> "há $diffDay dias"
        else -> {
            val dd = parsed.day.toString().padStart(2, '0')
            val mm = parsed.month.toString().padStart(2, '0')
            "$dd/$mm"
        }
    }
}

private fun parseIso8601(raw: String): ParsedUtcDateTime? {
    val s = raw.trim()
    if (s.length < 10) return null

    val year = s.substring(0, 4).toIntOrNull() ?: return null
    if (s[4] != '-') return null
    val month = s.substring(5, 7).toIntOrNull() ?: return null
    if (s[7] != '-') return null
    val day = s.substring(8, 10).toIntOrNull() ?: return null

    var hour = 0
    var minute = 0
    var second = 0
    var tzOffsetMinutes = 0

    if (s.length >= 16 && (s[10] == 'T' || s[10] == ' ')) {
        hour = s.substring(11, 13).toIntOrNull() ?: return null
        minute = s.substring(14, 16).toIntOrNull() ?: return null
        var idx = 16
        if (idx + 3 <= s.length && s[idx] == ':') {
            second = s.substring(idx + 1, idx + 3).toIntOrNull() ?: 0
            idx += 3
        }
        if (idx < s.length && s[idx] == '.') {
            idx++
            while (idx < s.length && s[idx].isDigit()) idx++
        }
        if (idx < s.length) {
            val tz = s.substring(idx)
            if (tz != "Z" && tz != "z" && tz.length >= 6 && (tz[0] == '+' || tz[0] == '-')) {
                val sign = if (tz[0] == '+') 1 else -1
                val tzH = tz.substring(1, 3).toIntOrNull() ?: 0
                val tzM = tz.substring(4, 6).toIntOrNull() ?: 0
                tzOffsetMinutes = sign * (tzH * 60 + tzM)
            }
        }
    }

    val days = daysFromCivil(year, month, day)
    val epochSec = days * 86400L + hour * 3600L + minute * 60L + second - tzOffsetMinutes * 60L
    return ParsedUtcDateTime(year = year, month = month, day = day, epochMillis = epochSec * 1000L)
}

// Algoritmo de Howard Hinnant para dias desde 1970-01-01
private fun daysFromCivil(y0: Int, m: Int, d: Int): Long {
    val y = if (m <= 2) y0 - 1 else y0
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val doy = (153 * (m + (if (m > 2) -3 else 9)) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era.toLong() * 146097L + doe.toLong() - 719468L
}
