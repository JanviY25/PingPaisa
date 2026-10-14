package com.pingpaisa.app.finance

/**
 * Integration point for PingPaisa's real expense history (SMS-based tracker).
 * Return total spend for each of the last complete months (most recent last).
 * With >= AffordabilityCalculator.MIN_HISTORY_MONTHS entries the calculator uses their mean
 * (E-bar = (1/k) * sum E_j) instead of the user's typed estimates. This standalone build has no
 * history, so [NoExpenseHistory] is used and the result is labelled as an estimate.
 */
interface ExpenseHistoryProvider {
    fun lastCompleteMonthTotals(): List<Double>
}

object NoExpenseHistory : ExpenseHistoryProvider {
    override fun lastCompleteMonthTotals(): List<Double> = emptyList()
}
