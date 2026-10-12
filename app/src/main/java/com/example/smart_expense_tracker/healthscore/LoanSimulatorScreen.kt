package com.example.smart_expense_tracker.healthscore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
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

private const val ASSUMED_ANNUAL_INTEREST_RATE = 10.5

private fun calculateEmi(principal: Double, tenureMonths: Int): Double {
    val monthlyRate = ASSUMED_ANNUAL_INTEREST_RATE / 12 / 100
    if (monthlyRate == 0.0) return principal / tenureMonths
    val factor = (1 + monthlyRate).pow(tenureMonths)
    return principal * monthlyRate * factor / (factor - 1)
}

@Composable
fun LoanSimulatorScreen(
    baseline: ScoreCalculator.RawFactors,
    baselineScore: Int,
    modifier: Modifier = Modifier
) {
    var loanAmount by remember { mutableStateOf(600000f) }
    var tenureMonths by remember { mutableStateOf(36) }

    val emi = calculateEmi(loanAmount.toDouble(), tenureMonths)

    val hypotheticalResult = ScoreCalculator.calculate(
        baseline.copy(totalEmiAmount = baseline.totalEmiAmount + emi)
    )

    val emiPercentOfIncome = if (baseline.income > 0) {
        ((emi / baseline.income) * 100).roundToInt()
    } else 0

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
            loanAmount = loanAmount,
            onLoanAmountChange = { loanAmount = it },
            tenureMonths = tenureMonths,
            onTenureChange = { tenureMonths = it }
        )

        Spacer(Modifier.height(16.dp))
        EmiCard(emi = emi)

        Spacer(Modifier.height(16.dp))
        HealthImpactCard(
            newScore = hypotheticalResult.score,
            baselineScore = baselineScore,
            risk = hypotheticalResult.risk,
            emiPercentOfIncome = emiPercentOfIncome
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LoanAmountCard(
    loanAmount: Float,
    onLoanAmountChange: (Float) -> Unit,
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
        Text("LOAN AMOUNT", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
        Text(
            "₹${"%,.0f".format(loanAmount)}",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = LoanColors.TextDark
        )
        Spacer(Modifier.height(8.dp))

        Slider(
            value = loanAmount,
            onValueChange = onLoanAmountChange,
            valueRange = 100000f..2000000f,
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
            Text("₹1L", fontSize = 11.sp, color = Color.Gray)
            Text("₹20L", fontSize = 11.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(16.dp))
        Text("Tenure", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TenureChip("1 yr", selected = tenureMonths == 12) { onTenureChange(12) }
            TenureChip("3 yrs", selected = tenureMonths == 36) { onTenureChange(36) }
            TenureChip("5 yrs", selected = tenureMonths == 60) { onTenureChange(60) }
        }
    }
}

@Composable
private fun TenureChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun EmiCard(emi: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LoanColors.Sage)
            .padding(20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column {
            Text("ESTIMATED EMI", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("₹${"%,.0f".format(emi)}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("/mo", fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("INTEREST", fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
            Text(
                "$ASSUMED_ANNUAL_INTEREST_RATE%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun HealthImpactCard(
    newScore: Int,
    baselineScore: Int,
    risk: ScoreCalculator.RiskLevel,
    emiPercentOfIncome: Int
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

        Box(modifier = Modifier.fillMaxWidth().height(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(LoanColors.Blush, LoanColors.Peach, LoanColors.Sage)
                        )
                    )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Risky", fontSize = 10.sp, color = Color.Gray)
            Text("Balanced", fontSize = 10.sp, color = Color.Gray)
            Text("Healthy", fontSize = 10.sp, color = Color.Gray)
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(LoanColors.AiLavender.copy(alpha = 0.35f))
                .padding(14.dp)
        ) {
            Text("✨", fontSize = 14.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "EMI would use $emiPercentOfIncome% of your monthly income.",
                fontSize = 13.sp,
                color = LoanColors.TextDark
            )
        }
    }
}