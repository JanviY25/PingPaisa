package com.pingpaisa.app.recognition

/** Picks a readable product title from OCR lines (largest, non-price, non-boilerplate text). */
object ProductNamer {
    private val BOILERPLATE = Regex(
        """(?i)\b(mrp|m\.r\.p|inclusive|taxes|net\s*qty|net\s*wt|mfd|mfg|expiry|best\s*before|batch|barcode|scan|www|http|customer\s*care|made\s*in|price|rs|inr|off|discount|sale|offer|save|emi|cashback|add\s*to\s*cart|buy\s*now|free\s*delivery|delivery|in\s*stock|ratings?|reviews?|gst|fssai|marketed|manufactured|packed|warranty)\b|₹|%|\.com|\.in\b"""
    )

    fun deriveName(lines: List<OcrLine>): String? {
        val usable = lines.filter { l ->
            val t = l.text.trim()
            val letters = t.count { it.isLetter() }
            val digits = t.count { it.isDigit() }
            letters >= 3 && t.length <= 70 && digits <= t.length * 0.35 && !BOILERPLATE.containsMatchIn(t)
        }
        if (usable.isEmpty()) return null
        val withBox = usable.filter { it.box != null }
        val picked = if (withBox.isEmpty()) {
            listOf(usable.maxByOrNull { it.text.length }!!)
        } else {
            val maxH = withBox.maxOf { it.box!!.height }
            withBox.filter { it.box!!.height >= 0.6f * maxH }.sortedBy { it.box!!.top }.take(2)
        }
        val name = picked.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim()
        return name.take(60).takeIf { it.isNotBlank() }
    }
}
