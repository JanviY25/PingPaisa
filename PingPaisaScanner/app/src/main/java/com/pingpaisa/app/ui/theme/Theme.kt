package com.pingpaisa.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette sampled from the reference screenshots. */
object PP {
    val Charcoal = Color(0xFF1B2220)
    val Surface = Color(0xFF252E2A)
    val Field = Color(0xFF2D3833)
    val Divider = Color(0xFF3A4741)
    val TextHi = Color(0xFFEAF0EB)
    val TextLo = Color(0xFF9FB0A6)
    val Accent = Color(0xFFBFE8A8)
    val AccentDark = Color(0xFF2D5A3D)
    val ChipBg = Color(0xFF2E3A35)
    val ScanLine = Color(0xFFBFE8A8)
    val NavBg = Color(0xFFF2F0EA)
    val NavIdle = Color(0xFF8C948F)
    val NavActive = Color(0xFF2D5A3D)
    val Ink = Color(0xFF1D2B23)
    val InkSoft = Color(0xFF3E5246)
    val VerdictLabel = Color(0xFF4F6F7D)
    val Error = Color(0xFFFF9C94)
}

@Composable
fun PingPaisaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = PP.Accent, onPrimary = PP.Ink, background = PP.Charcoal, onBackground = PP.TextHi,
            surface = PP.Surface, onSurface = PP.TextHi, surfaceVariant = PP.Field, onSurfaceVariant = PP.TextLo,
            outline = PP.Divider, error = PP.Error, secondaryContainer = PP.AccentDark, onSecondaryContainer = PP.TextHi
        ),
        content = content
    )
}
