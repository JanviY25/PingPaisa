package com.example.smart_expense_tracker

import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class DetectedRecurring(
    val name: String,
    val averageAmount: Double,
    val category: String,
    val occurrences: Int
)

object RecurringDetector {

    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    private fun parseDate(dateStr: String): Date? {
        return try { dateFormat.parse(dateStr) } catch (e: Exception) { null }
    }

    fun detect(transactions: List<Transaction>): List<DetectedRecurring> {
        val results = mutableListOf<DetectedRecurring>()

        // Group by recipient (case-insensitive)
        val grouped = transactions.groupBy { it.recipient.lowercase().trim() }

        for ((_, txns) in grouped) {
            if (txns.size < 2) continue // need at least 2 payments to detect a pattern

            val sorted = txns.sortedBy { parseDate(it.date)?.time ?: 0L }
            val amounts = sorted.map { it.amount }
            val avgAmount = amounts.average()

            // Check amounts are all within ~15% of the average (EMIs/SIPs are usually fixed or near-fixed)
            val amountsConsistent = amounts.all { abs(it - avgAmount) / avgAmount <= 0.15 }
            if (!amountsConsistent) continue

            // Check gaps between consecutive payments are roughly monthly (25-35 days)
            var monthlyGapsFound = 0
            for (i in 1 until sorted.size) {
                val d1 = parseDate(sorted[i - 1].date)
                val d2 = parseDate(sorted[i].date)
                if (d1 != null && d2 != null) {
                    val gapDays = abs(d2.time - d1.time) / (1000 * 60 * 60 * 24)
                    if (gapDays in 25..35) monthlyGapsFound++
                }
            }

            // Require at least one clean monthly gap to call it recurring
            if (monthlyGapsFound >= 2) {
                results.add(
                    DetectedRecurring(
                        name = sorted.first().recipient,
                        averageAmount = avgAmount,
                        category = sorted.first().category,
                        occurrences = sorted.size
                    )
                )
            }
        }

        return results
    }
}