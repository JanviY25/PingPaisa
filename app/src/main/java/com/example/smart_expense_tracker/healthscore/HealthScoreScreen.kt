package com.example.smart_expense_tracker.healthscore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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

/** Official PingPaisa design system colors (kept local to avoid merge conflicts). */
private object ScoreColors {
    val Sage = Color(0xFFA8C4A2)
    val DustyTeal = Color(0xFF7FA99B)
    val Blush = Color(0xFFF4C6C6)
    val Peach = Color(0xFFF2D3B3)
    val AiLavender = Color(0xFFC8BFE7)
    val WarmCream = Color(0xFFFAF7F2)

    val CardWhite = Color(0xFFFFFFFF)
    val TextDark = Color(0xFF2E2E2E)
    val NeedleColor = Color(0xFF3E4A3D)

    val GaugeLow = Blush
    val GaugeMid = Peach
    val GaugeHigh = Sage

    val RiskLow = Sage
    val RiskMedium = Peach
    val RiskHigh = Blush
}

@Composable
fun HealthScoreScreen(
    result: ScoreCalculator.ScoreResult,
    aiInsight: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScoreColors.WarmCream)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        GaugeCard(score = result.score, risk = result.risk)
        Spacer(Modifier.height(20.dp))
        FactorBreakdownCard(f = result.factors)
        Spacer(Modifier.height(20.dp))
        AiInsightCard(insight = aiInsight)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun GaugeCard(score: Int, risk: ScoreCalculator.RiskLevel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(ScoreColors.Sage.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .align(Alignment.TopEnd)
                .offset(x = 30.dp, y = (-30).dp)
                .clip(CircleShape)
                .background(ScoreColors.DustyTeal.copy(alpha = 0.25f))
        )

        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                "FINANCIAL HEALTH",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ScoreColors.DustyTeal
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Financial health",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = ScoreColors.TextDark
            )
            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    ScoreGauge(score = score)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$score",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = ScoreColors.TextDark
                        )
                        Text("/ 100", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                Spacer(Modifier.height(8.dp))
                RiskBadge(risk)
            }
        }
    }
}

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

        drawArc(
            color = ScoreColors.GaugeLow,
            startAngle = startAngle,
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = ScoreColors.GaugeMid,
            startAngle = startAngle + segmentSweep,
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = ScoreColors.GaugeHigh,
            startAngle = startAngle + (segmentSweep * 2),
            sweepAngle = segmentSweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

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
        drawCircle(color = ScoreColors.NeedleColor, radius = 8.dp.toPx(), center = centerOffset)
    }
}

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
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ScoreColors.TextDark)
    }
}

private fun pctText(p: Int): String = if (p >= 100) "100%+" else "$p%"

@Composable
private fun FactorBreakdownCard(f: ScoreCalculator.NormalizedFactors) {
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
            color = ScoreColors.TextDark
        )
        Spacer(Modifier.height(16.dp))

        FactorBar(
            label = "Savings rate",
            value = f.savings,
            color = ScoreColors.Sage,
            detail = if (f.savingsRatePercent < 0) "You're spending more than you earn"
            else "You save ${f.savingsRatePercent}% of income (30%+ = full marks)"
        )
        Spacer(Modifier.height(14.dp))
        FactorBar(
            label = "EMI burden",
            value = f.emi,
            color = ScoreColors.Blush,
            detail = "EMIs use ${pctText(f.emiRatioPercent)} of income (50%+ = zero marks)"
        )
        Spacer(Modifier.height(14.dp))
        FactorBar(
            label = "Spending consistency",
            value = f.consistency,
            color = ScoreColors.AiLavender,
            detail = if (f.consistencyKnown) "How steady your month-to-month spending is"
            else "Needs 2+ months of data, counted as neutral for now"
        )
        Spacer(Modifier.height(14.dp))
        FactorBar(
            label = "Recurring reliability",
            value = f.reliability,
            color = ScoreColors.Peach,
            detail = if (f.reliabilityKnown) "Share of recurring payments made on schedule"
            else "No recurring payments detected yet, counted as neutral"
        )
    }
}

@Composable
private fun FactorBar(label: String, value: Double, color: Color, detail: String) {
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
        Spacer(Modifier.height(4.dp))
        Text(detail, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun AiInsightCard(insight: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(ScoreColors.AiLavender.copy(alpha = 0.35f))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("✨", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "AI RECOMMENDATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ScoreColors.DustyTeal
            )
            Spacer(Modifier.height(4.dp))
            Text(insight, fontSize = 14.sp, color = ScoreColors.TextDark)
        }
    }
}