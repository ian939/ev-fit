// 충전 리포트(FIT Insight) — 이번 충전 SOC 구간 비교·영수증, 동일 차량 리포트, 이번 달 기록
package com.ian.evfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.AppData
import com.ian.evfit.data.BandStat
import com.ian.evfit.data.ChargeRecord
import com.ian.evfit.data.Prefs
import com.ian.evfit.engine.BandSpeed
import com.ian.evfit.engine.ChargeCurve
import com.ian.evfit.ui.theme.EvFitColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("충전 리포트", tag = "FIT Insight")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("이번 충전", "동일 차량", "이번 달").forEachIndexed { i, label ->
                Text(
                    label,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (tab == i) EvFitColors.ActionBg else EvFitColors.Card)
                        .clickable { tab = i }.padding(horizontal = 18.dp, vertical = 12.dp),
                    color = if (tab == i) Color.White else EvFitColors.TextSecondary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
                )
            }
        }
        when (tab) {
            0 -> SessionReport(s, wide)
            1 -> ModelReport(s)
            else -> MonthReport(s)
        }
    }
}

private data class BandRow(val my: BandSpeed, val exp: BandStat?)

@Composable
private fun SessionReport(s: UiState, wide: Boolean) {
    val r = s.lastRecord
    val data = s.data
    if (r == null || data == null) {
        EvCard(Modifier.fillMaxWidth()) { Muted("아직 충전 기록이 없어요. 홈에서 시연을 시작해 보세요.") }
        return
    }
    val battery = s.batteryKwh
    val bands = ChargeCurve.bandSpeeds(r.samples, battery)
    val station = data.station(r.stationId)
    val cls = AppData.chargerClass(station?.maxKw ?: 200)
    val bench = data.benchFor(s.wallet.vehicleModel)
    val expected = bench?.bands?.get(cls.toString())
    val rows = bands.map { b -> BandRow(b, expected?.firstOrNull { it.from == b.fromSoc }) }
    val ok = rows.all { it.exp == null || it.my.avgKw in it.exp.p25 * 0.9..it.exp.p75 * 1.1 }
    val minutes = if (r.samples.size >= 2) (r.samples.last().tMillis - r.samples.first().tMillis) / 60_000.0 else 0.0
    val avgKw = if (minutes > 0) r.kwh / (minutes / 60.0) else 0.0

    Text(if (ok) "기대한 만큼 나왔어요" else "일부 구간이 기대보다 낮았어요", style = MaterialTheme.typography.headlineMedium, color = EvFitColors.TextPrimary)
    Muted("${r.stationName} · 같은 ${s.wallet.vehicleModel ?: "차종"} 회원 ${bench?.members ?: 0}명의 SOC 구간별 기대 속도와 내 충전을 겹쳐 봤어요")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Badge("내 충전 구간 ${r.fromSoc}% → ${r.toSoc}%")
        Badge("평균 ${avgKw.toInt()}kW", bg = EvFitColors.CardAlt)
        Badge("소요 ${minutes.toInt()}분", bg = EvFitColors.CardAlt)
    }
    if (wide) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(14.dp)) { ChartCard(rows, expected, cls) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { BandTable(rows); Receipt(s, r) }
        }
    } else {
        ChartCard(rows, expected, cls)
        BandTable(rows)
        Receipt(s, r)
    }
}

@Composable
private fun ChartCard(rows: List<BandRow>, expected: List<BandStat>?, cls: Int) {
    EvCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SOC 구간별 충전 속도", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Muted("${cls}kW급")
        }
        val maxY = ((expected?.maxOfOrNull { it.p75 } ?: 0.0).coerceAtLeast(rows.maxOfOrNull { it.my.avgKw } ?: 0.0) * 1.1).coerceAtLeast(50.0)
        Canvas(Modifier.fillMaxWidth().height(240.dp)) {
            fun x(soc: Double) = (soc / 100.0 * size.width).toFloat()
            fun y(kw: Double) = (size.height - kw / maxY * size.height).toFloat()
            // 가로 눈금
            for (k in 0..4) {
                val yy = size.height * k / 4
                drawLine(EvFitColors.CardAlt, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 2f)
            }
            if (expected != null) {
                val band = Path().apply {
                    expected.forEachIndexed { i, b -> if (i == 0) moveTo(x(b.from + 5.0), y(b.p75)) else lineTo(x(b.from + 5.0), y(b.p75)) }
                    expected.reversed().forEach { b -> lineTo(x(b.from + 5.0), y(b.p25)) }
                    close()
                }
                drawPath(band, EvFitColors.FitBlueFill.copy(alpha = 0.3f))
                val median = Path().apply {
                    expected.forEachIndexed { i, b -> if (i == 0) moveTo(x(b.from + 5.0), y(b.median)) else lineTo(x(b.from + 5.0), y(b.median)) }
                }
                drawPath(median, EvFitColors.TextSecondary, style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
            }
            if (rows.isNotEmpty()) {
                val mine = Path().apply {
                    rows.forEachIndexed { i, r -> if (i == 0) moveTo(x(r.my.fromSoc + 5.0), y(r.my.avgKw)) else lineTo(x(r.my.fromSoc + 5.0), y(r.my.avgKw)) }
                }
                drawPath(mine, EvFitColors.FitBlueFill, style = Stroke(width = 6f))
                rows.forEach { r -> drawCircle(EvFitColors.TextPrimary, radius = 8f, center = Offset(x(r.my.fromSoc + 5.0), y(r.my.avgKw))) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0, 20, 40, 60, 80, 100).forEach { Text("$it", color = EvFitColors.TextSecondary, fontSize = 15.sp) }
        }
        Muted("■ 동일 차종 기대 구간(p25~p75)  ┄ 동일 차종 중앙값  ● 내 충전 · y축 최대 ${maxY.toInt()}kW")
        SyntheticLabel()
    }
}

@Composable
private fun BandTable(rows: List<BandRow>) {
    EvCard(Modifier.fillMaxWidth()) {
        Text("구간별 내 속도", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth()) {
            listOf("SOC 구간", "기대", "내 속도", "차이").forEach { Text(it, Modifier.weight(1f), color = EvFitColors.TextSecondary, fontSize = 17.sp) }
        }
        rows.forEach { r ->
            val diff = r.exp?.let { r.my.avgKw - it.median }
            Row(Modifier.fillMaxWidth()) {
                Text("${r.my.fromSoc}–${r.my.toSoc}%", Modifier.weight(1f), color = EvFitColors.TextPrimary, fontSize = 19.sp)
                Text(r.exp?.median?.toInt()?.toString() ?: "—", Modifier.weight(1f), color = EvFitColors.TextSecondary, fontSize = 19.sp)
                Text("${r.my.avgKw.toInt()}", Modifier.weight(1f), color = EvFitColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(
                    diff?.let { (if (it >= 0) "+" else "") + it.toInt() } ?: "—", Modifier.weight(1f),
                    color = if ((diff ?: 0.0) >= 0) EvFitColors.Saving else EvFitColors.Warning, fontSize = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun Receipt(s: UiState, r: ChargeRecord) {
    val plan = r.planId?.let { s.data?.plans?.get(it) }
    val sub = s.wallet.subscriptions.firstOrNull { it.planId == r.planId }
    EvCard(Modifier.fillMaxWidth(), highlight = true) {
        Text("영수증", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Muted("${r.credentialLabel}${plan?.let { " · ${it.name}" } ?: ""}${if (r.simulated) " · 시연" else ""}")
        Text(won(r.costKrw), fontSize = 40.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
        Muted("${one(r.kwh)}kWh · ${r.fromSoc}% → ${r.toSoc}%")
        r.moeReferenceKrw?.let { ref ->
            Text("환경부 카드(${won(ref)})보다 ${won(ref - r.costKrw)} 절약", color = EvFitColors.Saving, style = MaterialTheme.typography.titleMedium)
        }
        if (plan != null) {
            val cap = plan.monthlyCapKwh
            Muted(if (cap == null) "${plan.name} 남은 약정: 무제한" else "${plan.name} 남은 약정: ${one((cap - (sub?.usedKwhThisMonth ?: 0.0)).coerceAtLeast(0.0))}kWh")
        }
    }
}

@Composable
private fun ModelReport(s: UiState) {
    val bench = s.data?.benchFor(s.wallet.vehicleModel)
    if (bench == null) {
        EvCard(Modifier.fillMaxWidth()) { Muted("지갑에서 차종을 고르면 동일 차량 리포트를 보여 드려요") }
        return
    }
    EvCard(Modifier.fillMaxWidth(), highlight = true) {
        Muted("나와 같은 차, 이만큼 충전하고 있어요")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%,d".format(bench.members), fontSize = 56.sp, fontWeight = FontWeight.Black, color = EvFitColors.AccentText)
            Muted("명 · 같은 모델(${bench.model})을 타는 회원")
        }
        SyntheticLabel()
    }
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(
            "평균 속도" to "${(bench.avgKwByClass["200"] ?: 0.0).toInt()}kW",
            "성공률" to "${one(bench.successRatePct)}%",
            "만족도" to "${one(bench.satisfaction)} ★",
        ).forEach { (k, v) ->
            EvCard(Modifier.weight(1f)) {
                Muted(k)
                Text(v, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = EvFitColors.TextPrimary)
            }
        }
    }
    EvCard(Modifier.fillMaxWidth()) {
        Text("용량별 평균 속도", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        val maxAvg = bench.avgKwByClass.values.maxOrNull() ?: 1.0
        listOf(50, 100, 200, 350).forEach { c ->
            val v = bench.avgKwByClass[c.toString()] ?: 0.0
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${c}kW급", Modifier.width(96.dp), color = EvFitColors.TextSecondary, fontSize = 18.sp)
                ProgressBar((v / maxAvg).toFloat(), Modifier.weight(1f))
                Text("${v.toInt()}", Modifier.width(56.dp), color = EvFitColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        SyntheticLabel()
    }
}

@Composable
private fun MonthReport(s: UiState) {
    val month = Prefs.currentMonth()
    val fmt = DateTimeFormatter.ofPattern("M/d HH:mm")
    val mine = s.records.filter { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault())).toString() == month }
    EvCard(Modifier.fillMaxWidth(), highlight = true) {
        Muted("$month · 충전 ${mine.size}회")
        Text("지출 ${won(mine.sumOf { it.costKrw })}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
        Text("환경부 카드 대비 ${won(mine.sumOf { (it.moeReferenceKrw ?: it.costKrw) - it.costKrw })} 절약", color = EvFitColors.Saving, style = MaterialTheme.typography.titleMedium)
    }
    mine.reversed().forEach { r ->
        EvCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.stationName, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Muted("${Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(fmt)} · ${r.credentialLabel} · ${one(r.kwh)}kWh")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(won(r.costKrw), color = EvFitColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    r.moeReferenceKrw?.let { Text("-${won(it - r.costKrw)}", color = EvFitColors.Saving, fontSize = 18.sp) }
                }
            }
        }
    }
}
