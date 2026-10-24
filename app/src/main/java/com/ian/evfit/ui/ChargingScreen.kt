// 충전 중 화면(v2) — SOC 링(시작→목표 호 위 현재 위치), 충전량·금액·속도·남은 시간, 같은 차종 기대 구간 위 실시간 곡선
package com.ian.evfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.AppData
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.gauge

@Composable
fun ChargingScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    val c = s.session ?: return
    val target = s.wallet.targetSoc
    val bench = s.data?.benchFor(s.wallet.vehicleModel)
    val expected = bench?.bands?.get(AppData.chargerClass(c.station.maxKw).toString())
    val remainKwh = (target - c.soc).coerceAtLeast(0) / 100.0 * s.batteryKwh
    val remainMin = c.instantKw?.takeIf { it > 1 }?.let { remainKwh / it * 60 }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("충전 중", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
            Text(c.station.name, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
            Text(
                "PnC 인증 ${c.option.credential.label}${s.prep?.contract?.let { "  ${it.emaid}" } ?: ""}${if (c.simulated) "(시뮬레이션)" else ""}",
                color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
            )
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                SocRing(c.fromSoc, c.soc, target, Modifier.size(440.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Gauge("${c.soc}", "%", 120.sp)
                    Text("${c.fromSoc}%에서 목표 ${target}%로", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (c.simulated) EvButton("충전 끝내기(시연)", { vm.finishSession() }, Modifier.fillMaxWidth(), secondary = true)
            else Text("커넥터를 뽑거나 목표 충전량에 닿으면 자동으로 끝나요", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
        Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Metric("충전량", one(c.kwh), "kWh")
                Metric("지금까지", num(c.costKrw), "원")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(44.dp)) {
                Metric("충전 속도", c.instantKw?.let { "${it.toInt()}" } ?: "—", "kW")
                Metric("남은 시간", remainMin?.let { "${it.toInt().coerceAtLeast(1)}" } ?: "—", "분")
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EvFitColors.Card).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("같은 차종 기대 구간과 지금 내 속도", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                SocSpeedChart(expected, curvePoints(c.samples, s.batteryKwh, c.station.maxKw * 1.05), c.fromSoc, target, height = 260.dp, compact = true)
                SyntheticLabel("기대 구간은 시연용 합성 데이터예요")
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        Gauge(value, unit, 56.sp)
    }
}

/** SOC 링 — 바닥 원(0~100), 시작→목표 호(흐리게), 시작→현재 호(FIT Blue), 현재 위치 점 */
@Composable
private fun SocRing(from: Int, now: Int, target: Int, modifier: Modifier) {
    Canvas(modifier) {
        val sw = 26.dp.toPx()
        val tl = Offset(sw / 2, sw / 2)
        val sz = Size(size.width - sw, size.height - sw)
        fun deg(soc: Int) = -90f + 360f * soc / 100f
        drawArc(EvFitColors.Card, 0f, 360f, false, tl, sz, style = Stroke(sw))
        drawArc(EvFitColors.FitBlueFill.copy(alpha = 0.22f), deg(from), 360f * (target - from) / 100f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        drawArc(EvFitColors.FitBlueFill, deg(from), 360f * (now - from).coerceAtLeast(0) / 100f, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        val r = sz.width / 2
        val a = Math.toRadians(deg(now).toDouble())
        val dot = center + Offset((r * kotlin.math.cos(a)).toFloat(), (r * kotlin.math.sin(a)).toFloat())
        drawCircle(EvFitColors.TextPrimary, sw * 0.42f, dot)
        // 목표 눈금
        val t = Math.toRadians(deg(target).toDouble())
        val inner = center + Offset(((r - sw) * kotlin.math.cos(t)).toFloat(), ((r - sw) * kotlin.math.sin(t)).toFloat())
        val outer = center + Offset(((r + sw) * kotlin.math.cos(t)).toFloat(), ((r + sw) * kotlin.math.sin(t)).toFloat())
        drawLine(EvFitColors.TextPrimary, inner, outer, 4.dp.toPx(), StrokeCap.Round)
    }
}
