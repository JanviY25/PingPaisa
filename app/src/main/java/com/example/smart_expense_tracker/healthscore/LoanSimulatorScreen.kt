package com.example.smart_expense_tracker.healthscore

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private object LoanColors {
    val Sage = Color(0xFFA8C4A2)
    val DustyTeal = Color(0xFF7FA99B)
    val Blush = Color(0xFFF4C6C6)
    val Peach = Color(0xFFF2D3B3)
    val AiLavender = Color(0xFFC8BFE7)
    val WarmCream = Color(0xFFFAF7F2)
    val CardWhite = Color(0xFFFFFFFF)
    val TextDark = Color(0xFF2E2E2E)
}

private const val MIN_LOAN = 10000f
private const val STEP = 10000f

private fun rupees(value: Double): String = "₹${"%,.0f".format(value)}"

/** Turns the typed text into a quote, or null if the name is empty or the rate is out of range. */
private fun buildQuote(name: String, rateText: String): LoanQuote? {
    val cleanName = name.replace("|", " ").replace("\n", " ").trim()
    val rate = rateText.toDoubleOrNull() ?: return null
    if (cleanName.isEmpty() || rate !in 0.1..60.0) return null
    return LoanQuote(cleanName, rate)
}

@Composable
fun LoanSimulatorScreen(
    baseline: ScoreCalculator.RawFactors,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var loanAmount by remember { mutableStateOf(300000f) }
    var tenureMonths by remember { mutableStateOf(36) }
    var quotes by remember { mutableStateOf(QuoteStore.load(context)) }
    var selectedName by remember { mutableStateOf("") }

    var nameText by remember { mutableStateOf("") }
    var rateText by remember { mutableStateOf("") }

    val updateQuotes: (List<LoanQuote>) -> Unit = { newList ->
        quotes = newList
        QuoteStore.save(context, newList)
    }

    // --- How much can this user borrow? (slider maximum) ---
    // Rate for the limit: the highest rate among the user's quotes (conservative,
    // so the limit holds for every bank), or an assumed default if none yet.
    val rateIsAssumed = quotes.isEmpty()
    val limitRate = quotes.maxOfOrNull { it.annualRate } ?: LoanEligibility.DEFAULT_RATE
    val limits = LoanEligibility.limits(baseline, tenureMonths, limitRate)
    val maxLoan = (limits.maxLoan / STEP).toInt() * STEP      // rounded down to nearest 10,000
    val safeLoan = (limits.safeLoan / STEP).toInt() * STEP
    val canBorrow = maxLoan >= 2 * STEP
    val amount = if (canBorrow) loanAmount.coerceIn(MIN_LOAN, maxLoan) else 0f

    val results: List<QuoteResult> = if (quotes.isEmpty() || !canBorrow) {
        emptyList()
    } else {
        LoanQuotes.compare(baseline, amount.toDouble(), tenureMonths, quotes)
    }
    val selected = results.firstOrNull { it.quote.bankName == selectedName } ?: results.firstOrNull()
    val candidate = buildQuote(nameText, rateText)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LoanColors.WarmCream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("PLAN SAFELY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LoanColors.DustyTeal)
                Spacer(Modifier.height(4.dp))
                Text("Loan simulator", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(LoanColors.AiLavender.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text("✨", fontSize = 18.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        LoanAmountCard(
            amount = amount,
            maxLoan = maxLoan,
            safeLoan = safeLoan,
            canBorrow = canBorrow,
            hasIncome = baseline.income > 0,
            limitRate = limitRate,
            rateIsAssumed = rateIsAssumed,
            onAmountChange = { loanAmount = (it / STEP).roundToInt() * STEP },
            tenureMonths = tenureMonths,
            onTenureChange = { tenureMonths = it }
        )

        Spacer(Modifier.height(16.dp))
        AddQuoteCard(
            nameText = nameText, onName = { if (it.length <= 24) nameText = it },
            rateText = rateText, onRate = { rateText = it },
            canAdd = candidate != null,
            onAdd = {
                if (candidate != null) {
                    updateQuotes(
                        quotes.filter { !it.bankName.equals(candidate.bankName, ignoreCase = true) } + candidate
                    )
                    selectedName = candidate.bankName
                    nameText = ""
                    rateText = ""
                }
            }
        )

        Spacer(Modifier.height(16.dp))
        if (quotes.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(LoanColors.AiLavender.copy(alpha = 0.3f))
                    .padding(20.dp)
            ) {
                Text("No quotes yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Get the interest rate from your bank and add it above. The app will show the EMI, " +
                            "total interest and whether the loan is safe for you. Add several banks to compare them.",
                    fontSize = 13.sp,
                    color = LoanColors.TextDark
                )
            }
        } else if (selected != null) {
            CompareCard(
                rows = results,
                selectedName = selected.quote.bankName,
                onSelect = { selectedName = it },
                onDelete = { name -> updateQuotes(quotes.filter { it.bankName != name }) }
            )

            Spacer(Modifier.height(16.dp))
            EmiCard(selected, tenureMonths)

            Spacer(Modifier.height(16.dp))
            HealthImpactCard(
                newScore = selected.verdict.after.score,
                baselineScore = selected.verdict.before.score,
                risk = selected.verdict.after.risk
            )

            Spacer(Modifier.height(16.dp))
            VerdictCard(selected.verdict)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoanAmountCard(
    amount: Float,
    maxLoan: Float,
    safeLoan: Float,
    canBorrow: Boolean,
    hasIncome: Boolean,
    limitRate: Double,
    rateIsAssumed: Boolean,
    onAmountChange: (Float) -> Unit,
    tenureMonths: Int,
    onTenureChange: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LoanColors.CardWhite)
            .padding(20.dp)
    ) {
        if (canBorrow) {
            Text("LOAN AMOUNT", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
            Text(
                rupees(amount.toDouble()),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = LoanColors.TextDark
            )
            Spacer(Modifier.height(8.dp))

            // The slider's maximum is the most this user can borrow, not a fixed number.
            Slider(
                value = amount,
                onValueChange = onAmountChange,
                valueRange = MIN_LOAN..maxLoan,
                colors = SliderDefaults.colors(
                    thumbColor = LoanColors.DustyTeal,
                    activeTrackColor = LoanColors.DustyTeal,
                    inactiveTrackColor = Color(0xFFE0E0E0)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("₹10K", fontSize = 11.sp, color = Color.Gray)
                Text("Max ${rupees(maxLoan.toDouble())}", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(8.dp))

            val comfortable = if (safeLoan >= MIN_LOAN) {
                "Comfortable up to ${rupees(safeLoan.toDouble())} (EMIs within 35% of income)."
            } else {
                "There is no comfortable amount at this tenure (EMIs would pass 35% of income)."
            }
            val rateNote = if (rateIsAssumed) {
                "an assumed ${"%.1f".format(limitRate)}% rate (add a bank quote to use a real one)"
            } else {
                "${"%.2f".format(limitRate)}%, the highest rate among your quotes"
            }
            Text(
                "Most you can borrow: ${rupees(maxLoan.toDouble())}. Total EMIs may use up to 50% of your " +
                        "income, and expenses + EMI must stay within it. Worked out at $rateNote for " +
                        "${tenureMonths / 12} yr. $comfortable This is an estimate, not a bank sanction.",
                fontSize = 11.sp,
                color = Color.Gray
            )
        } else {
            Text("LOAN AMOUNT", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                if (!hasIncome) {
                    "Enter your monthly income above to see how much you can borrow."
                } else {
                    "With your current income, spending and EMIs there is no room for a new loan right now. " +
                            "Try a longer tenure, or lower your monthly spending."
                },
                fontSize = 13.sp,
                color = LoanColors.TextDark
            )
        }

        Spacer(Modifier.height(16.dp))
        Text("Tenure", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceChip("1 yr", selected = tenureMonths == 12) { onTenureChange(12) }
            ChoiceChip("3 yrs", selected = tenureMonths == 36) { onTenureChange(36) }
            ChoiceChip("5 yrs", selected = tenureMonths == 60) { onTenureChange(60) }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LoanColors.Sage else Color(0xFFF0F0F0))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Color.White else Color.Gray
        )
    }
}

@Composable
private fun AddQuoteCard(
    nameText: String, onName: (String) -> Unit,
    rateText: String, onRate: (String) -> Unit,
    canAdd: Boolean, onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LoanColors.CardWhite)
            .padding(20.dp)
    ) {
        Text("ADD A BANK QUOTE", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Text(
            "Type the interest rate your bank quoted. Rates change, so always use the bank's latest quote.",
            fontSize = 12.sp,
            color = Color.Gray
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = nameText,
                onValueChange = onName,
                label = { Text("Bank name", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.weight(1.4f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LoanColors.DustyTeal)
            )
            OutlinedTextField(
                value = rateText,
                onValueChange = { new ->
                    val dots = new.count { it == '.' }
                    if (new.length <= 5 && dots <= 1 && new.all { it.isDigit() || it == '.' }) {
                        onRate(new)
                    }
                },
                label = { Text("Rate % p.a.", fontSize = 12.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LoanColors.DustyTeal)
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(if (canAdd) LoanColors.DustyTeal else Color(0xFFE0E0E0))
                .then(if (canAdd) Modifier.clickable { onAdd() } else Modifier)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Add quote",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (canAdd) Color.White else Color.Gray
            )
        }
    }
}

/** All saved quotes, ranked. Tap a row to select it, tap ✕ to delete it. */
@Composable
private fun CompareCard(
    rows: List<QuoteResult>,
    selectedName: String,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val best = rows.first()
    val headline = when (best.verdict.verdict) {
        VerdictEngine.Verdict.SAFE -> "Cheapest safe option: ${best.quote.bankName}"
        VerdictEngine.Verdict.TIGHT -> "Cheapest option (budget gets tight): ${best.quote.bankName}"
        VerdictEngine.Verdict.RISKY -> "No quote is safe at this amount. Try a smaller amount or a longer tenure."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LoanColors.CardWhite)
            .padding(20.dp)
    ) {
        Text("YOUR QUOTES", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(4.dp))
        Text(headline, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        Spacer(Modifier.height(12.dp))

        rows.forEachIndexed { index, row ->
            QuoteRow(
                row = row,
                isBest = index == 0,
                isSelected = row.quote.bankName == selectedName,
                onClick = { onSelect(row.quote.bankName) },
                onDelete = { onDelete(row.quote.bankName) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "EMI = P × r × (1+r)^n ÷ ((1+r)^n − 1), where r = rate ÷ 12 ÷ 100 and n = months " +
                    "(reducing balance, the usual bank method). Total interest = EMI × months − loan amount. " +
                    "Processing fees and other charges are not included.",
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun QuoteRow(
    row: QuoteResult,
    isBest: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dotColor = when (row.verdict.verdict) {
        VerdictEngine.Verdict.SAFE -> LoanColors.Sage
        VerdictEngine.Verdict.TIGHT -> LoanColors.Peach
        VerdictEngine.Verdict.RISKY -> LoanColors.Blush
    }
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) LoanColors.Sage.copy(alpha = 0.2f) else Color(0xFFF7F7F7))
            .then(if (isSelected) Modifier.border(1.5.dp, LoanColors.DustyTeal, shape) else Modifier)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.quote.bankName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LoanColors.TextDark)
                if (isBest) {
                    Spacer(Modifier.width(6.dp))
                    Text("BEST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LoanColors.DustyTeal)
                }
            }
            Text("${"%.2f".format(row.quote.annualRate)}% p.a.", fontSize = 11.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${rupees(row.emi)}/mo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
            Text("interest ${rupees(row.totalInterest)}", fontSize = 11.sp, color = Color.Gray)
        }
        Text(
            "✕",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier
                .clickable { onDelete() }
                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp, end = 2.dp)
        )
    }
}

@Composable
private fun EmiCard(selected: QuoteResult, tenureMonths: Int) {
    val totalPayable = selected.emi * tenureMonths
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LoanColors.Sage)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    "ESTIMATED EMI · ${selected.quote.bankName.uppercase()}",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(rupees(selected.emi), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("/mo", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("INTEREST", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                Text(
                    "${"%.2f".format(selected.quote.annualRate)}% p.a.",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Total interest ${rupees(selected.totalInterest)}  ·  Total payable ${rupees(totalPayable)}",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun HealthImpactCard(
    newScore: Int,
    baselineScore: Int,
    risk: ScoreCalculator.RiskLevel
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(LoanColors.CardWhite)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("HEALTH SCORE IMPACT", fontSize = 11.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$newScore", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
                    Spacer(Modifier.width(6.dp))
                    Text("from $baselineScore", fontSize = 12.sp, color = Color.Gray)
                }
            }
            val (badgeLabel, badgeColor) = when (risk) {
                ScoreCalculator.RiskLevel.LOW -> "Low risk" to LoanColors.Sage
                ScoreCalculator.RiskLevel.MEDIUM -> "Medium risk" to LoanColors.Peach
                ScoreCalculator.RiskLevel.HIGH -> "High risk" to LoanColors.Blush
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(badgeColor)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(badgeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier.fillMaxWidth().height(18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(LoanColors.Blush, LoanColors.Peach, LoanColors.Sage)
                        )
                    )
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(newScore.coerceIn(1, 99).toFloat()))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(3.dp, LoanColors.DustyTeal, CircleShape)
                )
                Spacer(Modifier.weight((100 - newScore).coerceIn(1, 99).toFloat()))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Risky", fontSize = 10.sp, color = Color.Gray)
            Text("Balanced", fontSize = 10.sp, color = Color.Gray)
            Text("Healthy", fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun VerdictCard(result: VerdictEngine.VerdictResult) {
    val (title, color) = when (result.verdict) {
        VerdictEngine.Verdict.SAFE -> "Safe to take this loan" to LoanColors.Sage
        VerdictEngine.Verdict.TIGHT -> "Manageable, but it's tight" to LoanColors.Peach
        VerdictEngine.Verdict.RISKY -> "Risky — think twice" to LoanColors.Blush
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.3f))
            .padding(20.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        Spacer(Modifier.height(12.dp))
        result.reasons.forEach { reason ->
            Row(modifier = Modifier.padding(vertical = 3.dp)) {
                Text("•  ", fontSize = 13.sp, color = LoanColors.TextDark)
                Text(reason, fontSize = 13.sp, color = LoanColors.TextDark)
            }
        }
    }
}