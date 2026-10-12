package com.example.smart_expense_tracker.healthscore

import android.content.Context
import kotlin.math.pow

/** One loan quote the user typed in after confirming the rate with the bank. */
data class LoanQuote(
    val bankName: String,
    val annualRate: Double   // % p.a., reducing balance (the usual bank method)
)

/** Everything computed for one quote at the chosen amount and tenure. */
data class QuoteResult(
    val quote: LoanQuote,
    val emi: Double,
    val totalInterest: Double,
    val verdict: VerdictEngine.VerdictResult
)

/** How much the user can borrow, estimated from income, spending and existing EMIs. */
data class LoanLimits(
    val maxLoan: Double,   // bank-style limit: total EMIs up to 50% of income
    val safeLoan: Double   // comfortable limit: total EMIs up to 35% of income
)

object LoanMath {

    /** Reducing balance: EMI = P * r * (1+r)^n / ((1+r)^n - 1), r = monthly rate. */
    fun emi(principal: Double, tenureMonths: Int, annualRatePercent: Double): Double {
        val monthlyRate = annualRatePercent / 12 / 100
        if (monthlyRate == 0.0) return principal / tenureMonths
        val factor = (1 + monthlyRate).pow(tenureMonths)
        return principal * monthlyRate * factor / (factor - 1)
    }

    /** The EMI formula turned around: the loan amount a given EMI can repay. */
    fun principalFromEmi(emi: Double, tenureMonths: Int, annualRatePercent: Double): Double {
        val monthlyRate = annualRatePercent / 12 / 100
        if (monthlyRate == 0.0) return emi * tenureMonths
        return emi * (1 - (1 + monthlyRate).pow(-tenureMonths)) / monthlyRate
    }
}

object LoanEligibility {

    /** Used for the slider limit only while the user has not added any bank quote. */
    const val DEFAULT_RATE = 11.0

    /**
     * Largest EMI the user can afford (see VerdictEngine.maxEmi) converted into
     * a loan amount at the given rate and tenure.
     */
    fun limits(
        baseline: ScoreCalculator.RawFactors,
        tenureMonths: Int,
        annualRatePercent: Double
    ): LoanLimits {
        val maxEmi = VerdictEngine.maxEmi(baseline, VerdictEngine.MAX_EMI_RATIO)
        val safeEmi = VerdictEngine.maxEmi(baseline, VerdictEngine.COMFORTABLE_EMI_RATIO)
        return LoanLimits(
            maxLoan = LoanMath.principalFromEmi(maxEmi, tenureMonths, annualRatePercent),
            safeLoan = LoanMath.principalFromEmi(safeEmi, tenureMonths, annualRatePercent)
        )
    }
}

object LoanQuotes {

    fun evaluate(
        baseline: ScoreCalculator.RawFactors,
        principal: Double,
        tenureMonths: Int,
        quote: LoanQuote
    ): QuoteResult {
        val emi = LoanMath.emi(principal, tenureMonths, quote.annualRate)
        return QuoteResult(
            quote = quote,
            emi = emi,
            totalInterest = emi * tenureMonths - principal,
            verdict = VerdictEngine.evaluate(baseline, emi)
        )
    }

    /** Safe first, then Tight, then Risky; inside each group the lowest total interest first. */
    fun compare(
        baseline: ScoreCalculator.RawFactors,
        principal: Double,
        tenureMonths: Int,
        quotes: List<LoanQuote>
    ): List<QuoteResult> {
        return quotes
            .map { evaluate(baseline, principal, tenureMonths, it) }
            .sortedWith(compareBy<QuoteResult>({ it.verdict.verdict.ordinal }, { it.totalInterest }))
    }
}

/** Saves the user's quotes on the phone so they survive an app restart. */
object QuoteStore {
    private const val PREFS = "loan_quotes"
    private const val KEY = "quotes_v2"   // new key: older quotes (with fee/type) are ignored

    fun load(context: Context): List<LoanQuote> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        return raw.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 2) return@mapNotNull null
            val rate = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            LoanQuote(parts[0], rate)
        }
    }

    fun save(context: Context, quotes: List<LoanQuote>) {
        val raw = quotes.joinToString("\n") { "${it.bankName}|${it.annualRate}" }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, raw)
            .apply()
    }
}