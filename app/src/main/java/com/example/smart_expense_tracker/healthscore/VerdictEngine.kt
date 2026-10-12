package com.example.smart_expense_tracker.healthscore

import kotlin.math.roundToInt

/**
 * Section 5.4 "verdict engine": decides Safe / Tight / Risky for a
 * hypothetical loan and produces reasons from REAL before-vs-after numbers.
 *
 * Rules (common bank rule of thumb, "FOIR"):
 *  - RISKY: expenses + EMI exceed income, OR total EMIs > 50% of income,
 *           OR the resulting health score falls in the High-risk band.
 *  - TIGHT: total EMIs > 35% of income, OR score falls in Medium band.
 *  - SAFE : otherwise.
 * Assumption: a new loan's EMI is extra monthly spending on top of the
 * existing spending.
 */
object VerdictEngine {

    enum class Verdict { SAFE, TIGHT, RISKY }

    data class VerdictResult(
        val verdict: Verdict,
        val reasons: List<String>,
        val before: ScoreCalculator.ScoreResult,
        val after: ScoreCalculator.ScoreResult
    )

    /** EMIs up to 35% of income are comfortable. */
    const val COMFORTABLE_EMI_RATIO = 0.35

    /** Banks rarely allow total EMIs above about 50% of income. */
    const val MAX_EMI_RATIO = 0.50

    private fun pct(ratio: Double): String =
        if (ratio >= 1.0) "100%+" else "${(ratio * 100).roundToInt()}%"

    /**
     * Largest NEW monthly EMI that keeps total EMIs within [emiRatioLimit] of
     * income AND keeps expenses + EMI within income. Used to size the loan slider.
     */
    fun maxEmi(baseline: ScoreCalculator.RawFactors, emiRatioLimit: Double): Double {
        if (baseline.income <= 0) return 0.0
        val byEmiRatio = baseline.income * emiRatioLimit - baseline.totalEmiAmount
        val bySurplus = baseline.income - baseline.expense
        return maxOf(0.0, minOf(byEmiRatio, bySurplus))
    }

    fun evaluate(baseline: ScoreCalculator.RawFactors, newEmi: Double): VerdictResult {
        val before = ScoreCalculator.calculate(baseline)
        val afterRaw = baseline.copy(
            expense = baseline.expense + newEmi,
            totalEmiAmount = baseline.totalEmiAmount + newEmi
        )
        val after = ScoreCalculator.calculate(afterRaw)
        val income = baseline.income

        if (income <= 0) {
            return VerdictResult(
                Verdict.RISKY,
                listOf("Enter your monthly income above to get a meaningful verdict."),
                before,
                after
            )
        }

        val emiRatioBefore = ScoreCalculator.emiRatio(income, baseline.totalEmiAmount)
        val emiRatioAfter = ScoreCalculator.emiRatio(income, afterRaw.totalEmiAmount)
        val savingsBefore = ScoreCalculator.savingsRate(income, baseline.expense)
        val savingsAfter = ScoreCalculator.savingsRate(income, afterRaw.expense)
        val surplusAfter = income - afterRaw.expense

        val verdict = when {
            surplusAfter < 0 || emiRatioAfter > MAX_EMI_RATIO ||
                    after.risk == ScoreCalculator.RiskLevel.HIGH -> Verdict.RISKY
            emiRatioAfter > COMFORTABLE_EMI_RATIO ||
                    after.risk == ScoreCalculator.RiskLevel.MEDIUM -> Verdict.TIGHT
            else -> Verdict.SAFE
        }

        // Only savings and EMI factors change when a loan is added, so
        // rank those two by how many score points each one loses.
        val savingsPts = ((before.factors.savings - after.factors.savings) *
                ScoreCalculator.WEIGHT_SAVINGS * 100).roundToInt()
        val emiPts = ((before.factors.emi - after.factors.emi) *
                ScoreCalculator.WEIGHT_EMI * 100).roundToInt()

        val savingsLine = "Savings rate would go from ${(savingsBefore * 100).roundToInt()}% " +
                "to ${(savingsAfter * 100).roundToInt()}% (-$savingsPts score points)."
        val emiLine = "EMIs would use ${pct(emiRatioAfter)} of your income, " +
                "up from ${pct(emiRatioBefore)} (-$emiPts score points)."

        val topTwo = listOf(savingsPts to savingsLine, emiPts to emiLine)
            .sortedByDescending { it.first }
            .map { it.second }

        val surplusLine = if (surplusAfter >= 0) {
            "After expenses and this EMI you'd still keep about ₹${"%,.0f".format(surplusAfter)} a month."
        } else {
            "You'd be about ₹${"%,.0f".format(-surplusAfter)} short every month, because expenses plus this EMI exceed your income."
        }

        return VerdictResult(verdict, topTwo + surplusLine, before, after)
    }
}