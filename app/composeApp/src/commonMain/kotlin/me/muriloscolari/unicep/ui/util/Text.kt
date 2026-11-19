package me.muriloscolari.unicep.ui.util

private val LOWER_CONNECTORS = setOf(
    "de", "da", "do", "das", "dos",
    "e", "em", "para", "por", "com",
    "a", "o", "as", "os", "à", "ao", "às", "aos",
    "no", "na", "nos", "nas", "pelo", "pela", "pelos", "pelas"
)

private val ROMAN_NUMERALS = setOf(
    "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"
)

private val KNOWN_ACRONYMS = setOf(
    "TCC", "TI", "ABNT", "UNICEP", "D2L", "AVA", "SSO",
    "IA", "API", "SQL", "UML", "POO", "SO", "BD", "LP",
    "ADS", "SI", "EAD", "CG", "IHC", "WEB", "HTML", "CSS", "RA"
)

/**
 * Converte nomes de disciplinas e alunos vindos em CAIXA ALTA do portal legado
 * para Title Case em português, preservando preposições, numerais romanos e siglas.
 */
fun String.toDisplayTitle(): String {
    val trimmed = trim()
    if (trimmed.isEmpty()) return ""

    // Preserva caso o texto já pareça misto intencionalmente? Não: o portal mistura às vezes,
    // mas normalizar tudo garante consistência visual. Exceção: strings curtas tipo códigos.
    val tokens = trimmed.split(Regex("\\s+"))
    val result = StringBuilder()

    tokens.forEachIndexed { index, rawToken ->
        if (index > 0) result.append(' ')
        val forceCap = index == 0 || tokens[index - 1].endsWith(":") || tokens[index - 1] == "-" || tokens[index - 1] == "/"
        result.append(formatToken(rawToken, forceCap))
    }
    return result.toString()
}

private fun formatToken(token: String, forceCap: Boolean): String {
    if (token.isEmpty()) return token
    // Separa pontuação inicial/final como parênteses, vírgula, dois-pontos
    var start = 0
    var end = token.length
    while (start < end && token[start] in "(\"[{'") start++
    while (end > start && token[end - 1] in "),.;:!?\"']}") end--

    if (start >= end) return token

    val prefix = token.substring(0, start)
    val core = token.substring(start, end)
    val suffix = token.substring(end)

    val formattedCore = if (core.contains('-')) {
        core.split('-').mapIndexed { i, part ->
            formatWord(part, forceCap && i == 0 || i > 0)
        }.joinToString("-")
    } else if (core.contains('/')) {
        core.split('/').mapIndexed { i, part ->
            formatWord(part, forceCap && i == 0 || i > 0)
        }.joinToString("/")
    } else {
        formatWord(core, forceCap)
    }

    return prefix + formattedCore + suffix
}

private fun formatWord(word: String, forceCap: Boolean): String {
    if (word.isEmpty()) return word
    val upper = word.uppercase()
    if (upper in ROMAN_NUMERALS) return upper
    if (upper in KNOWN_ACRONYMS) return upper

    val lower = word.lowercase()
    if (!forceCap && lower in LOWER_CONNECTORS) {
        return lower
    }
    return lower.replaceFirstChar { ch ->
        if (ch.isLowerCase()) ch.titlecase() else ch.toString()
    }
}

fun initialsFromName(fullName: String?): String {
    val parts = fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.isEmpty()) return "AL"
    if (parts.size == 1) return parts.first().take(2).uppercase()
    val first = parts.first().first().uppercaseChar()
    val last = parts.last().first().uppercaseChar()
    return "$first$last"
}

data class TextLinkSpan(
    val start: Int,
    val end: Int,
    val displayText: String,
    val url: String
)

private val URL_CANDIDATE_REGEX = Regex("""(?:https?://|www\.)[^\s<>"]+""", RegexOption.IGNORE_CASE)

private val WEB_INLINE_PREVIEW_EXTENSIONS = setOf(
    "pdf", "png", "jpg", "jpeg", "gif", "webp", "txt", "html", "htm"
)

private val PLAIN_TEXT_DOCUMENT_EXTENSIONS = setOf(
    "txt", "md", "csv", "json", "xml", "yaml", "yml",
    "sql", "java", "kt", "py", "c", "cpp", "h", "hpp", "cs", "js", "ts", "css", "sh", "ini", "log"
)

private val ANDROID_IN_APP_PREVIEW_EXTENSIONS = setOf(
    "pdf", "png", "jpg", "jpeg", "webp", "gif", "bmp", "html", "htm"
) + PLAIN_TEXT_DOCUMENT_EXTENSIONS

private fun extractNormalizedFileExtension(fileName: String): String? {
    val clean = fileName.trim().substringBefore('?').substringBefore('#')
    val lastSegment = clean.substringAfterLast('/')
    val dotIdx = lastSegment.lastIndexOf('.')
    if (dotIdx <= 0 || dotIdx == lastSegment.lastIndex) return null
    return lastSegment.substring(dotIdx + 1).lowercase()
}

fun extractTextLinks(text: String): List<TextLinkSpan> {
    if (text.isBlank()) return emptyList()
    val out = mutableListOf<TextLinkSpan>()
    for (match in URL_CANDIDATE_REGEX.findAll(text)) {
        val start = match.range.first
        if (start > 0) {
            val prev = text[start - 1]
            if (!prev.isWhitespace() && prev !in "(\"[{'=:") {
                continue
            }
        }
        var raw = match.value
        while (raw.isNotEmpty()) {
            val last = raw.last()
            val shouldTrim = when (last) {
                '.', ',', ';', ':', '!', '?', '\'', '"' -> true
                ')' -> raw.count { it == ')' } > raw.count { it == '(' }
                ']' -> raw.count { it == ']' } > raw.count { it == '[' }
                '}' -> raw.count { it == '}' } > raw.count { it == '{' }
                else -> false
            }
            if (!shouldTrim) break
            raw = raw.dropLast(1)
        }
        if (raw.isBlank()) continue
        val url = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
        val hasHost = url.substringAfter("://", "").substringBefore('/').contains('.')
        if (!hasHost) continue
        out += TextLinkSpan(
            start = start,
            end = start + raw.length,
            displayText = raw,
            url = url
        )
    }
    return out
}

fun shouldDirectDownloadOnWeb(fileName: String): Boolean {
    val ext = extractNormalizedFileExtension(fileName) ?: return false
    return ext !in WEB_INLINE_PREVIEW_EXTENSIONS
}

fun isPlainTextDocumentFile(fileName: String): Boolean {
    val ext = extractNormalizedFileExtension(fileName) ?: return false
    return ext in PLAIN_TEXT_DOCUMENT_EXTENSIONS
}

fun isInAppPreviewSupported(fileName: String): Boolean {
    val ext = extractNormalizedFileExtension(fileName) ?: return false
    return ext in ANDROID_IN_APP_PREVIEW_EXTENSIONS
}
