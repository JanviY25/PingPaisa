package com.example.smart_expense_tracker.healthscore

import com.example.smart_expense_tracker.DetectedRecurring
import com.example.smart_expense_tracker.RecurringItem
import com.example.smart_expense_tracker.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Turns the raw transaction list into the numbers ScoreCalculator needs:
 *  - this month's spend and total monthly outflow (spend + EMIs)
 *  - per-month totals (for the spending-consistency factor)
 *  - recurring-payment reliability (for the reliability factor)
 * All transaction dates in this app are stored as "dd/MM/yy" strings.
 */
object SpendingHistory {

    // SimpleDateFormat is not thread-safe, so a fresh one is created per call.
    private fun parseDate(dateStr: String): Date? = try {
        SimpleDateFormat("dd/MM/yy", Locale.getDefault()).parse(dateStr)
    } catch (e: Exception) {
        null
    }

    private fun monthKey(date: Date): String =
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(date)

    /** Month ("2026-09") -> total spent in that month, oldest first. */
    fun monthlyTotals(transactions: List<Transaction>): Map<String, Double> {
        val totals = mutableMapOf<String, Double>()
        for (txn in transactions) {
            val date = parseDate(txn.date) ?: continue
            val key = monthKey(date)
            totals[key] = (totals[key] ?: 0.0) + txn.amount
        }
        return totals.toSortedMap()
    }

    /** Transactions that fall in the CURRENT calendar month. */
    fun thisMonthTransactions(transactions: List<Transaction>): List<Transaction> {
        val current = monthKey(Date())
        return transactions.filter { txn ->
            parseDate(txn.date)?.let { monthKey(it) } == current
        }
    }

    /** Total of tracked transactions in the CURRENT calendar month only. */
    fun thisMonthSpend(transactions: List<Transaction>): Double =
        thisMonthTransactions(transactions).sumOf { it.amount }

    /**
     * Money that actually leaves the account this month:
     * tracked spending + EMIs declared in the Recurring tab that are NOT
     * already among this month's transactions. Bank EMI auto-debits usually
     * don't arrive as UPI alerts, so without this they'd be invisible to the
     * savings rate. If a transaction with the same name exists this month,
     * the EMI is assumed to be already counted (avoids double counting).
     */
    fun monthlyOutflow(
        transactions: List<Transaction>,
        recurringItems: List<RecurringItem>
    ): Double {
        val thisMonth = thisMonthTransactions(transactions)
        val spend = thisMonth.sumOf { it.amount }
        val paidNames = thisMonth.map { it.recipient.trim().lowercase() }.toSet()
        val untrackedEmi = recurringItems
            .filter { it.type == "EMI" && it.name.trim().lowercase() !in paidNames }
            .sumOf { it.amount }
        return spend + untrackedEmi
    }

    /** Up to the last 6 monthly totals, used for the consistency factor. */
    fun recentMonthlyTotals(transactions: List<Transaction>): List<Double> =
        monthlyTotals(transactions).values.toList().takeLast(6)

    /**
     * For every auto-detected recurring payment: (payments actually seen) /
     * (months between its first and last payment + 1). Missed months lower
     * the ratio. Returns the average, or null if nothing was detected
     * (meaning "no data", not "bad").
     */
    fun recurringReliability(
        transactions: List<Transaction>,
        detected: List<DetectedRecurring>
    ): Double? {
        val ratios = detected.mapNotNull { item ->
            val dates = transactions
                .filter { it.recipient.trim().equals(item.name.trim(), ignoreCase = true) }
                .mapNotNull { parseDate(it.date) }
                .sorted()
            if (dates.size < 2) {
                null
            } else {
                val expectedMonths = monthsBetween(dates.first(), dates.last()) + 1
                (dates.size.toDouble() / expectedMonths).coerceIn(0.0, 1.0)
            }
        }
        return if (ratios.isEmpty()) null else ratios.average()
    }

    private fun monthsBetween(start: Date, end: Date): Int {
        val a = Calendar.getInstance().apply { time = start }
        val b = Calendar.getInstance().apply { time = end }
        return (b.get(Calendar.YEAR) - a.get(Calendar.YEAR)) * 12 +
                (b.get(Calendar.MONTH) - a.get(Calendar.MONTH))
    }
}