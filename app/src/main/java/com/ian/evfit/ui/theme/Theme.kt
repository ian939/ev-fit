// EV FIT 색·글꼴 토큰과 다크 테마(v2 "밤의 회랑") — 한글 UI는 Wanted Sans, 큰 숫자는 Barlow Semi Condensed
package com.ian.evfit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ian.evfit.R

object EvFitColors {
    val Navy = Color(0xFF071F41)          // Deep Navy — 바탕
    val Panel = Color(0xFF0A2850)         // 오른쪽 패널·면
    val Card = Color(0xFF0D2A52)          // 낮은 면(리포트 묶음)
    val CardAlt = Color(0xFF12366A)       // 선택·강조 면
    val Hairline = Color(0xFF1E4275)      // 구분선·시도 경계
    val Land = Color(0xFF0E2C57)          // 지도 육지
    val Sea = Color(0xFF041530)           // 지도 바다
    val TextPrimary = Color(0xFFEAF3FF)   // Soft Blue — 본문(패널 위 12:1)
    val TextSecondary = Color(0xFFA9BDD8) // 보조 글자(7:1)
    val AccentText = Color(0xFF5BA5FF)    // 강조 글자(5.6:1)
    val ActionBg = Color(0xFF1F64D6)      // 글자가 올라가는 버튼(흰 글씨 5.5:1)
    val FitBlueFill = Color(0xFF2778F6)   // FIT Blue — 경로선·차트 면(글자 없는 면)
    val Saving = Color(0xFF3DDC97)        // Charge Mint — 아낀 돈·도달 가능
    val Warning = Color(0xFFFFB547)       // Caution Amber — 합성·추정·도달 한계
    val Disabled = Color(0xFF6B7F9C)
}

@OptIn(ExperimentalTextApi::class)
private fun wanted(weight: Int) = Font(
    R.font.wanted_sans_variable, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** 한글 UI 글꼴(Wanted Sans Variable, OFL) */
val Wanted = FontFamily(wanted(400), wanted(500), wanted(600), wanted(700), wanted(800))

/** 계기판 숫자 글꼴(Barlow Semi Condensed, OFL) — 금액·SOC·kW·분 */
val Gauge = FontFamily(
    Font(R.font.barlow_sc_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_sc_bold, FontWeight.Bold),
)

/** 큰 숫자 스타일 — 숫자 폭을 고르게(tnum) */
fun gauge(size: TextUnit, weight: FontWeight = FontWeight.Bold, color: Color = EvFitColors.TextPrimary) =
    TextStyle(fontFamily = Gauge, fontSize = size, fontWeight = weight, color = color, fontFeatureSettings = "tnum", letterSpacing = 0.sp)

private val scheme = darkColorScheme(
    primary = EvFitColors.ActionBg,
    onPrimary = Color.White,
    secondary = EvFitColors.AccentText,
    onSecondary = EvFitColors.Navy,
    background = EvFitColors.Navy,
    onBackground = EvFitColors.TextPrimary,
    surface = EvFitColors.Panel,
    onSurface = EvFitColors.TextPrimary,
    surfaceVariant = EvFitColors.CardAlt,
    onSurfaceVariant = EvFitColors.TextSecondary,
)

private fun t(size: Int, weight: FontWeight, line: Int) =
    TextStyle(fontFamily = Wanted, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = (-0.2).sp)

// 15 보조 / 18 본문 / 22 소제목 / 28 화면 제목 / 34 판정 문장 — 차 화면이라 15 미만은 쓰지 않는다
private val typography = Typography(
    headlineLarge = t(34, FontWeight.ExtraBold, 42),
    headlineMedium = t(28, FontWeight.ExtraBold, 36),
    titleLarge = t(24, FontWeight.Bold, 32),
    titleMedium = t(21, FontWeight.Bold, 28),
    titleSmall = t(19, FontWeight.Bold, 26),
    bodyLarge = t(19, FontWeight.Medium, 28),
    bodyMedium = t(17, FontWeight.Medium, 25),
    bodySmall = t(15, FontWeight.Medium, 21),
    labelLarge = t(19, FontWeight.Bold, 24),
    labelMedium = t(17, FontWeight.SemiBold, 22),
    labelSmall = t(15, FontWeight.SemiBold, 20),
)

@Composable
fun EvFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
