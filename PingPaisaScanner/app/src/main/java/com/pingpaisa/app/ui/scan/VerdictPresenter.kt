package com.pingpaisa.app.ui.scan

import com.pingpaisa.app.data.ItemState
import com.pingpaisa.app.data.Product
import com.pingpaisa.app.finance.Assessment
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.util.Money
import kotlin.math.roundToInt

enum class VerdictKind { GREEN, AMBER, RED, NEUTRAL }
enum class VerdictIcon { CHECK, WARN, LOCK, SCAN }
enum class Mark(val glyph: String) { OK("✓"), WARN("!"), BAD("✕") }
enum class VerdictAction { NONE, DETAILS, PROFILE }
data class VerdictBullet(val mark: Mark, val text: String)

data class VerdictUi(
    val kind: VerdictKind,
    val icon: VerdictIcon,
    val title: String,
    val body: String,
    val bullets: List<VerdictBullet> = emptyList(),
    val action: VerdictAction = VerdictAction.NONE
)

/** Turns the calculator's numbers into the card text. Never produces a positive verdict without a real assessment. */
object VerdictPresenter {
    fun present(products: List<Product>, a: Assessment?, profile: FinancialProfile?): VerdictUi {
        if (products.any { it.state == ItemState.PROCESSING }) {
            return neutral("Analysing…", "Reading the price and matching the category.", VerdictIcon.SCAN)
        }
        if (products.isEmpty()) {
            return neutral("Scan a product", "Point the camera at a price tag or add photos to see if you can afford it.", VerdictIcon.SCAN)
        }
        if (profile == null || !profile.isUsable() || a == null) {
            return VerdictUi(
                VerdictKind.NEUTRAL, VerdictIcon.SCAN, "Add your finances",
                "A verdict needs your income, savings and expenses.",
                listOf(VerdictBullet(Mark.WARN, "Tap here to set up your financial profile")), VerdictAction.PROFILE
            )
        }
        if (a.pricedCount == 0) {
            return neutral("Price needed", "Tap a product below and enter its price to get a verdict.", VerdictIcon.SCAN)
        }
        val b = a.budget
        val missing = if (a.needsPriceCount > 0) " ${a.needsPriceCount} item${if (a.needsPriceCount == 1) "" else "s"} still need a price." else ""
        val incomeLine = VerdictBullet(Mark.OK, "${pct(if (a.buyCount > 0) a.incomeShareBuyNow else a.incomeShareAll)} of monthly income")
        val reserve = VerdictBullet(Mark.OK, "Emergency fund stays protected")
        val left = if (a.budgetLeft <= 0.5) VerdictBullet(Mark.WARN, "No safe budget left after this")
        else VerdictBullet(Mark.OK, "${Money.format(a.budgetLeft)} left in safe budget")
        val over = VerdictBullet(Mark.WARN, "${Money.format(a.overBudget)} over your safe budget")

        if (a.pricedCount == 1) {
            val line = a.lines.first { it.decision != Decision.NEEDS_PRICE }
            return when (line.decision) {
                Decision.BUY_NOW -> VerdictUi(VerdictKind.GREEN, VerdictIcon.CHECK, "Yes, you can afford it",
                    "This purchase fits your plan without touching savings.$missing", listOf(left, reserve, incomeLine), VerdictAction.DETAILS)
                Decision.BUY_WITH_CAUTION -> VerdictUi(VerdictKind.AMBER, VerdictIcon.WARN, "Yes, with caution",
                    "This fits, but ${Money.format(line.fromSavings)} comes from savings above your emergency reserve.$missing",
                    listOf(left, reserve, incomeLine), VerdictAction.DETAILS)
                Decision.WAIT -> VerdictUi(VerdictKind.RED, VerdictIcon.LOCK, "Better to wait",
                    "This would push your shopping budget over the safe limit.$missing",
                    listOf(over, reserve, VerdictBullet(Mark.WARN, "About ${line.monthsToSave} month${if (line.monthsToSave == 1) "" else "s"} to save up")),
                    VerdictAction.DETAILS)
                else -> VerdictUi(VerdictKind.RED, VerdictIcon.LOCK, "Skip for now", line.reason + missing,
                    listOf(if (b.hasShortfall) VerdictBullet(Mark.BAD, "Monthly shortfall of ${Money.format(-b.disposable)}") else over, reserve, incomeLine),
                    VerdictAction.DETAILS)
            }
        }

        val n = a.pricedCount
        return when {
            a.buyCount == n && a.lines.none { it.decision == Decision.BUY_WITH_CAUTION } ->
                VerdictUi(VerdictKind.GREEN, VerdictIcon.CHECK, "Yes, you can afford all $n",
                    "Together ${Money.format(a.totalAll)} fits your plan without touching savings.$missing", listOf(left, reserve, incomeLine), VerdictAction.DETAILS)
            a.buyCount == n ->
                VerdictUi(VerdictKind.AMBER, VerdictIcon.WARN, "Yes, with caution",
                    "Together ${Money.format(a.totalAll)} fits, using ${Money.format(a.lines.sumOf { it.fromSavings })} from savings above your reserve.$missing",
                    listOf(left, reserve, incomeLine), VerdictAction.DETAILS)
            a.buyCount > 0 ->
                VerdictUi(VerdictKind.AMBER, VerdictIcon.WARN, "Buy ${a.buyCount} now, hold ${n - a.buyCount}",
                    "${Money.format(a.totalBuyNow)} of ${Money.format(a.totalAll)} fits your safe budget; the rest should wait or be skipped.$missing",
                    listOf(left, reserve, over), VerdictAction.DETAILS)
            a.waitCount > 0 ->
                VerdictUi(VerdictKind.RED, VerdictIcon.LOCK, "Better to wait",
                    "Together ${Money.format(a.totalAll)} would push your shopping budget over the safe limit.$missing",
                    listOf(over, reserve, incomeLine), VerdictAction.DETAILS)
            else ->
                VerdictUi(VerdictKind.RED, VerdictIcon.LOCK, "Skip for now",
                    (if (b.hasShortfall) "Your plan already runs a monthly shortfall of ${Money.format(-b.disposable)}."
                    else "These don't fit your plan within ${b.maxWaitMonths} months.") + missing,
                    listOf(if (b.hasShortfall) VerdictBullet(Mark.BAD, "Monthly shortfall of ${Money.format(-b.disposable)}") else over, reserve, incomeLine),
                    VerdictAction.DETAILS)
        }
    }

    private fun neutral(title: String, body: String, icon: VerdictIcon) =
        VerdictUi(VerdictKind.NEUTRAL, icon, title, body)

    private fun pct(share: Double): String {
        val p = share * 100
        return if (p < 1.0) "<1%" else "${p.roundToInt()}%"
    }
}
