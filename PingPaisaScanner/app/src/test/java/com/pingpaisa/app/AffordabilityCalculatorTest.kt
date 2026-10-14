package com.pingpaisa.app

import com.pingpaisa.app.finance.AffordabilityCalculator
import com.pingpaisa.app.finance.Candidate
import com.pingpaisa.app.finance.Decision
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.finance.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffordabilityCalculatorTest {
    // D = 60000 - 20000 - 15000 - 5000 = 20000 ; S_a = 100000 - 60000 = 40000 ; alpha = beta = 0.5
    // B_m = 10000 ; savings allowance = 20000 ; B_safe = 30000
    private val profile = FinancialProfile(
        netIncome = 60000.0, fixedExpenses = 20000.0, variableExpenses = 15000.0, monthlySavingsTarget = 5000.0,
        savings = 100000.0, emergencyReserve = 60000.0, discretionaryShare = 0.5, savingsSpendShare = 0.5, maxWaitMonths = 12
    )

    private fun one(price: Double) = AffordabilityCalculator.assess(profile, listOf(Candidate("a", price))).lines[0]

    @Test fun budgetMatchesTheFormulas() {
        val b = AffordabilityCalculator.budget(profile)
        assertEquals(20000.0, b.disposable, 0.001)
        assertEquals(40000.0, b.spendableSavings, 0.001)
        assertEquals(10000.0, b.monthlyAllowance, 0.001)
        assertEquals(30000.0, b.safeBudget, 0.001)
        assertFalse(b.hasShortfall)
    }

    @Test fun singleProductDecisions() {
        assertEquals(Decision.BUY_NOW, one(8000.0).decision)
        val caution = one(25000.0)
        assertEquals(Decision.BUY_WITH_CAUTION, caution.decision)
        assertEquals(10000.0, caution.fromMonthly, 0.001)
        assertEquals(15000.0, caution.fromSavings, 0.001)
        val wait = one(45000.0)
        assertEquals(Decision.WAIT, wait.decision)
        assertEquals(2, wait.monthsToSave)           // (45000 - 30000) / 10000 = 1.5 -> 2 months
        assertEquals(Decision.SKIP, one(500000.0).decision) // 47 months > 12
    }

    @Test fun emergencyReserveIsNeverSpent() {
        val a = AffordabilityCalculator.assess(profile, listOf(Candidate("a", 29000.0)))
        assertTrue(a.reserveProtected)
        assertTrue(a.savingsAfter >= profile.emergencyReserve)
        val belowReserve = profile.copy(savings = 40000.0)
        assertEquals(0.0, AffordabilityCalculator.budget(belowReserve).spendableSavings, 0.0)
    }

    @Test fun existingShortfallBlocksEveryPurchase() {
        val short = profile.copy(netIncome = 30000.0) // D = -10000
        val a = AffordabilityCalculator.assess(short, listOf(Candidate("a", 100.0)))
        assertEquals(Decision.SKIP, a.lines[0].decision)
        assertEquals(0.0, a.budgetLeft, 0.0)
    }

    @Test fun multipleProductsAreSummedAndGreedyOrdered() {
        val items = listOf(Candidate("headphones", 3000.0), Candidate("shoes", 4000.0), Candidate("watch", 8000.0))
        val a = AffordabilityCalculator.assess(profile, items)
        assertEquals(15000.0, a.totalAll, 0.001)
        assertEquals(15000.0, a.totalBuyNow, 0.001)
        assertEquals(15000.0, a.budgetLeft, 0.001)
        assertEquals(Decision.BUY_NOW, a.line("headphones")!!.decision)
        assertEquals(Decision.BUY_NOW, a.line("shoes")!!.decision)
        assertEquals(Decision.BUY_WITH_CAUTION, a.line("watch")!!.decision)
        assertEquals(95000.0, a.savingsAfter, 0.001)
    }

    @Test fun mustHaveGetsBudgetFirst() {
        val items = listOf(Candidate("nice", 8000.0, Priority.NORMAL), Candidate("must", 25000.0, Priority.MUST_HAVE))
        val a = AffordabilityCalculator.assess(profile, items)
        assertEquals(Decision.BUY_WITH_CAUTION, a.line("must")!!.decision)
        val nice = a.line("nice")!!
        assertEquals(Decision.WAIT, nice.decision)  // only 5000 left of 30000 -> 3000 short -> 1 month
        assertEquals(1, nice.monthsToSave)
    }

    @Test fun missingPriceIsNotCounted() {
        val a = AffordabilityCalculator.assess(profile, listOf(Candidate("a", null), Candidate("b", 1000.0)))
        assertEquals(Decision.NEEDS_PRICE, a.line("a")!!.decision)
        assertEquals(1000.0, a.totalAll, 0.0)
        assertEquals(1, a.needsPriceCount)
    }

    @Test fun usesHistoryMeanWhenThreeMonthsExist() {
        val b = AffordabilityCalculator.budget(profile, listOf(30000.0, 40000.0, 50000.0))
        assertEquals(40000.0, b.monthlySpend, 0.001)
        assertEquals(15000.0, b.disposable, 0.001)   // 60000 - 40000 - 5000
        assertEquals(3, b.historyMonths)
        assertEquals(35000.0, AffordabilityCalculator.budget(profile, listOf(1.0, 2.0)).monthlySpend, 0.001) // too few -> typed
    }

    @Test fun fullShareGivesPlainBudgetFormula() {
        val b = AffordabilityCalculator.budget(profile.copy(discretionaryShare = 1.0, savingsSpendShare = 1.0))
        assertEquals(20000.0 + 40000.0, b.safeBudget, 0.001) // B = max(0,D) + S_a
    }

    @Test fun profileValidation() {
        assertTrue(FinancialProfile().problems().isNotEmpty())   // no income
        assertTrue(profile.isUsable())
        assertFalse(profile.copy(fixedExpenses = -1.0).isUsable())
    }
}
