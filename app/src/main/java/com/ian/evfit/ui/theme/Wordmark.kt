// EV FIT 로고 — 심볼 "회랑 링"(휘어 오는 경로 위 추천 링)과 텍스트 워드마크 "EV"(본문색) + "FIT"(강조 블루), Wanted Sans 가장 굵게
package com.ian.evfit.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

@Composable
fun EvFitWordmark(sizeSp: Int, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = EvFitColors.TextPrimary)) { append("EV ") }
            withStyle(SpanStyle(color = EvFitColors.AccentText)) { append("FIT") }
        },
        modifier = modifier,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = Wanted,
        letterSpacing = (-0.5).sp,
    )
}

/**
 * 심볼 "회랑 링" — 100×100 격자 좌표는 `브랜드/로고_시안.html` 1번, 런처 아이콘(`res/drawable/ic_launcher.xml`)과 같다.
 * 링 안은 경로선을 가리도록 놓이는 면의 색([ringFill])으로 채운다.
 */
@Composable
fun EvFitMark(size: Dp, modifier: Modifier = Modifier, ringFill: Color = EvFitColors.Bg) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 100f
        fun p(x: Float, y: Float) = Offset(x * k, y * k)
        val route = Path().apply {
            moveTo(12 * k, 84 * k)
            cubicTo(34 * k, 84 * k, 30 * k, 50 * k, 50 * k, 50 * k)
            cubicTo(70 * k, 50 * k, 66 * k, 16 * k, 88 * k, 16 * k)
        }
        drawPath(route, EvFitColors.TextPrimary, style = Stroke(9 * k, cap = StrokeCap.Round))
        drawCircle(EvFitColors.TextPrimary, 7.5f * k, p(12f, 84f))
        drawCircle(ringFill, 17 * k, p(50f, 50f))
        drawCircle(EvFitColors.FitBlueFill, 17 * k, p(50f, 50f), style = Stroke(10 * k))
    }
}
