package com.example.smart_expense_tracker.healthscore

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * ============================================================
 * SPENDING HEALTH SCORE ENGINE
 * Each of 4 factors is turned into a 0.0-1.0 sub-score (1.0 = best),
 * then combined with fixed weights into a 0-100 score.
 *
 * WEIGHTS (add up to 1.0):
 *   Savings rate ........ 35%   full marks when you save 30%+ of income
 *   EMI burden .......... 30%   full marks at 0% EMI, zero at 50%+ of income
 *   Spending consistency  20%   steady month-to-month spending scores higher
 *   Recurring reliability 15%   share of recurring payments made on schedule
 *
 * If there isn't enough data for consistency / reliability, they are
 * counted as neutral (1.0) instead of being punished, and the UI says so.
 * ============================================================
 */
object ScoreCalculator {

    const val WEIGHT_SAVINGS = 0.35
    const val WEIGHT_EMI = 0.30
    const val WEIGHT_CONSISTENCY = 0.20
    const val WEIGHT_RELIABILITY = 0.15

    private const val SAVINGS_TARGET = 0.30   // saving 30%+ of income = full marks
    private const val EMI_LIMIT = 0.50        // EMIs at 50%+ of income = zero marks
    private const val CV_LIMIT = 0.50         // 50% swing in monthly spend = zero marks

    private const val LOW_RISK_MIN = 70
    private const val MEDIUM_RISK_MIN = 40

    /** Real-world inputs, all MONTHLY. recurringReliability is null when nothing was detected. */
    data class RawFactors(
        val income: Double,
        val expense: Double,
        val totalEmiAmount: Double,
        val monthlyTotals: List<Double>,
        val recurringReliability: Double?
    )

    /** 0.0-1.0 sub-scores (1.0 = best) plus real percentages for explanations. */
    data class NormalizedFactors(
        val savings: Double,
        val emi: Double,
        val consistency: Double,
        val reliability: Double,
        val savingsRatePercent: Int,
        val emiRatioPercent: Int,
        val consistencyKnown: Boolean,
        val reliabilityKnown: Boolean
    )

    enum class RiskLevel { LOW, MEDIUM, HIGH }

    data class ScoreResult(
        val score: Int,
        val risk: RiskLevel,
        val factors: NormalizedFactors
    )

    /** (income - expense) / income. Negative means overspending. */
    fun savingsRate(income: Double, expense: Double): Double =
        if (income > 0) (income - expense) / income else 0.0

    /** EMI total / income. Can exceed 1.0. */
    fun emiRatio(income: Double, emi: Double): Double =
        if (income > 0) emi / income else 0.0

    fun calculate(raw: RawFactors): ScoreResult {
        val f = normalize(raw)
        val weighted =
            f.savings * WEIGHT_SAVINGS +
                    f.emi * WEIGHT_EMI +
                    f.consistency * WEIGHT_CONSISTENCY +
                    f.reliability * WEIGHT_RELIABILITY

        // roundToInt (not toInt) so a perfect 1.0 gives 100, not 99.
        val score = (weighted * 100).roundToInt().coerceIn(0, 100)
        return ScoreResult(score, classifyRisk(score), f)
    }

    fun normalize(raw: RawFactors): NormalizedFactors {
        val hasIncome = raw.income > 0
        val rate = savingsRate(raw.income, raw.expense)
        val ratio = emiRatio(raw.income, raw.totalEmiAmount)

        val savings = if (hasIncome) (rate / SAVINGS_TARGET).coerceIn(0.0, 1.0) else 0.0
        val emi = if (hasIncome) (1.0 - ratio / EMI_LIMIT).coerceIn(0.0, 1.0) else 0.0

        val consistencyKnown = raw.monthlyTotals.size >= 2
        val consistency = if (consistencyKnown) calculateConsistency(raw.monthlyTotals) else 1.0

        val reliabilityKnown = raw.recurringReliability != null
        val reliability = (raw.recurringReliability ?: 1.0).coerceIn(0.0, 1.0)

        return NormalizedFactors(
            savings = savings,
            emi = emi,
            consistency = consistency,
            reliability = reliability,
            savingsRatePercent = (rate * 100).roundToInt().coerceIn(-999, 999),
            emiRatioPercent = (ratio * 100).roundToInt().coerceIn(0, 999),
            consistencyKnown = consistencyKnown,
            reliabilityKnown = reliabilityKnown
        )
    }

    /** Coefficient of variation (std-dev / mean): 0 = identical months -> 1.0, 50%+ swing -> 0.0. */
    private fun calculateConsistency(monthlyTotals: List<Double>): Double {
        val mean = monthlyTotals.average()
        if (mean == 0.0) return 1.0
        val variance = monthlyTotals.sumOf { (it - mean) * (it - mean) } / monthlyTotals.size
        val cv = sqrt(variance) / mean
        return (1.0 - cv / CV_LIMIT).coerceIn(0.0, 1.0)
    }

    /** Rule-based classifier, kept separate so it can later be swapped for a trained ML model. */
    fun classifyRisk(score: Int): RiskLevel = when {
        score >= LOW_RISK_MIN -> RiskLevel.LOW
        score >= MEDIUM_RISK_MIN -> RiskLevel.MEDIUM
        else -> RiskLevel.HIGH
    }
}