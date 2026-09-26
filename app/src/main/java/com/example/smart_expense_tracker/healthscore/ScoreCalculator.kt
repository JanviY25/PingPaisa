package com.example.smart_expense_tracker.healthscore

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ============================================================
 * SPENDING HEALTH SCORE ENGINE
 * ============================================================
 * Computes a single 0-100 "financial health" number from four
 * independent factors. Each factor is first normalized to a
 * 0.0-1.0 sub-score (1.0 = best), then combined using fixed
 * weights below. This makes it easy to explain in a viva:
 * "your score is a weighted average of 4 things we measure
 * about your spending".
 *
 * WEIGHTS (must add up to 1.0):
 *   - Savings Rate         -> 35% (most important: are you saving money at all?)
 *   - EMI-to-Income Ratio  -> 30% (how much of your income is already committed to loans?)
 *   - Spending Consistency -> 20% (do you spend a predictable amount, or wildly swing?)
 *   - Recurring Reliability-> 15% (do your recurring payments happen on schedule?)
 * ============================================================
 */
object ScoreCalculator {

    // ---- Weights (tweak here only, nowhere else, if grading rubric changes) ----
    private const val WEIGHT_SAVINGS = 0.35
    private const val WEIGHT_EMI = 0.30
    private const val WEIGHT_CONSISTENCY = 0.20
    private const val WEIGHT_RELIABILITY = 0.15

    // ---- Risk thresholds on the final 0-100 score ----
    private const val LOW_RISK_MIN = 70
    private const val MEDIUM_RISK_MIN = 40

    /**
     * Raw factor values before normalization, in real-world units.
     * These are what the UI shows as the 4 progress bars.
     */
    data class RawFactors(
        val income: Double,           // monthly income (manual input, or from Teammate A's Income table)
        val expense: Double,          // monthly expense (sum of this month's transactions)
        val totalEmiAmount: Double,   // sum of RecurringItem amounts where type == "EMI"
        val monthlyTotals: List<Double>, // total expense for each of the last N months (for consistency)
        val recurringOccurrences: Int,   // how many times recurring payments were actually detected
        val recurringExpectedMonths: Int // how many months we'd expect them to have occurred by now
    )

    /** The 0.0-1.0 normalized version of each factor, used for the progress bars. */
    data class NormalizedFactors(
        val savingsRate: Double,
        val emiToIncomeRatio: Double,
        val spendingConsistency: Double,
        val recurringReliability: Double
    )

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class ScoreResult(
        val score: Int,
        val risk: RiskLevel,
        val factors: NormalizedFactors
    )

    /**
     * Main entry point: pass in the raw numbers, get back a full result.
     */
    fun calculate(raw: RawFactors): ScoreResult {
        val factors = normalize(raw)

        val weightedSum =
            factors.savingsRate * WEIGHT_SAVINGS +
                    (1.0 - factors.emiToIncomeRatio) * WEIGHT_EMI + // lower EMI ratio is better, so invert it
                    factors.spendingConsistency * WEIGHT_CONSISTENCY +
                    factors.recurringReliability * WEIGHT_RELIABILITY

        val score = (weightedSum * 100).toInt().coerceIn(0, 100)

        return ScoreResult(
            score = score,
            risk = classifyRisk(score),
            factors = factors
        )
    }

    /**
     * Converts raw real-world numbers into clean 0.0-1.0 sub-scores.
     * Kept separate from calculate() so it's independently testable
     * and so the UI can show these exact values in the progress bars.
     */
    fun normalize(raw: RawFactors): NormalizedFactors {
        // 1) Savings rate = (income - expense) / income, clamped to 0..1
        //    (a negative rate, i.e. overspending, is clamped to 0 = worst case)
        val savingsRate = if (raw.income > 0) {
            ((raw.income - raw.expense) / raw.income).coerceIn(0.0, 1.0)
        } else 0.0

        // 2) EMI-to-income ratio, clamped to 0..1 (anything over 100% of income is just "maxed out")
        val emiRatio = if (raw.income > 0) {
            (raw.totalEmiAmount / raw.income).coerceIn(0.0, 1.0)
        } else 0.0

        // 3) Spending consistency: derived from the coefficient of variation
        //    (standard deviation / mean) of monthly totals. Low variation = consistent = good.
        val consistency = calculateConsistency(raw.monthlyTotals)

        // 4) Recurring reliability: what fraction of expected recurring payments
        //    actually showed up as detected transactions. No recurring items at all
        //    is treated as neutral-good (1.0), since there's nothing to miss.
        val reliability = if (raw.recurringExpectedMonths <= 0) {
            1.0
        } else {
            (raw.recurringOccurrences.toDouble() / raw.recurringExpectedMonths).coerceIn(0.0, 1.0)
        }

        return NormalizedFactors(
            savingsRate = savingsRate,
            emiToIncomeRatio = emiRatio,
            spendingConsistency = consistency,
            recurringReliability = reliability
        )
    }

    /**
     * Coefficient-of-variation based consistency score.
     * CV = stddev / mean. A CV of 0 (identical spending every month) -> consistency 1.0.
     * A CV of 0.5+ (spending swings by 50%+ of the average) -> consistency 0.0.
     */
    private fun calculateConsistency(monthlyTotals: List<Double>): Double {
        if (monthlyTotals.size < 2) return 1.0 // not enough history to judge; assume neutral-good

        val mean = monthlyTotals.average()
        if (mean == 0.0) return 1.0

        val variance = monthlyTotals.sumOf { (it - mean) * (it - mean) } / monthlyTotals.size
        val stdDev = sqrt(variance)
        val coefficientOfVariation = stdDev / mean

        // A CV of 0.5 (50% swing) or more is treated as "0 consistency".
        val consistency = 1.0 - (coefficientOfVariation / 0.5)
        return consistency.coerceIn(0.0, 1.0)
    }

    /**
     * Simple rule-based risk classifier, deliberately separated into its
     * own function so it can later be swapped for a trained ML model
     * without touching calculate() or normalize() above.
     */
    fun classifyRisk(score: Int): RiskLevel = when {
        score >= LOW_RISK_MIN -> RiskLevel.LOW
        score >= MEDIUM_RISK_MIN -> RiskLevel.MEDIUM
        else -> RiskLevel.HIGH
    }
}