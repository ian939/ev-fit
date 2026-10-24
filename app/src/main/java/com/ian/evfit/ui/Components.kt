// 화면 공통 컴포넌트(v2) — 계기판 숫자, 버튼(눌림 95% 축소), 구간 선택, 막대, 구분선과 금액 표기
package com.ian.evfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.gauge
import java.util.Locale

fun won(v: Long): String = String.format(Locale.KOREA, "%,d원", v)
fun num(v: Long): String = String.format(Locale.KOREA, "%,d", v)
fun unitPrice(p: Double): String =
    if (p % 1.0 == 0.0) String.format(Locale.KOREA, "%,.0f원", p) else String.format(Locale.KOREA, "%,.1f원", p)
fun one(v: Double): String = String.format(Locale.KOREA, "%.1f", v)

/** 계기판 숫자 + 작은 단위. 숫자는 Barlow, 단위는 Wanted로 흐리게. 둘은 같은 기준선에 놓고, 바깥 Row에서도 alignByBaseline으로 맞출 수 있다 */
@Composable
fun Gauge(value: String, unit: String? = null, size: TextUnit = 44.sp, color: Color = EvFitColors.TextPrimary, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, Modifier.alignByBaseline(), style = gauge(size, color = color))
        if (unit != null) {
            Text(unit, Modifier.alignByBaseline(), color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 낮은 면으로 묶은 영역(리포트용) — 같은 카드를 나열하지 않도록 필요한 곳에만 쓴다 */
@Composable
fun EvCard(modifier: Modifier = Modifier, highlight: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (highlight) EvFitColors.CardAlt else EvFitColors.Card)
            .padding(22.dp),
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
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .graphicsLayer { val s = if (pressed) 0.95f else 1f; scaleX = s; scaleY = s }
            .clip(shape)
            .then(
                when {
                    !enabled -> Modifier.background(EvFitColors.Card)
                    secondary -> Modifier.border(2.dp, EvFitColors.Hairline, shape)
                    else -> Modifier.background(EvFitColors.ActionBg)
                },
            )
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text, color = when { !enabled -> EvFitColors.Disabled; secondary -> EvFitColors.TextPrimary; else -> Color.White },
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
        )
    }
}

/** 구간 선택(탭 기준·리포트 보기) — 고른 칸만 FIT Blue 면 */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(EvFitColors.Bg).padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (i == selected) EvFitColors.ActionBg else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, color = if (i == selected) Color.White else EvFitColors.TextSecondary,
                    style = MaterialTheme.typography.labelLarge, maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Badge(text: String, bg: Color = EvFitColors.ActionBg, fg: Color = Color.White) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 12.dp, vertical = 5.dp),
        color = fg, style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun SyntheticLabel(text: String = "시연용 합성 데이터") {
    Text(text, color = EvFitColors.Warning, style = MaterialTheme.typography.labelSmall)
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier, style = MaterialTheme.typography.titleLarge, color = EvFitColors.TextPrimary)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
}

/** 라벨 위·값 아래로 쌓은 속성 하나 — " · "로 이어 붙이지 않고 열을 맞춰 나열할 때 쓴다 */
@Composable
fun Attr(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = EvFitColors.TextPrimary) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
        Text(value, color = valueColor, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(EvFitColors.Hairline))
}

@Composable
fun VHairline(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).fillMaxHeight().background(EvFitColors.Hairline))
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = EvFitColors.FitBlueFill, track: Color = EvFitColors.Bg, height: Int = 10) {
    Box(modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(color))
    }
}

@Composable
fun BackRow(label: String = "뒤로", onBack: () -> Unit) {
    Text(
        "‹ $label", color = EvFitColors.AccentText, style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(vertical = 12.dp, horizontal = 4.dp),
    )
}
