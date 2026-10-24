// EV FIT 브랜드 색 토큰과 다크 테마 — 글자 대비 4.5:1 이상을 지키도록 FIT Blue는 글자 없는 면에만 쓴다
package com.ian.evfit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object EvFitColors {
    val Navy = Color(0xFF071F41)          // Deep Navy — 배경
    val Card = Color(0xFF0D2A52)          // 카드
    val CardAlt = Color(0xFF12366A)       // 강조 카드·선택
    val TextPrimary = Color(0xFFEAF3FF)   // Soft Blue — 본문(카드 위 12.7:1)
    val TextSecondary = Color(0xFFA9BDD8) // 보조 글자(7.4:1)
    val AccentText = Color(0xFF5BA5FF)    // 강조 글자(5.6:1)
    val ActionBg = Color(0xFF1F64D6)      // 글자가 올라가는 버튼·배지(흰 글씨 5.5:1)
    val FitBlueFill = Color(0xFF2778F6)   // FIT Blue — 차트·막대 등 글자 없는 면
    val Saving = Color(0xFF3DDC97)
    val Warning = Color(0xFFFFB547)
    val Disabled = Color(0xFF6B7F9C)
}

private val scheme = darkColorScheme(
    primary = EvFitColors.ActionBg,
    onPrimary = Color.White,
    secondary = EvFitColors.AccentText,
    onSecondary = EvFitColors.Navy,
    background = EvFitColors.Navy,
    onBackground = EvFitColors.TextPrimary,
    surface = EvFitColors.Card,
    onSurface = EvFitColors.TextPrimary,
    surfaceVariant = EvFitColors.CardAlt,
    onSurfaceVariant = EvFitColors.TextSecondary,
)

private val typography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 20.sp),
    bodyMedium = TextStyle(fontSize = 18.sp),
    labelLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
)

@Composable
fun EvFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
