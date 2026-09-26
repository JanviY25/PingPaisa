package com.example.smart_expense_tracker.healthscore

import androidx.compose.foundation.Canvas
import androidx.compose.material3.Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/**
 * ============================================================
 * COLOR PALETTE — specific to the Health Score screen only.
 * Kept local to this file (not touching the app's shared
 * colors.xml) so it can't conflict with teammates' color changes.
 * Inspired by a warm coral -> gold -> green fintech gauge style.
 * ============================================================
 */
private object ScoreColors {
    val Background = Color(0xFFFAF7F2)      // warm cream, same base tone as rest of app
    val CardWhite = Color(0xFFFFFFFF)

    val GaugeCoral = Color(0xFFEDA98D)       // low-score end of the arc
    val GaugeGold = Color(0xFFEAD9A0)        // mid-score
    val GaugeGreen = Color(0xFFA8C4A2)       // high-score end of the arc
    val NeedleColor = Color(0xFF3E4A3D)      // dark green-gray needle

    val BarSavings = Color(0xFFA8C4A2)       // green — savings rate
    val BarEmi = Color(0xFFE8917A)           // coral/red — EMI-to-income (debt-like factor)
    val BarConsistency = Color(0xFFB6A6E0)   // purple — spending consistency
    val BarReliability = Color(0xFFEFC08A)   // orange/peach — recurring reliability

    val RiskLow = Color(0xFFA8C4A2)
    val RiskMedium = Color(0xFFEFC08A)
    val RiskHigh = Color(0xFFE8917A)

    val AiCardBackground = Color(0xFFE7E1F5) // soft lavender, reserved for AI content
    val AiCardText = Color(0xFF5B4E8C)
}

/**
 * Top-level screen. Pass in the already-computed ScoreCalculator.ScoreResult
 * (calculated in the ViewModel/Activity from real transaction+income data)
 * plus an optional AI-generated insight string (null while loading, or if
 * the API call failed — see Section 5.3 fallback logic, added later).
 */
@Composable
fun HealthScoreScreen(
    result: ScoreCalculator.ScoreResult,
    aiInsight: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScoreColors.Background)
            .padding(20.dp)
    ) {
        Text(
            "Financial Health",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E2E2E)
        )
        Spacer(Modifier.height(20.dp))

        GaugeCard(score = result.score, risk = result.risk)

        Spacer(Modifier.height(20.dp))

        FactorBreakdownCard(factors = result.factors)

        Spacer(Modifier.height(20.dp))

        AiInsightCard(insight = aiInsight)

        Spacer(Modifier.height(20.dp))
    }
}

/**
 * The big rounded card containing the circular gauge, the numeric score,
 * and the risk label underneath — mirrors the "Financial Health" hero
 * card look from the design reference.
 */
@Composable
private fun GaugeCard(score: Int, risk: ScoreCalculator.RiskLevel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ScoreColors.CardWhite)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            ScoreGauge(score = score)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$score",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E2E2E)
                )
                Text(
                    "/ 100",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        RiskBadge(risk)
    }
}

/**
 * Custom-drawn speedometer-style arc gauge using Compose Canvas.
 * The arc is split into 3 equal 90-degree segments (coral -> gold -> green)
 * covering a total sweep of 270 degrees, starting at the bottom-left (135°)
 * and ending at the bottom-right (405°) — same convention as a car speedometer.
 * The needle rotates to point at (score / 100) fraction of that sweep.
 */
@Composable
private fun ScoreGauge(score: Int) {
    val startAngle = 135f
    val totalSweep = 270f
    val segmentSweep = totalSweep / 3f

    Canvas(modifier = Modifier.size(220.dp)) {
        val strokeWidth = 22.dp.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(
            size.width - strokeWidth,
            size.height - strokeWidth
        )
        val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

        // Draw the 3 colored arc segments.
        drawArc(
            color = ScoreColors.GaugeCoral,
            startAngle = startAngle,
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = ScoreColors.GaugeGold,
            startAngle = startAngle + segmentSweep,
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = ScoreColors.GaugeGreen,
            startAngle = startAngle + (segmentSweep * 2),
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Draw the needle: rotates from startAngle across totalSweep based on score/100.
        val needleAngleDeg = startAngle + (totalSweep * (score / 100f))
        val needleAngleRad = Math.toRadians(needleAngleDeg.toDouble())
        val centerOffset = Offset(size.width / 2, size.height / 2)
        val needleLength = (size.minDimension / 2) - strokeWidth
        val needleEnd = Offset(
            x = centerOffset.x + (needleLength * cos(needleAngleRad)).toFloat(),
            y = centerOffset.y + (needleLength * sin(needleAngleRad)).toFloat()
        )

        drawLine(
            color = ScoreColors.NeedleColor,
            start = centerOffset,
            end = needleEnd,
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = ScoreColors.NeedleColor,
            radius = 8.dp.toPx(),
            center = centerOffset
        )
    }
}

/** Small pill badge showing the Low / Medium / High risk classification. */
@Composable
private fun RiskBadge(risk: ScoreCalculator.RiskLevel) {
    val (label, color) = when (risk) {
        ScoreCalculator.RiskLevel.LOW -> "LOW RISK" to ScoreColors.RiskLow
        ScoreCalculator.RiskLevel.MEDIUM -> "MEDIUM RISK" to ScoreColors.RiskMedium
        ScoreCalculator.RiskLevel.HIGH -> "HIGH RISK" to ScoreColors.RiskHigh
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

/** Card showing the 4 weighted factors as labeled horizontal progress bars. */
@Composable
private fun FactorBreakdownCard(factors: ScoreCalculator.NormalizedFactors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ScoreColors.CardWhite)
            .padding(20.dp)
    ) {
        Text(
            "What shapes your score",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2E2E2E)
        )
        Spacer(Modifier.height(16.dp))

        FactorBar("Savings rate", factors.savingsRate, ScoreColors.BarSavings)
        Spacer(Modifier.height(14.dp))
        // Note: EMI ratio is inverted for display, since a LOWER ratio is better —
        // showing (1 - ratio) so a fuller bar always means "healthier" like the others.
        FactorBar("EMI-to-income", 1.0 - factors.emiToIncomeRatio, ScoreColors.BarEmi)
        Spacer(Modifier.height(14.dp))
        FactorBar("Spending consistency", factors.spendingConsistency, ScoreColors.BarConsistency)
        Spacer(Modifier.height(14.dp))
        FactorBar("Recurring reliability", factors.recurringReliability, ScoreColors.BarReliability)
    }
}

@Composable
private fun FactorBar(label: String, value: Double, color: Color) {
    val percent = (value * 100).toInt().coerceIn(0, 100)
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 14.sp, color = Color(0xFF444444))
            Text("$percent/100", fontSize = 13.sp, color = Color.Gray)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xFFEDEDED))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = percent / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

/** Lavender AI-insight card — lavender is reserved app-wide for AI-generated content. */
@Composable
private fun AiInsightCard(insight: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ScoreColors.AiCardBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("✨", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "AI INSIGHT",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ScoreColors.AiCardText
            )
            Spacer(Modifier.height(4.dp))
            Text(insight, fontSize = 14.sp, color = ScoreColors.AiCardText)
        }
    }
}