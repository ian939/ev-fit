// EV FIT 텍스트 워드마크 — "EV"(Soft Blue) + "FIT"(밝은 블루), Wanted Sans 가장 굵게
package com.ian.evfit.ui.theme

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun EvFitWordmark(sizeSp: Int) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = EvFitColors.TextPrimary)) { append("EV ") }
            withStyle(SpanStyle(color = EvFitColors.AccentText)) { append("FIT") }
        },
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.ExtraBold,
        fontFamily = Wanted,
        letterSpacing = (-0.5).sp,
    )
}
