package com.pingpaisa.app.util

import kotlin.math.abs
import kotlin.math.round

/** Indian-rupee formatting / parsing (lakh-crore grouping: 12,34,567). Pure Kotlin, JVM-testable. */
object Money {
    const val SYMBOL = "₹"

    fun groupIndian(value: Long): String {
        val s = abs(value).toString()
        val grouped = if (s.length <= 3) {
            s
        } else {
            val last3 = s.takeLast(3)
            val rest = s.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
            "$rest,$last3"
        }
        return if (value < 0) "-$grouped" else grouped
    }

    /** ₹18,999 · ₹1,23,456.50 · decimals only when needed. */
    fun format(amount: Double, withSymbol: Boolean = true): String {
        val symbol = if (withSymbol) SYMBOL else ""
        if (!amount.isFinite()) return "$symbol—"
        val rounded = round(amount * 100) / 100
        val magnitude = abs(rounded)
        val whole = magnitude.toLong()
        val cents = round((magnitude - whole) * 100).toInt()
        val body = if (cents == 0) groupIndian(whole)
        else "${groupIndian(whole)}.${cents.toString().padStart(2, '0')}"
        return (if (rounded < 0) "-" else "") + symbol + body
    }

    private val CURRENCY_WORDS = Regex("""(?i)\b(rs|inr)\b\.?|₹""")

    /** "₹ 18,999", "Rs. 18,999/-", "18999.50" -> number. Returns null when nothing sensible is found. */
    fun parse(text: String?): Double? {
        if (text.isNullOrBlank()) return null
        val cleaned = text.replace(CURRENCY_WORDS, "").filter { it.isDigit() || it == '.' }
        if (cleaned.isEmpty() || cleaned == "." || cleaned.count { it == '.' } > 1) return null
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
