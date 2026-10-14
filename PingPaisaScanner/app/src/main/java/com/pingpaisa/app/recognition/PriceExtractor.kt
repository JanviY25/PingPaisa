package com.pingpaisa.app.recognition

import kotlin.math.max
import kotlin.math.min

/** Plain geometry (no android.graphics) so the whole OCR-parsing layer is JVM-testable. */
data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    fun containsCenterOf(other: Box): Boolean =
        other.centerX in left..right && other.centerY in top..bottom
}

data class OcrLine(val text: String, val box: Box? = null, val confidence: Float? = null)

enum class PriceKind { SELLING, MRP, UNKNOWN }

data class PriceCandidate(
    val value: Double,
    val raw: String,
    val kind: PriceKind,
    val hasCurrencyMarker: Boolean,
    val score: Double,
    val ocrConfidence: Float?,
    val box: Box?
)

data class PriceResult(
    val best: PriceCandidate?,
    val candidates: List<PriceCandidate>,   // best first, de-duplicated by value
    val confidence: Float,                  // 0..1, 0 when nothing was found
    val note: String?
) {
    val isLowConfidence: Boolean get() = best != null && confidence < LOW_CONFIDENCE
    companion object {
        const val LOW_CONFIDENCE = 0.6f
        val NONE = PriceResult(null, emptyList(), 0f, null)
    }
}

/**
 * Finds the most likely *selling* price in OCR text.
 * Handles ₹18,999 / Rs. 18,999 / Rs.18999 / INR 18,999 / 18,999/- / 1,23,456 and ignores discounts,
 * EMIs, percentages, units (GB, mAh, ml...), dates, pin codes, phone numbers and model numbers.
 * Prefers: currency marker > larger print > inside the aiming box; a lower price beats an MRP.
 */
object PriceExtractor {
    // digits with optional Indian/western grouping and optional decimals; not glued to other digits/commas
    private val AMOUNT = Regex("""(?<![\d,])(?<!\d\.)(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d{1,2}))?(?!\d)""")
    private val MARKER_END = Regex(
        """(?i)(₹|\brs\.?|\binr|\bmrp\s*[:.\-]?|\bm\.r\.p\.?\s*[:.\-]?|\b(?:offer|sale|deal|special|our|selling)?\s*price\s*[:.\-]?|\bonly)\s*$"""
    )
    private val MRP_WORD = Regex("""(?i)\bm\.?r\.?p\b""")
    private val EXCLUDE_BEFORE = Regex(
        """(?i)\b(?:save|saving|savings|discount|cashback|emi|coupon|ph|phone|tel|call|mob|mobile|pin|pincode|qty|quantity|batch|lot|model|sku|asin|barcode|ean|upc|serial|no\.?|gst|cin|fssai|lic|care)\W*$"""
    )
    private val EXCLUDE_AFTER = Regex(
        """(?i)^\s*(?:%|percent\b|off\b|gb\b|tb\b|mb\b|kb\b|kg\b|gms?\b|mg\b|ml\b|ltrs?\b|litres?\b|liters?\b|l\b|mah\b|w\b|watts?\b|mp\b|inch(?:es)?\b|in\b|cm\b|mm\b|hz\b|fps\b|x\b|pcs\b|pc\b|pieces?\b|pack\b|units?\b|months?\b|mos?\b|yrs?\b|years?\b|/\s*(?:month|mo)\b|emi\b|stars?\b|ratings?\b|reviews?\b|people\b|sold\b|bought\b)"""
    )
    private val DATE_AFTER = Regex("""^\s*[/:\-]\s*\d""")
    private val DATE_BEFORE = Regex("""\d\s*[/:\-]\s*$""")
    private val LINE_EXCLUDE = Regex(
        """(?i)\bemi\b|per\s*month|/\s*(?:month|mo)\b|a\s*month|cashback|exchange|bank\s*offer|coupon|you\s*save|\bsave\b"""
    )

    private class Raw(
        val value: Double, val raw: String, val kind: PriceKind, val marker: Boolean, val slash: Boolean,
        val base: Double, val conf: Float?, val box: Box?
    )

    fun extract(lines: List<OcrLine>, focus: Box? = null): PriceResult {
        val raws = ArrayList<Raw>()
        lines.forEachIndexed { index, line ->
            if (LINE_EXCLUDE.containsMatchIn(line.text)) return@forEachIndexed
            val prev = lines.getOrNull(index - 1)?.text.orEmpty()
            AMOUNT.findAll(line.text).forEach { m ->
                evaluate(line, prev, m)?.let { raws += it }
            }
        }
        if (raws.isEmpty()) return PriceResult.NONE

        val maxHeight = raws.mapNotNull { it.box?.height }.maxOrNull()?.takeIf { it > 0f }
        val scored = raws.map { r ->
            var s = r.base
            if (r.box != null && maxHeight != null) s += 3.0 * (r.box.height / maxHeight)
            if (focus != null && r.box != null) s += if (focus.containsCenterOf(r.box)) 1.5 else -0.5
            r to s
        }
        // A cheaper marked price next to an MRP is the selling price.
        val mrpValues = scored.filter { it.first.kind == PriceKind.MRP }.map { it.first.value }
        val adjusted = scored.map { (r, s) ->
            val demote = r.kind == PriceKind.MRP && scored.any { (o, _) ->
                o.kind != PriceKind.MRP && (o.marker || o.slash) && mrpValues.any { m -> o.value < m }
            }
            r to (if (demote) s - 3.0 else s)
        }
        val candidates = adjusted
            .groupBy { it.first.value }
            .map { (_, group) -> group.maxByOrNull { it.second }!! }
            .sortedByDescending { it.second }
            .map { (r, s) -> PriceCandidate(r.value, r.raw, r.kind, r.marker || r.slash, s, r.conf, r.box) }

        val best = candidates.first()
        val second = candidates.getOrNull(1)
        val margin = if (second == null) 5.0 else best.score - second.score
        var conf = 0.35 + 0.25 * (best.ocrConfidence ?: 0.7f) + (if (best.hasCurrencyMarker) 0.2 else 0.0) +
            min(0.2, max(0.0, margin) * 0.04)
        if (!best.hasCurrencyMarker) conf = min(conf, 0.55)
        val note = when {
            best.kind == PriceKind.MRP -> "Only the MRP was readable - the selling price may be lower."
            second != null && margin < 1.0 -> "Several similar prices were found - please check."
            else -> null
        }
        return PriceResult(best, candidates, conf.coerceIn(0.0, 1.0).toFloat(), note)
    }

    private fun evaluate(line: OcrLine, prevLine: String, m: MatchResult): Raw? {
        val text = line.text
        val intPart = m.groupValues[1]
        val dec = m.groupValues[2]
        val value = (intPart.replace(",", "") + if (dec.isNotEmpty()) ".$dec" else "").toDoubleOrNull() ?: return null
        val before = text.substring(0, m.range.first)
        val after = text.substring(m.range.last + 1)

        if (EXCLUDE_AFTER.containsMatchIn(after)) return null
        if (DATE_AFTER.containsMatchIn(after) || DATE_BEFORE.containsMatchIn(before)) return null

        val marker = MARKER_END.find(before)
        val beforeMarker = if (marker != null) before.substring(0, marker.range.first) else before
        if (EXCLUDE_BEFORE.containsMatchIn(beforeMarker.takeLast(24))) return null

        val slash = after.trimStart().startsWith("/-")
        val markerText = marker?.value?.lowercase().orEmpty()
        val prevEndsWithMrp = prevLine.trim().lowercase().trimEnd(':', '.', ' ').let { it.endsWith("mrp") || it.endsWith("m.r.p") }
        val kind = when {
            MRP_WORD.containsMatchIn(markerText) || MRP_WORD.containsMatchIn(beforeMarker.takeLast(16)) -> PriceKind.MRP
            prevEndsWithMrp && beforeMarker.isBlank() -> PriceKind.MRP
            markerText.contains("price") || markerText.contains("only") -> PriceKind.SELLING
            else -> PriceKind.UNKNOWN
        }
        val hasMarker = marker != null || slash

        val digitsOnly = intPart.replace(",", "")
        if (hasMarker) {
            if (value < 1.0 || value > 10_000_000.0) return null
        } else {
            if (value < 10.0 || value > 999_999.0) return null
            if (digitsOnly.length >= 6 && !intPart.contains(',')) return null            // pin codes, ids
            if (!intPart.contains(',') && dec.isEmpty() && value in 1990.0..2039.0) return null // years
        }

        var base = 0.0
        if (marker != null) base += 4.0
        if (slash) base += 2.0
        if (kind == PriceKind.SELLING) base += 1.0
        if (kind == PriceKind.MRP) base -= 0.5
        if (intPart.contains(',')) base += 0.5
        if (!hasMarker && value < 100.0) base -= 1.0
        base += (line.confidence ?: 0.7f).toDouble()
        return Raw(value, m.value, kind, marker != null, slash, base, line.confidence, line.box)
    }
}
