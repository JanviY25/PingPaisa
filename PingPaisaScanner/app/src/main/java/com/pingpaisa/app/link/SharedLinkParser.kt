package com.pingpaisa.app.link

data class SharedContent(val url: String?, val text: String) {
    val host: String? get() = url?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.removePrefix("www.")
}

/** Pulls a product URL out of whatever a shopping app puts in EXTRA_TEXT. */
object SharedLinkParser {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val LEAD_NOISE = Regex(
        """(?i)^\W*(hey[, ]*)?(please\s+)?(check\s*(this|it)\s*out|check\s*out(\s+this|\s+the)?|look\s*at\s*this|have\s*a\s*look\s*at|take\s*a\s*look\s*at|found\s*this)\W*"""
    )

    /** Null when there is nothing usable. A plain text with no link is still returned (url = null). */
    fun parse(raw: String?): SharedContent? {
        val input = raw?.trim().orEmpty()
        if (input.isEmpty()) return null
        val match = URL.find(input)
        val url = match?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}', '>', '\'', '"')
        val lead = if (match == null) input else input.replace(match.value, " ")
        val cleaned = lead.replace(Regex("\\s+"), " ").trim().replace(LEAD_NOISE, "").trim()
        if (url == null && cleaned.isEmpty()) return null
        return SharedContent(url, cleaned.take(300))
    }

    /** Stable key for duplicate detection: no fragment, no trailing slash, scheme/host lower-cased. */
    fun normalizedKey(url: String): String {
        val noFragment = url.substringBefore('#').trimEnd('/')
        return noFragment.replaceFirst(Regex("^(?i)https?://(www\\.)?"), "").lowercase()
    }
}
