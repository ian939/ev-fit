// 화면 공통 컴포넌트 — 카드·버튼(눌림 95% 축소)·배지·라벨과 금액 표기
package com.ian.evfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.ui.theme.EvFitColors
import java.util.Locale

fun won(v: Long): String = String.format(Locale.KOREA, "%,d원", v)
fun unitPrice(p: Double): String =
    if (p % 1.0 == 0.0) String.format(Locale.KOREA, "%,.0f원", p) else String.format(Locale.KOREA, "%,.1f원", p)
fun one(v: Double): String = String.format(Locale.KOREA, "%.1f", v)

@Composable
fun EvCard(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (highlight) EvFitColors.CardAlt else EvFitColors.Card)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun EvButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, secondary: Boolean = false,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val bg = when {
        !enabled -> EvFitColors.Card
        secondary -> EvFitColors.CardAlt
        else -> EvFitColors.ActionBg
    }
    Box(
        modifier
            .heightIn(min = 64.dp)
            .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = if (enabled) Color.White else EvFitColors.Disabled,
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun Badge(text: String, bg: Color = EvFitColors.ActionBg, fg: Color = Color.White) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
        color = fg, fontSize = 17.sp, fontWeight = FontWeight.Bold,
    )
}

@Composable
fun SyntheticLabel() {
    Text("시연용 합성 데이터", color = EvFitColors.Warning, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun SectionTitle(title: String, tag: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (tag != null) Text(tag, color = EvFitColors.AccentText, fontSize = 16.sp, fontWeight = FontWeight.Black)
        Text(title, style = MaterialTheme.typography.titleLarge, color = EvFitColors.TextPrimary)
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(EvFitColors.Navy)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(EvFitColors.FitBlueFill))
    }
}

@Composable
fun BackRow(onBack: () -> Unit) {
    Text(
        "← 뒤로", color = EvFitColors.AccentText, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(12.dp),
    )
}
