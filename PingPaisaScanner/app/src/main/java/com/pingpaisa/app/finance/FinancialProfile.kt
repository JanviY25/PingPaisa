package com.pingpaisa.app.finance

/**
 * Presets for the two configurable policy parameters (see AffordabilityCalculator):
 *  alpha = share of the monthly surplus that may go to discretionary purchases
 *  beta  = share of savings above the emergency reserve that may be spent per purchase period
 * They are policy choices, not universal financial rules - the profile screen says so.
 */
enum class RiskTolerance(val label: String, val alpha: Double, val beta: Double, val maxWaitMonths: Int) {
    CAUTIOUS("Cautious", 0.30, 0.25, 6),
    BALANCED("Balanced", 0.50, 0.50, 12),
    FLEXIBLE("Flexible", 0.80, 0.80, 18)
}

data class FinancialProfile(
    val netIncome: Double = 0.0,             // I  (monthly take-home)
    val extraIncome: Double = 0.0,           // reliable extra monthly income only
    val savings: Double = 0.0,               // S
    val emergencyReserve: Double = 0.0,      // S_e
    val fixedExpenses: Double = 0.0,         // E_f (rent, bills, EMIs ...)
    val variableExpenses: Double = 0.0,      // E_v (food, transport, fun ...)
    val monthlySavingsTarget: Double = 0.0,  // S_t
    val extraDebtEmi: Double = 0.0,          // only debts NOT already inside fixed expenses
    val discretionaryShare: Double = RiskTolerance.BALANCED.alpha,
    val savingsSpendShare: Double = RiskTolerance.BALANCED.beta,
    val maxWaitMonths: Int = RiskTolerance.BALANCED.maxWaitMonths,
    val risk: RiskTolerance = RiskTolerance.BALANCED
) {
    val totalIncome: Double get() = netIncome + extraIncome

    /** Empty list = usable. Messages are user-facing. */
    fun problems(): List<String> {
        val out = ArrayList<String>()
        val money = listOf(
            netIncome, extraIncome, savings, emergencyReserve, fixedExpenses,
            variableExpenses, monthlySavingsTarget, extraDebtEmi
        )
        if (money.any { !it.isFinite() || it < 0 }) out += "Amounts must be zero or more."
        if (money.any { it > MAX_AMOUNT }) out += "One of the amounts is unrealistically large."
        if (netIncome <= 0) out += "Enter your monthly income."
        if (discretionaryShare !in 0.0..1.0 || savingsSpendShare !in 0.0..1.0) out += "Shares must be between 0% and 100%."
        if (maxWaitMonths !in 1..120) out += "Waiting period must be between 1 and 120 months."
        return out
    }

    fun isUsable(): Boolean = problems().isEmpty()

    companion object {
        const val MAX_AMOUNT = 10_000_000_000.0 // ₹1,000 crore
    }
}
