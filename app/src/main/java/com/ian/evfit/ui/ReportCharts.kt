// 리포트·충전 중 화면 차트 — SOC×kW 충전 곡선(동일 차종 기대 구간 위 내 곡선), 구간별 차이 막대, 회원 속도 분포, 약정 링
package com.ian.evfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.BandStat
import com.ian.evfit.engine.SocSample
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.Wanted
import com.ian.evfit.ui.theme.gauge
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.sqrt

data class CurvePoint(val soc: Double, val kw: Double)

/** 연속 샘플 쌍의 순간 속도(kW) = ΔSOC/100×용량 ÷ Δ시간. 충전기 최대 출력(+5%)을 넘는 쌍은 SOC 순간 변경이라 뺀다 */
fun curvePoints(samples: List<SocSample>, batteryKwh: Double, maxKw: Double): List<CurvePoint> =
    samples.sortedBy { it.tMillis }.zipWithNext().mapNotNull { (a, b) ->
        val h = (b.tMillis - a.tMillis) / 3_600_000.0
        if (b.soc <= a.soc || h <= 0) return@mapNotNull null
        val kw = (b.soc - a.soc) / 100.0 * batteryKwh / h
        if (kw > maxKw) null else CurvePoint((a.soc + b.soc) / 2.0, kw)
    }

/** 표준정규 누적분포(Abramowitz–Stegun 7.1.26 erf 근사, 오차 1.5e-7) */
fun normalCdf(z: Double): Double {
    val x = abs(z) / sqrt(2.0)
    val t = 1 / (1 + 0.3275911 * x)
    val erf = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * exp(-x * x)
    return if (z >= 0) 0.5 * (1 + erf) else 0.5 * (1 - erf)
}

/**
 * SOC(가로)×kW(세로) 충전 곡선. 동일 차종 기대 구간(p25~p75) 면, 중앙값 점선, 내 충전 구간 세로 띠, 내 곡선 실선.
 * 기대 구간은 10% 구간 통계를 구간 가운데(SOC+5)에 놓고 잇는다.
 */
@Composable
fun SocSpeedChart(
    expected: List<BandStat>?, mine: List<CurvePoint>, fromSoc: Int?, toSoc: Int?,
    modifier: Modifier = Modifier, height: Dp = 360.dp, compact: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current.density
    val axis = gauge(if (compact) 15.sp else 17.sp, FontWeight.SemiBold, EvFitColors.TextSecondary)
    val windowStyle = TextStyle(fontFamily = Wanted, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EvFitColors.AccentText)
    val top = listOfNotNull(expected?.maxOfOrNull { it.p75 }, mine.maxOfOrNull { it.kw }).maxOrNull() ?: 100.0
    val maxY = (ceil(top * 1.12 / 50.0) * 50.0).coerceAtLeast(50.0)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val left = 52 * d
        val bottom = size.height - 30 * d
        val right = size.width - 8 * d
        val topY = 10 * d
        fun x(soc: Double) = (left + (right - left) * soc / 100.0).toFloat()
        fun y(kw: Double) = (bottom - (bottom - topY) * kw / maxY).toFloat()

        // 가로 눈금(50kW마다)과 세로 눈금(20%마다)
        var g = 0.0
        while (g <= maxY + 0.1) {
            drawLine(EvFitColors.Hairline, Offset(left, y(g)), Offset(right, y(g)), 1.2f * d)
            val lay = measurer.measure("${g.toInt()}", axis)
            drawText(lay, topLeft = Offset(left - 10 * d - lay.size.width, y(g) - lay.size.height / 2f))
            g += 50.0
        }
        for (soc in 0..100 step 20) {
            val lay = measurer.measure("$soc", axis)
            drawText(lay, topLeft = Offset(x(soc.toDouble()) - lay.size.width / 2f, bottom + 6 * d))
        }
        // 내 충전 구간
        if (fromSoc != null && toSoc != null) {
            drawRect(EvFitColors.FitBlueFill.copy(alpha = 0.09f), Offset(x(fromSoc.toDouble()), topY), Size(x(toSoc.toDouble()) - x(fromSoc.toDouble()), bottom - topY))
            if (!compact) {
                listOf(fromSoc, toSoc).forEach { v ->
                    drawLine(EvFitColors.AccentText.copy(alpha = 0.5f), Offset(x(v.toDouble()), topY), Offset(x(v.toDouble()), bottom), 1.5f * d,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5 * d, 5 * d)))
                    val lay = measurer.measure("$v%", windowStyle)
                    drawText(lay, topLeft = Offset(x(v.toDouble()) + 6 * d, topY))
                }
            }
        }
        // 동일 차종 기대 구간과 중앙값
        if (!expected.isNullOrEmpty()) {
            val sorted = expected.sortedBy { it.from }
            fun cx(b: BandStat, i: Int) = when (i) {
                0 -> b.from.toDouble()
                sorted.lastIndex -> b.to.toDouble()
                else -> b.from + 5.0
            }
            val area = Path().apply {
                sorted.forEachIndexed { i, b -> if (i == 0) moveTo(x(cx(b, i)), y(b.p75)) else lineTo(x(cx(b, i)), y(b.p75)) }
                sorted.indices.reversed().forEach { i -> lineTo(x(cx(sorted[i], i)), y(sorted[i].p25)) }
                close()
            }
            drawPath(area, EvFitColors.FitBlueFill.copy(alpha = 0.22f))
            val median = Path().apply {
                sorted.forEachIndexed { i, b -> if (i == 0) moveTo(x(cx(b, i)), y(b.median)) else lineTo(x(cx(b, i)), y(b.median)) }
            }
            drawPath(median, EvFitColors.TextSecondary, style = Stroke(2 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9 * d, 7 * d))))
        }
        // 내 곡선 — 바깥 빛 + 안쪽 선
        if (mine.size >= 2) {
            val p = Path().apply { mine.forEachIndexed { i, c -> if (i == 0) moveTo(x(c.soc), y(c.kw)) else lineTo(x(c.soc), y(c.kw)) } }
            drawPath(p, EvFitColors.FitBlueFill.copy(alpha = 0.45f), style = Stroke(8 * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(p, EvFitColors.TextPrimary, style = Stroke(2.6f * d, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val last = mine.last()
            drawCircle(EvFitColors.TextPrimary, 6 * d, Offset(x(last.soc), y(last.kw)))
        } else if (mine.size == 1) {
            drawCircle(EvFitColors.TextPrimary, 6 * d, Offset(x(mine[0].soc), y(mine[0].kw)))
        }
    }
}

/** 차트 범례 한 줄 */
@Composable
fun ChartLegend(myLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem({ drawRect(EvFitColors.FitBlueFill.copy(alpha = 0.35f)) }, "같은 차종 기대 구간(하위 25~상위 25%)")
        LegendItem({ drawLine(EvFitColors.TextSecondary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))) }, "중앙값")
        LegendItem({ drawLine(EvFitColors.TextPrimary, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 6f) }, myLabel)
    }
}

@Composable
private fun LegendItem(mark: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 26.dp, height = 12.dp)) { mark() }
        Text(text, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

/** 구간별 차이(내 속도 − 기대 중앙값) — 0을 가운데 둔 막대. 표에 보이는 정수끼리 뺀다 */
@Composable
fun BandDiffStrip(rows: List<Triple<String, Int, Int?>>, modifier: Modifier = Modifier) {
    val maxAbs = (rows.mapNotNull { r -> r.third?.let { abs(r.second - it) } }.maxOrNull() ?: 0).coerceAtLeast(10)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { (label, mine, exp) ->
            val diff = exp?.let { mine - it }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Text("$mine", style = gauge(26.sp))
                Box(Modifier.fillMaxWidth().height(56.dp)) {
                    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
                        val mid = size.height / 2
                        drawLine(EvFitColors.Hairline, Offset(0f, mid), Offset(size.width, mid), 2f)
                        if (diff != null && diff != 0) {
                            val h = (mid - 4) * abs(diff) / maxAbs
                            val c = if (diff > 0) EvFitColors.Saving else EvFitColors.Warning
                            val w = size.width * 0.34f
                            drawRect(c, Offset((size.width - w) / 2, if (diff > 0) mid - h else mid), Size(w, h))
                        }
                    }
                }
                Text(
                    diff?.let { if (it > 0) "+$it" else "$it" } ?: "—",
                    color = when { diff == null || diff == 0 -> EvFitColors.TextSecondary; diff > 0 -> EvFitColors.Saving; else -> EvFitColors.Warning },
                    style = gauge(20.sp, color = Color.Unspecified),
                )
                Text("기대 ${exp ?: "—"}", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** 같은 차종 회원 평균 속도 분포(정규분포로 합성) 위에 내 위치 */
@Composable
fun MemberDistribution(mean: Double, sd: Double, mine: Double?, modifier: Modifier = Modifier, height: Dp = 240.dp) {
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current.density
    val axis = gauge(16.sp, FontWeight.SemiBold, EvFitColors.TextSecondary)
    val meStyle = TextStyle(fontFamily = Wanted, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EvFitColors.TextPrimary)
    Canvas(modifier.fillMaxWidth().height(height)) {
        val lo = mean - 3 * sd
        val hi = mean + 3 * sd
        val bins = 24
        val bottom = size.height - 28 * d
        val w = size.width / bins
        fun x(v: Double) = ((v - lo) / (hi - lo) * size.width).toFloat()
        val edges = (0..bins).map { lo + (hi - lo) * it / bins }
        val probs = (0 until bins).map { normalCdf((edges[it + 1] - mean) / sd) - normalCdf((edges[it] - mean) / sd) }
        val pMax = probs.max()
        for (i in 0 until bins) {
            val v0 = edges[i]
            val v1 = edges[i + 1]
            val h = (bottom - 24 * d) * (probs[i] / pMax).toFloat()
            val isMe = mine != null && mine >= v0 && mine < v1
            drawRect(
                if (isMe) EvFitColors.TextPrimary else EvFitColors.FitBlueFill.copy(alpha = 0.45f),
                Offset(i * w + 2 * d, bottom - h), Size(w - 4 * d, h),
            )
        }
        drawLine(EvFitColors.Hairline, Offset(0f, bottom), Offset(size.width, bottom), 2f)
        listOf(lo, mean, hi).forEach { v ->
            val lay = measurer.measure("${v.toInt()}", axis)
            drawText(lay, topLeft = Offset((x(v) - lay.size.width / 2f).coerceIn(0f, size.width - lay.size.width), bottom + 4 * d))
        }
        if (mine != null) {
            val mx = x(mine.coerceIn(lo, hi))
            drawLine(EvFitColors.TextPrimary, Offset(mx, 18 * d), Offset(mx, bottom), 2 * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * d, 5 * d)))
            val lay = measurer.measure("나 ${mine.toInt()}kW", meStyle)
            drawText(lay, topLeft = Offset((mx + 8 * d).coerceAtMost(size.width - lay.size.width), 0f))
        }
    }
}

/** 구독 약정 링 — 브랜드 동심원 호. cap이 없으면(무제한) 점선 링 */
@Composable
fun AllowanceRing(usedKwh: Double, capKwh: Double?, modifier: Modifier = Modifier) {
    Canvas(modifier.size(120.dp)) {
        val sw = 12.dp.toPx()
        val inset = sw / 2
        val sz = Size(size.width - sw, size.height - sw)
        val tl = Offset(inset, inset)
        if (capKwh == null) {
            drawArc(EvFitColors.FitBlueFill, -90f, 360f, false, tl, sz, style = Stroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
        } else {
            drawArc(EvFitColors.Bg, -90f, 360f, false, tl, sz, style = Stroke(sw))
            val left = ((capKwh - usedKwh) / capKwh).coerceIn(0.0, 1.0)
            drawArc(EvFitColors.FitBlueFill, -90f, (360 * left).toFloat(), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
    }
}
