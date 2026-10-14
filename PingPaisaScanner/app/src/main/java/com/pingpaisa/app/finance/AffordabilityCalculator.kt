package com.pingpaisa.app.finance

import com.pingpaisa.app.util.Money
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

enum class Priority(val label: String) { MUST_HAVE("Must-have"), NORMAL("Normal"), NICE_TO_HAVE("Nice-to-have") }

enum class Decision { BUY_NOW, BUY_WITH_CAUTION, WAIT, SKIP, NEEDS_PRICE }

data class Candidate(val id: String, val price: Double?, val priority: Priority = Priority.NORMAL)

/** All intermediate quantities, so the UI can show the working instead of an unexplained score. */
data class Budget(
    val income: Double,              // I
    val fixedTotal: Double,          // E_f (+ extra debt EMIs)
    val variable: Double,            // E_v
    val monthlySpend: Double,        // E used in D (typed estimate, or mean of history)
    val savingsTarget: Double,       // S_t
    val disposable: Double,          // D = I - E - S_t
    val savings: Double,             // S
    val reserve: Double,             // S_e
    val spendableSavings: Double,    // S_a = max(0, S - S_e)
    val alpha: Double,
    val beta: Double,
    val monthlyAllowance: Double,    // B_m = alpha * max(0, D)
    val savingsAllowance: Double,    // beta * S_a
    val safeBudget: Double,          // B_safe = B_m + beta * S_a   (beta = 1 gives the plain B_m + S_a)
    val maxWaitMonths: Int,
    val historyMonths: Int           // 0 = typed estimates, k>0 = mean of k real months
) {
    val hasShortfall: Boolean get() = disposable < 0
    val usesHistory: Boolean get() = historyMonths > 0
}

data class LineAssessment(
    val id: String,
    val price: Double?,
    val decision: Decision,
    val monthsToSave: Int?,
    val fromMonthly: Double,
    val fromSavings: Double,
    val reason: String
)

data class Assessment(
    val budget: Budget,
    val lines: List<LineAssessment>,      // same order as the input candidates
    val totalAll: Double,                 // P_total of every priced product
    val totalBuyNow: Double,
    val totalWait: Double,
    val totalSkip: Double,
    val budgetLeft: Double,               // B_safe - totalBuyNow (never below 0)
    val overBudget: Double,               // priced-but-not-bought total minus what is left
    val savingsAfter: Double,
    val reserveProtected: Boolean,
    val incomeShareBuyNow: Double,        // fraction of monthly income
    val incomeShareAll: Double,
    val monthlySpendAfter: Double         // E + totalBuyNow (this month)
) {
    fun line(id: String): LineAssessment? = lines.firstOrNull { it.id == id }
    val buyCount get() = lines.count { it.decision == Decision.BUY_NOW || it.decision == Decision.BUY_WITH_CAUTION }
    val waitCount get() = lines.count { it.decision == Decision.WAIT }
    val skipCount get() = lines.count { it.decision == Decision.SKIP }
    val needsPriceCount get() = lines.count { it.decision == Decision.NEEDS_PRICE }
    val pricedCount get() = buyCount + waitCount + skipCount
}

/**
 * Deterministic cash-flow model (no ML):
 *   D       = I - E_f - E_v - S_t                monthly money left after plan
 *   S_a     = max(0, S - S_e)                    savings above the emergency reserve
 *   B_m     = alpha * max(0, D)                  monthly discretionary allowance
 *   B_safe  = B_m + beta * S_a                   safe purchase budget
 * A product of price p is
 *   AFFORDABLE         p fits in B_m (no savings touched)
 *   AFFORDABLE+CAUTION p fits in B_safe but needs savings above the reserve
 *   WAIT               p > B_safe but can be covered by B_m within maxWaitMonths
 *   SKIP               D < 0, or no monthly allowance, or it would take longer than maxWaitMonths
 * Several products use a greedy pass: priority first (must-have, normal, nice-to-have), then cheaper first,
 * each product only if it fits what is left. Greedy is transparent but NOT guaranteed optimal.
 */
object AffordabilityCalculator {
    const val MIN_HISTORY_MONTHS = 3
    private const val EPS = 0.005

    fun budget(profile: FinancialProfile, history: List<Double> = emptyList()): Budget {
        val income = profile.totalIncome
        val fixedTotal = profile.fixedExpenses + profile.extraDebtEmi
        val typedSpend = fixedTotal + profile.variableExpenses
        val useHistory = history.size >= MIN_HISTORY_MONTHS
        val spend = if (useHistory) history.average() else typedSpend
        val disposable = income - spend - profile.monthlySavingsTarget
        val spendable = max(0.0, profile.savings - profile.emergencyReserve)
        val alpha = profile.discretionaryShare.coerceIn(0.0, 1.0)
        val beta = profile.savingsSpendShare.coerceIn(0.0, 1.0)
        val monthly = alpha * max(0.0, disposable)
        val fromSavings = beta * spendable
        return Budget(
            income = income, fixedTotal = fixedTotal, variable = profile.variableExpenses,
            monthlySpend = spend, savingsTarget = profile.monthlySavingsTarget, disposable = disposable,
            savings = profile.savings, reserve = profile.emergencyReserve, spendableSavings = spendable,
            alpha = alpha, beta = beta, monthlyAllowance = monthly, savingsAllowance = fromSavings,
            safeBudget = monthly + fromSavings, maxWaitMonths = profile.maxWaitMonths,
            historyMonths = if (useHistory) history.size else 0
        )
    }

    fun assess(profile: FinancialProfile, items: List<Candidate>, history: List<Double> = emptyList()): Assessment {
        val b = budget(profile, history)
        val decisions = HashMap<String, Decision>()
        val fromMonthly = HashMap<String, Double>()
        val fromSavings = HashMap<String, Double>()
        val months = HashMap<String, Int>()
        val reasons = HashMap<String, String>()

        val priced = items.withIndex().filter { (it.value.price ?: 0.0) > 0.0 }
        items.filter { (it.price ?: 0.0) <= 0.0 }.forEach {
            decisions[it.id] = Decision.NEEDS_PRICE
            reasons[it.id] = "Enter a price to include this product."
        }

        val order = priced.sortedWith(
            compareBy<IndexedValue<Candidate>>({ it.value.priority.ordinal }, { it.value.price }, { it.index })
        ).map { it.value }

        var remMonthly = if (b.hasShortfall) 0.0 else b.monthlyAllowance
        var remSavings = if (b.hasShortfall) 0.0 else b.savingsAllowance

        // Pass 1: what can be bought now
        val rejected = ArrayList<Candidate>()
        for (c in order) {
            val p = c.price!!
            if (b.hasShortfall) { rejected += c; continue }
            if (p <= remMonthly + remSavings + EPS) {
                val fm = min(p, remMonthly)
                val fs = max(0.0, p - fm)
                remMonthly -= fm
                remSavings -= fs
                fromMonthly[c.id] = fm
                fromSavings[c.id] = fs
                if (fs > EPS) {
                    decisions[c.id] = Decision.BUY_WITH_CAUTION
                    reasons[c.id] = "Uses ${Money.format(fm)} of this month's allowance and ${Money.format(fs)} " +
                        "of savings above your emergency reserve."
                } else {
                    decisions[c.id] = Decision.BUY_NOW
                    reasons[c.id] = "Fits this month's ${Money.format(b.monthlyAllowance)} allowance; savings untouched."
                }
            } else rejected += c
        }

        // Pass 2: rejected products -> wait (with a timeline) or skip
        val poolLeft = remMonthly + remSavings
        var waitCumulative = 0.0
        for (c in rejected) {
            val p = c.price!!
            when {
                b.hasShortfall -> {
                    decisions[c.id] = Decision.SKIP
                    reasons[c.id] = "Your plan already runs a monthly shortfall of ${Money.format(-b.disposable)}. " +
                        "Fix that before adding purchases."
                }
                b.monthlyAllowance <= EPS -> {
                    decisions[c.id] = Decision.SKIP
                    reasons[c.id] = "It is ${Money.format(p - poolLeft)} over your safe budget and you have no monthly " +
                        "allowance to save from."
                }
                else -> {
                    val deficit = waitCumulative + p - poolLeft
                    val m = min(1000.0, ceil(max(deficit, EPS) / b.monthlyAllowance)).toInt()
                    if (m <= b.maxWaitMonths) {
                        waitCumulative += p
                        decisions[c.id] = Decision.WAIT
                        months[c.id] = m
                        reasons[c.id] = "${Money.format(max(0.0, p - poolLeft))} over your safe budget. About $m month" +
                            "${if (m == 1) "" else "s"} of your ${Money.format(b.monthlyAllowance)} monthly allowance would cover it."
                    } else {
                        decisions[c.id] = Decision.SKIP
                        months[c.id] = m
                        reasons[c.id] = "At ${Money.format(b.monthlyAllowance)} a month it would take about $m months " +
                            "(your limit is ${b.maxWaitMonths})."
                    }
                }
            }
        }

        val lines = items.map { c ->
            LineAssessment(
                id = c.id, price = c.price?.takeIf { it > 0 }, decision = decisions[c.id] ?: Decision.NEEDS_PRICE,
                monthsToSave = months[c.id], fromMonthly = fromMonthly[c.id] ?: 0.0,
                fromSavings = fromSavings[c.id] ?: 0.0, reason = reasons[c.id] ?: ""
            )
        }
        fun sum(vararg d: Decision) = lines.filter { it.decision in d }.sumOf { it.price ?: 0.0 }
        val buy = sum(Decision.BUY_NOW, Decision.BUY_WITH_CAUTION)
        val wait = sum(Decision.WAIT)
        val skip = sum(Decision.SKIP)
        val savingsUsed = lines.sumOf { it.fromSavings }
        val safe = if (b.hasShortfall) 0.0 else b.safeBudget
        val budgetLeft = max(0.0, safe - buy)
        val savingsAfter = b.savings - savingsUsed
        return Assessment(
            budget = b, lines = lines, totalAll = buy + wait + skip, totalBuyNow = buy, totalWait = wait, totalSkip = skip,
            budgetLeft = budgetLeft, overBudget = max(0.0, wait + skip - budgetLeft),
            savingsAfter = savingsAfter,
            reserveProtected = savingsUsed <= EPS || savingsAfter + EPS >= b.reserve,
            incomeShareBuyNow = if (b.income > 0) buy / b.income else 0.0,
            incomeShareAll = if (b.income > 0) (buy + wait + skip) / b.income else 0.0,
            monthlySpendAfter = b.monthlySpend + buy
        )
    }
}
