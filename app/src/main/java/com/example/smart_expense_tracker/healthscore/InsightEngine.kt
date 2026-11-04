package com.example.smart_expense_tracker.healthscore

import kotlin.math.roundToInt

/**
 * Section 5.3: the text shown in the insight card.
 *
 * fallback() builds a plain-English explanation from the REAL factor values,
 * with no internet or API key, so the card is never blank or broken. A real LLM
 * call can later replace it and set isAi = true; this function stays as the
 * fallback for when that call fails or times out.
 */
object InsightEngine {

    data class Insight(val text: String, val isAi: Boolean)

    private data class Drag(val name: String, val pointsLost: Int, val detail: String)

    /** Score points a factor is costing: (1 - sub-score) * weight * 100. */
    private fun pointsLost(subScore: Double, weight: Double): Int =
        ((1.0 - subScore) * weight * 100).roundToInt()

    private fun rupees(value: Double): String = "₹${"%,.0f".format(value)}"

    fun fallback(raw: ScoreCalculator.RawFactors, result: ScoreCalculator.ScoreResult): Insight =
        Insight(buildTemplate(raw, result), isAi = false)

    private fun buildTemplate(
        raw: ScoreCalculator.RawFactors,
        result: ScoreCalculator.ScoreResult
    ): String {
        val f = result.factors

        if (raw.income <= 0) {
            return "Enter your monthly income to get a personalised explanation of your score."
        }

        val riskWord = when (result.risk) {
            ScoreCalculator.RiskLevel.LOW -> "low risk"
            ScoreCalculator.RiskLevel.MEDIUM -> "medium risk"
            ScoreCalculator.RiskLevel.HIGH -> "high risk"
        }
        val intro = "Your score is ${result.score}/100 ($riskWord)."

        if (raw.expense <= 0.0) {
            return "$intro No spending is recorded for this month, so your savings rate looks perfect. " +
                    "Add your transactions for a realistic score."
        }

        val emiPct = if (f.emiRatioPercent >= 100) "100%+" else "${f.emiRatioPercent}%"

        val savingsDetail = if (f.savingsRatePercent < 0) {
            "you spend ${rupees(raw.expense - raw.income)} more than you earn this month"
        } else {
            "you save ${f.savingsRatePercent}% of your income (30% or more gets full marks)"
        }
        val emiDetail = "EMIs use $emiPct of your income (${rupees(raw.totalEmiAmount)} of ${rupees(raw.income)})"

        // Which factors cost the most points? Unknown ones are neutral, so they cost nothing.
        val drags = listOf(
            Drag("savings", pointsLost(f.savings, ScoreCalculator.WEIGHT_SAVINGS), savingsDetail),
            Drag("EMI burden", pointsLost(f.emi, ScoreCalculator.WEIGHT_EMI), emiDetail),
            Drag(
                "spending consistency",
                if (f.consistencyKnown) pointsLost(f.consistency, ScoreCalculator.WEIGHT_CONSISTENCY) else 0,
                "your spending changes a lot from month to month"
            ),
            Drag(
                "recurring reliability",
                if (f.reliabilityKnown) pointsLost(f.reliability, ScoreCalculator.WEIGHT_RELIABILITY) else 0,
                "some recurring payments were not made on schedule"
            )
        ).sortedByDescending { it.pointsLost }

        val top = drags[0]
        if (top.pointsLost < 1) {
            return "$intro Every factor is in good shape: you save ${f.savingsRatePercent}% of your income " +
                    "and EMIs use only $emiPct. Keep it up."
        }

        val text = StringBuilder(
            "$intro The biggest drag is ${top.name}: ${top.detail}, which costs about ${top.pointsLost} points."
        )
        val second = drags[1]
        if (second.pointsLost >= 1) {
            text.append(" Next is ${second.name}: ${second.detail}, which costs about ${second.pointsLost} points.")
        }
        if (!f.consistencyKnown || !f.reliabilityKnown) {
            text.append(" Spending consistency and recurring reliability are counted as neutral until there is more history.")
        }
        return text.toString()
    }
}