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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private object LoanColors {
    val Sage = Color(0xFFA8C4A2)
    val DustyTeal = Color(0xFF7FA99B)
    val Blush = Color(0xFFF4C6C6)
    val Peach = Color(0xFFF2D3B3)
    val WarmCream = Color(0xFFFAF7F2)
    val TextDark = Color(0xFF2E2E2E)
    val Muted = Color(0xFF8A8A8A)
}

private val CardShape = RoundedCornerShape(28.dp)

private const val MIN_LOAN = 10000f
private const val STEP = 10000f
private const val MAX_TENURE_YEARS = 30

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
    var showCustomTenure by remember { mutableStateOf(false) }
    var customYearsText by remember { mutableStateOf("") }
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        ScreenHeader()

        Spacer(Modifier.height(18.dp))
        AmountCard(
            amount = amount,
            maxLoan = maxLoan,
            safeLoan = safeLoan,
            canBorrow = canBorrow,
            hasIncome = baseline.income > 0,
            limitRate = limitRate,
            rateIsAssumed = rateIsAssumed,
            tenureMonths = tenureMonths,
            onAmountChange = { loanAmount = (it / STEP).roundToInt() * STEP },
            showCustomTenure = showCustomTenure,
            customYearsText = customYearsText,
            onPresetTenure = { months ->
                tenureMonths = months
                showCustomTenure = false
            },
            onToggleCustom = {
                showCustomTenure = !showCustomTenure
                if (showCustomTenure) customYearsText = (tenureMonths / 12).toString()
            },
            onCustomYears = { text ->
                if (text.length <= 2 && text.all { it.isDigit() }) {
                    customYearsText = text
                    val years = text.toIntOrNull()
                    if (years != null && years in 1..MAX_TENURE_YEARS) tenureMonths = years * 12
                }
            }
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
            EmptyQuotesCard()
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

// ------------------------------------------------------------------
// Building blocks
// ------------------------------------------------------------------

/** Soft, raised card: gentle shadow, subtle gradient and a light edge (the "clay" look). */
@Composable
private fun ClayCard(
    modifier: Modifier = Modifier,
    background: Brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFFCFAF6))),
    padding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = CardShape,
                ambientColor = Color(0x1F5C6B5A),
                spotColor = Color(0x3D5C6B5A)
            )
            .clip(CardShape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = 0.9f), CardShape)
            .padding(padding),
        content = content
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = LoanColors.Muted
    )
}

@Composable
private fun IconBadge(emoji: String, tint: Color, size: Dp = 40.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.35f)),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = 18.sp)
    }
}

@Composable
private fun StatPill(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tint.copy(alpha = 0.28f))
            .padding(12.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color(0xFF6B6B6B))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
    }
}

/** A small "how is this calculated?" line that expands to show the explanation. */
@Composable
private fun InfoToggle(label: String, text: String) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(
            if (open) "▾ $label" else "▸ $label",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = LoanColors.DustyTeal,
            modifier = Modifier
                .clickable { open = !open }
                .padding(vertical = 6.dp)
        )
        if (open) {
            Text(text, fontSize = 11.sp, lineHeight = 16.sp, color = LoanColors.Muted)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) LoanColors.DustyTeal else Color(0xFFF1EEE8))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Color.White else Color(0xFF6B6B6B)
        )
    }
}

@Composable
private fun VerdictPill(verdict: VerdictEngine.Verdict) {
    val (label, color) = when (verdict) {
        VerdictEngine.Verdict.SAFE -> "Safe" to LoanColors.Sage
        VerdictEngine.Verdict.TIGHT -> "Tight" to LoanColors.Peach
        VerdictEngine.Verdict.RISKY -> "Risky" to LoanColors.Blush
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = LoanColors.TextDark)
    }
}

// ------------------------------------------------------------------
// Screen sections
// ------------------------------------------------------------------

@Composable
private fun ScreenHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("PLAN SAFELY", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = LoanColors.DustyTeal)
            Spacer(Modifier.height(2.dp))
            Text("Loan simulator", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        }
        IconBadge("🏦", LoanColors.Sage, size = 46.dp)
    }
}

@Composable
private fun AmountCard(
    amount: Float,
    maxLoan: Float,
    safeLoan: Float,
    canBorrow: Boolean,
    hasIncome: Boolean,
    limitRate: Double,
    rateIsAssumed: Boolean,
    tenureMonths: Int,
    onAmountChange: (Float) -> Unit,
    showCustomTenure: Boolean,
    customYearsText: String,
    onPresetTenure: (Int) -> Unit,
    onToggleCustom: () -> Unit,
    onCustomYears: (String) -> Unit
) {
    ClayCard(background = Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F6EF)))) {
        if (canBorrow) {
            SectionLabel("HOW MUCH DO YOU NEED?")
            Spacer(Modifier.height(4.dp))
            Text(
                Money.inr(amount.toDouble()),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = LoanColors.TextDark
            )
            Spacer(Modifier.height(6.dp))

            // The slider's maximum is the most this user can borrow, not a fixed number.
            Slider(
                value = amount,
                onValueChange = onAmountChange,
                valueRange = MIN_LOAN..maxLoan,
                colors = SliderDefaults.colors(
                    thumbColor = LoanColors.DustyTeal,
                    activeTrackColor = LoanColors.DustyTeal,
                    inactiveTrackColor = Color(0xFFE6E1D8)
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(Money.compact(MIN_LOAN.toDouble()), fontSize = 11.sp, color = LoanColors.Muted)
                Text("Max ${Money.compact(maxLoan.toDouble())}", fontSize = 11.sp, color = LoanColors.Muted)
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill(
                    "You can borrow up to",
                    Money.inr(maxLoan.toDouble()),
                    LoanColors.Sage,
                    Modifier.weight(1f)
                )
                StatPill(
                    "Comfortable up to",
                    if (safeLoan >= MIN_LOAN) Money.inr(safeLoan.toDouble()) else "None",
                    LoanColors.Peach,
                    Modifier.weight(1f)
                )
            }

            val rateNote = if (rateIsAssumed) {
                "an assumed ${"%.1f".format(limitRate)}% rate (add a bank quote to use a real one)"
            } else {
                "${"%.2f".format(limitRate)}%, the highest rate among your quotes"
            }
            InfoToggle(
                "How is this calculated?",
                "Total EMIs may use up to 50% of your income, and expenses + EMI must stay within it. " +
                        "Worked out at $rateNote over ${tenureMonths / 12} yr. " +
                        "Comfortable means EMIs within 35% of income. " +
                        "This is an estimate, not a bank sanction."
            )
        } else {
            SectionLabel("HOW MUCH DO YOU NEED?")
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(LoanColors.Blush.copy(alpha = 0.35f))
                    .padding(14.dp)
            ) {
                Text(
                    if (!hasIncome) {
                        "Enter your monthly income above to see how much you can borrow."
                    } else {
                        "With your current income, spending and EMIs there is no room for a new loan right now. " +
                                "Try a longer repayment period, or lower your monthly spending."
                    },
                    fontSize = 13.sp,
                    color = LoanColors.TextDark
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel("REPAYMENT PERIOD")
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChoiceChip("1 yr", selected = !showCustomTenure && tenureMonths == 12) { onPresetTenure(12) }
            ChoiceChip("3 yrs", selected = !showCustomTenure && tenureMonths == 36) { onPresetTenure(36) }
            ChoiceChip("5 yrs", selected = !showCustomTenure && tenureMonths == 60) { onPresetTenure(60) }
            ChoiceChip("+", selected = showCustomTenure) { onToggleCustom() }
        }

        if (showCustomTenure) {
            Spacer(Modifier.height(10.dp))
            val years = customYearsText.toIntOrNull()
            OutlinedTextField(
                value = customYearsText,
                onValueChange = onCustomYears,
                label = { Text("Years (1 to $MAX_TENURE_YEARS)", fontSize = 12.sp) },
                singleLine = true,
                isError = customYearsText.isNotEmpty() && (years == null || years !in 1..MAX_TENURE_YEARS),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LoanColors.DustyTeal,
                    unfocusedBorderColor = Color(0xFFE5E0D8)
                )
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Selected: ${tenureMonths / 12} yr (${tenureMonths} months). A longer period lowers the EMI but increases total interest.",
            fontSize = 11.sp,
            color = LoanColors.Muted
        )
    }
}

@Composable
private fun AddQuoteCard(
    nameText: String, onName: (String) -> Unit,
    rateText: String, onRate: (String) -> Unit,
    canAdd: Boolean, onAdd: () -> Unit
) {
    ClayCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge("🏷️", LoanColors.Peach, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Add a bank quote", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
                Text(
                    "Use the rate your bank quoted. Rates change, so always use the latest.",
                    fontSize = 11.sp,
                    color = LoanColors.Muted
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = nameText,
                onValueChange = onName,
                label = { Text("Bank name", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1.4f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LoanColors.DustyTeal,
                    unfocusedBorderColor = Color(0xFFE5E0D8)
                )
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LoanColors.DustyTeal,
                    unfocusedBorderColor = Color(0xFFE5E0D8)
                )
            )
        }

        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(
                    if (canAdd) {
                        Brush.horizontalGradient(listOf(LoanColors.Sage, LoanColors.DustyTeal))
                    } else {
                        Brush.horizontalGradient(listOf(Color(0xFFE9E5DE), Color(0xFFE9E5DE)))
                    }
                )
                .then(if (canAdd) Modifier.clickable { onAdd() } else Modifier)
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Add quote",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (canAdd) Color.White else LoanColors.Muted
            )
        }
    }
}

@Composable
private fun EmptyQuotesCard() {
    ClayCard(background = Brush.verticalGradient(listOf(Color(0xFFF6F2EA), Color(0xFFF1EDE4)))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge("📋", LoanColors.Sage, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Text("No quotes yet", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Get the interest rate from your bank and add it above. You will see the EMI, total interest and " +
                    "whether the loan is safe for you. Add several banks to compare them.",
            fontSize = 13.sp,
            color = LoanColors.TextDark
        )
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
    val (title, subtitle) = when (best.verdict.verdict) {
        VerdictEngine.Verdict.SAFE ->
            "${best.quote.bankName} is the cheapest safe option" to "Lowest total interest among the quotes that fit your budget."
        VerdictEngine.Verdict.TIGHT ->
            "${best.quote.bankName} is cheapest, but your budget gets tight" to "No quote leaves you fully comfortable at this amount."
        VerdictEngine.Verdict.RISKY ->
            "No quote is safe at this amount" to "Try a smaller amount or a longer repayment period."
    }

    ClayCard {
        SectionLabel("YOUR QUOTES")
        Spacer(Modifier.height(4.dp))
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, fontSize = 12.sp, color = LoanColors.Muted)
        Spacer(Modifier.height(14.dp))

        rows.forEachIndexed { index, row ->
            QuoteRow(
                row = row,
                isBest = index == 0,
                isSelected = row.quote.bankName == selectedName,
                onClick = { onSelect(row.quote.bankName) },
                onDelete = { onDelete(row.quote.bankName) }
            )
            Spacer(Modifier.height(10.dp))
        }

        InfoToggle(
            "How is the EMI calculated?",
            "EMI = P × r × (1+r)^n ÷ ((1+r)^n − 1), where r = rate ÷ 12 ÷ 100 and n = months " +
                    "(reducing balance, the usual bank method). Total interest = EMI × months − loan amount. " +
                    "Processing fees and other charges are not included."
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
    val badgeColor = when (row.verdict.verdict) {
        VerdictEngine.Verdict.SAFE -> LoanColors.Sage
        VerdictEngine.Verdict.TIGHT -> LoanColors.Peach
        VerdictEngine.Verdict.RISKY -> LoanColors.Blush
    }
    val shape = RoundedCornerShape(20.dp)
    val initial = row.quote.bankName.firstOrNull()?.uppercase() ?: "?"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isSelected) LoanColors.Sage.copy(alpha = 0.18f) else Color(0xFFF8F5EF))
            .then(if (isSelected) Modifier.border(1.5.dp, LoanColors.DustyTeal, shape) else Modifier)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(badgeColor.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.quote.bankName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LoanColors.TextDark,
                    maxLines = 1
                )
                if (isBest) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(LoanColors.DustyTeal)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text("BEST", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${"%.2f".format(row.quote.annualRate)}% p.a.", fontSize = 11.sp, color = LoanColors.Muted)
                Spacer(Modifier.width(8.dp))
                VerdictPill(row.verdict.verdict)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${Money.inr(row.emi)}/mo", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
            Text("interest ${Money.inr(row.totalInterest)}", fontSize = 10.sp, color = LoanColors.Muted)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFEDE8DF))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Text("✕", fontSize = 11.sp, color = LoanColors.Muted)
        }
    }
}

@Composable
private fun GlassStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun EmiCard(selected: QuoteResult, tenureMonths: Int) {
    val totalPayable = selected.emi * tenureMonths
    ClayCard(background = Brush.linearGradient(listOf(LoanColors.Sage, LoanColors.DustyTeal))) {
        Text(
            "ESTIMATED EMI · ${selected.quote.bankName.uppercase()}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = Color.White.copy(alpha = 0.85f)
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Money.inr(selected.emi), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(4.dp))
            Text("/month", fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassStat("Interest rate", "${"%.2f".format(selected.quote.annualRate)}%", Modifier.weight(1f))
            GlassStat("Total interest", Money.inr(selected.totalInterest), Modifier.weight(1.2f))
            GlassStat("Total payable", Money.inr(totalPayable), Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun HealthImpactCard(
    newScore: Int,
    baselineScore: Int,
    risk: ScoreCalculator.RiskLevel
) {
    val delta = newScore - baselineScore
    val (deltaText, deltaColor) = when {
        delta < 0 -> "▼ ${-delta} pts" to Color(0xFFD9776F)
        delta > 0 -> "▲ $delta pts" to Color(0xFF6C9A8B)
        else -> "no change" to LoanColors.Muted
    }

    ClayCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                SectionLabel("HEALTH SCORE IMPACT")
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$newScore", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "from $baselineScore",
                        fontSize = 12.sp,
                        color = LoanColors.Muted,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Text(deltaText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = deltaColor)
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
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(badgeLabel, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
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
            Text("Risky", fontSize = 10.sp, color = LoanColors.Muted)
            Text("Balanced", fontSize = 10.sp, color = LoanColors.Muted)
            Text("Healthy", fontSize = 10.sp, color = LoanColors.Muted)
        }
    }
}

@Composable
private fun VerdictCard(result: VerdictEngine.VerdictResult) {
    val (title, color, glyph) = when (result.verdict) {
        VerdictEngine.Verdict.SAFE -> Triple("Safe to take this loan", LoanColors.Sage, "✓")
        VerdictEngine.Verdict.TIGHT -> Triple("Manageable, but it's tight", LoanColors.Peach, "!")
        VerdictEngine.Verdict.RISKY -> Triple("Risky — think twice", LoanColors.Blush, "✕")
    }

    ClayCard(background = Brush.verticalGradient(listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.18f)))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LoanColors.TextDark)
        }
        Spacer(Modifier.height(14.dp))
        result.reasons.forEach { reason ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(LoanColors.TextDark.copy(alpha = 0.6f))
                )
                Spacer(Modifier.width(10.dp))
                Text(reason, fontSize = 13.sp, lineHeight = 19.sp, color = LoanColors.TextDark)
            }
        }
    }
}