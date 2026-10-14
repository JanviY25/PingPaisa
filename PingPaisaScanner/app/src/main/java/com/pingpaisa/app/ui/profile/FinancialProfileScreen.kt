@file:OptIn(ExperimentalLayoutApi::class)

package com.pingpaisa.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pingpaisa.app.finance.AffordabilityCalculator
import com.pingpaisa.app.finance.FinancialProfile
import com.pingpaisa.app.finance.RiskTolerance
import com.pingpaisa.app.ui.theme.PP
import com.pingpaisa.app.ui.theme.fieldColors
import com.pingpaisa.app.util.Money
import kotlin.math.roundToInt

private fun amt(v: Double): String = if (v <= 0.0) "" else if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

@Composable
fun FinancialProfileScreen(initial: FinancialProfile?, onSave: (FinancialProfile) -> Unit, onBack: () -> Unit) {
    val start = initial ?: FinancialProfile()
    var income by remember { mutableStateOf(amt(start.netIncome)) }
    var extra by remember { mutableStateOf(amt(start.extraIncome)) }
    var savings by remember { mutableStateOf(amt(start.savings)) }
    var reserve by remember { mutableStateOf(amt(start.emergencyReserve)) }
    var fixed by remember { mutableStateOf(amt(start.fixedExpenses)) }
    var variable by remember { mutableStateOf(amt(start.variableExpenses)) }
    var target by remember { mutableStateOf(amt(start.monthlySavingsTarget)) }
    var emi by remember { mutableStateOf(amt(start.extraDebtEmi)) }
    var risk by remember { mutableStateOf(start.risk) }
    var alpha by remember { mutableStateOf(start.discretionaryShare.toFloat()) }
    var beta by remember { mutableStateOf(start.savingsSpendShare.toFloat()) }
    var months by remember { mutableStateOf(start.maxWaitMonths.toFloat()) }
    var showErrors by remember { mutableStateOf(false) }

    fun num(s: String): Double = Money.parse(s) ?: 0.0
    fun bad(s: String): Boolean = s.isNotBlank() && Money.parse(s) == null

    val profile = FinancialProfile(
        netIncome = num(income), extraIncome = num(extra), savings = num(savings), emergencyReserve = num(reserve),
        fixedExpenses = num(fixed), variableExpenses = num(variable), monthlySavingsTarget = num(target), extraDebtEmi = num(emi),
        discretionaryShare = alpha.toDouble(), savingsSpendShare = beta.toDouble(), maxWaitMonths = months.roundToInt(), risk = risk
    )
    val anyBad = listOf(income, extra, savings, reserve, fixed, variable, target, emi).any { bad(it) }
    val problems = profile.problems()
    val canSave = !anyBad && problems.isEmpty()
    val budget = if (canSave) AffordabilityCalculator.budget(profile) else null

    @Composable
    fun field(label: String, value: String, set: (String) -> Unit, hint: String, required: Boolean = false) {
        val err = bad(value) || (showErrors && required && num(value) <= 0.0)
        OutlinedTextField(
            value = value, onValueChange = { v -> set(v.filter { it.isDigit() || it == '.' || it == ',' }.take(13)) },
            label = { Text(label) }, prefix = { Text("₹ ") }, singleLine = true, isError = err,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), colors = fieldColors(),
            shape = RoundedCornerShape(14.dp),
            supportingText = { Text(if (err) "Enter a valid amount" else Money.parse(value)?.takeIf { it > 0 }?.let { Money.format(it) + " · " + hint } ?: hint) },
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
        )
    }

    Column(Modifier.fillMaxSize().background(PP.Charcoal).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back", tint = PP.TextHi) }
            Text("Financial profile", color = PP.TextHi, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 18.dp)) {
            Text(
                "Used only on this device to decide what you can afford. Amounts are monthly unless stated.",
                color = PP.TextLo, fontSize = 12.sp, modifier = Modifier.padding(bottom = 12.dp)
            )
            Heading("Income")
            field("Monthly take-home income", income, { income = it }, "after tax", required = true)
            field("Extra monthly income (optional)", extra, { extra = it }, "count only what is reliable")
            Heading("Savings")
            field("Current savings", savings, { savings = it }, "everything you could draw on")
            field("Emergency reserve to keep untouched", reserve, { reserve = it }, "never counted as spendable")
            field("Monthly savings target", target, { target = it }, "set aside every month")
            Heading("Monthly spending")
            field("Fixed expenses", fixed, { fixed = it }, "rent, bills, EMIs you already pay")
            field("Variable expenses", variable, { variable = it }, "food, travel, fun - your estimate")
            field("Extra debt EMI (optional)", emi, { emi = it }, "only if NOT already in fixed expenses")
            Heading("How careful should the verdict be?")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiskTolerance.values().forEach { r ->
                    FilterChip(selected = risk == r, onClick = {
                        risk = r; alpha = r.alpha.toFloat(); beta = r.beta.toFloat(); months = r.maxWaitMonths.toFloat()
                    }, label = { Text(r.label) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Slide("Share of leftover monthly money you may spend (α)", "${(alpha * 100).roundToInt()}%", alpha, 0f..1f) { alpha = it }
            Slide("Share of savings above reserve you may spend (β)", "${(beta * 100).roundToInt()}%", beta, 0f..1f) { beta = it }
            Slide("Longest you'd wait for something", "${months.roundToInt()} months", months, 1f..36f) { months = it }
            Text(
                "These are your own policy settings, not financial rules. Lower numbers make the app stricter.",
                color = PP.TextLo, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )

            Box(Modifier.fillMaxWidth().background(PP.Surface, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Column {
                    Text("YOUR NUMBERS", color = PP.TextLo, fontSize = 10.sp, letterSpacing = 1.2.sp)
                    if (budget != null) {
                        Spacer(Modifier.height(6.dp))
                        Row2("Left each month (D)", Money.format(budget.disposable))
                        Row2("Safe purchase budget", Money.format(budget.safeBudget))
                        if (budget.hasShortfall) Text(
                            "Your spending and savings target exceed your income, so nothing is left to spend each month.",
                            color = PP.Error, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    } else Text(
                        if (anyBad) "Fix the highlighted amounts." else problems.firstOrNull() ?: "Fill in the form.",
                        color = PP.TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.fillMaxWidth().background(PP.Charcoal).navigationBarsPadding().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Button(
                onClick = { if (canSave) onSave(profile) else showErrors = true },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PP.Accent, contentColor = PP.Ink)
            ) { Text("Save profile", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Heading(t: String) =
    Text(t.uppercase(), color = PP.Accent, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))

@Composable
private fun Slide(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(label, color = PP.TextHi, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(valueText, color = PP.Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
    Slider(
        value = value, onValueChange = onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = PP.Accent, activeTrackColor = PP.Accent, inactiveTrackColor = PP.Divider)
    )
}

@Composable
private fun Row2(l: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(l, color = PP.TextLo, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(v, color = PP.TextHi, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
