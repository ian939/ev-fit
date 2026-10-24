// 충전 리포트(v2, FIT Insight) — 이번 충전(판정·핵심 숫자·SOC×kW 곡선·구간 차이·영수증 비교), 같은 차종(회원 속도 분포), 이번 달(지출·절약·약정 링)
package com.ian.evfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.AppData
import com.ian.evfit.data.BandStat
import com.ian.evfit.data.ChargeRecord
import com.ian.evfit.data.Prefs
import com.ian.evfit.engine.BandSpeed
import com.ian.evfit.engine.ChargeCurve
import com.ian.evfit.engine.PriceEngine
import com.ian.evfit.ui.theme.EvFitColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReportScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("충전 리포트", style = MaterialTheme.typography.headlineMedium, color = EvFitColors.TextPrimary)
            Spacer(Modifier.weight(1f))
            Segmented(listOf("이번 충전", "같은 차종", "이번 달"), tab, { tab = it }, Modifier.width(600.dp))
        }
        when (tab) {
            0 -> SessionReport(s, vm)
            1 -> ModelReport(s)
            else -> MonthReport(s)
        }
    }
}

/** 라벨 위, 큰 숫자, 아래 설명 */
@Composable
private fun Instrument(
    label: String, value: String, unit: String?, caption: String? = null,
    color: Color = EvFitColors.TextPrimary, captionColor: Color = EvFitColors.TextSecondary, modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        Gauge(value, unit, 52.sp, color)
        caption?.let { Text(it, color = captionColor, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp)).background(EvFitColors.Card).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

private data class BandRow(val my: BandSpeed, val exp: BandStat?)

/** 기대 구간의 퍼짐(표준편차 근사) = 각 구간 (p75−p25)/1.349의 평균 */
private fun spreadOf(bands: List<BandStat>): Double = bands.map { (it.p75 - it.p25) / 1.349 }.average().coerceAtLeast(1.0)

@Composable
private fun SessionReport(s: UiState, vm: AppViewModel) {
    val r = s.lastRecord
    val data = s.data
    if (r == null || data == null) {
        Panel(Modifier.fillMaxWidth()) {
            Text("아직 충전 기록이 없어요", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleLarge)
            Muted("경로 화면에서 추천 충전소의 [충전 준비]를 누르고 충전을 마치면 여기서 내 충전 속도를 같은 차종과 비교해 드려요.")
            EvButton("경로 화면으로", { vm.selectTab(Tab.ROUTE) }, Modifier.width(280.dp))
        }
        return
    }
    val battery = s.batteryKwh
    val station = data.station(r.stationId)
    // 충전기 최대 출력(+5%)을 넘는 속도는 SOC 순간 변경이라 계산에서 뺀다
    val maxKw = station?.maxKw?.times(1.05) ?: Double.POSITIVE_INFINITY
    val bands = ChargeCurve.bandSpeeds(r.samples, battery, maxKw = maxKw)
    val cls = AppData.chargerClass(station?.maxKw ?: 200)
    val bench = data.benchFor(s.wallet.vehicleModel)
    val expected = bench?.bands?.get(cls.toString())
    val rows = bands.map { b -> BandRow(b, expected?.firstOrNull { it.from == b.fromSoc }) }
    val ok = rows.all { it.exp == null || it.my.avgKw >= it.exp.p25 * 0.9 }
    val minutes = if (r.samples.size >= 2) (r.samples.last().tMillis - r.samples.first().tMillis) / 60_000.0 else 0.0
    val avgKw = (if (minutes > 0) r.kwh / (minutes / 60.0) else 0.0).takeIf { it > 0 && it <= maxKw }
    // 기대 소요 = 내 구간 1%마다 0.01×용량 ÷ 그 구간 중앙값 kW
    val window = expected?.filter { it.to > r.fromSoc && it.from < r.toSoc }
    val expMinutes = expected?.let { ex ->
        (r.fromSoc until r.toSoc).sumOf { soc -> 0.01 * battery / (ex.firstOrNull { soc >= it.from && soc < it.to }?.median ?: 100.0) * 60 }
    }
    val expAvg = expMinutes?.takeIf { it > 0 }?.let { r.kwh / (it / 60.0) }
    val topPct = if (avgKw != null && expAvg != null && !window.isNullOrEmpty()) {
        (1 - normalCdf((avgKw - expAvg) / spreadOf(window))) * 100
    } else null
    val headline = when {
        rows.isEmpty() -> "속도를 잴 만큼 기록이 쌓이지 않았어요"
        ok -> "기대한 만큼 나왔어요"
        else -> "일부 구간이 기대보다 낮았어요"
    }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(headline, style = MaterialTheme.typography.headlineLarge, color = EvFitColors.TextPrimary)
            Text(
                "${r.stationName}에서 ${r.fromSoc}%부터 ${r.toSoc}%까지 충전했어요. 같은 차종 회원 ${bench?.members?.let { num(it.toLong()) } ?: 0}명의 구간별 기대 속도와 겹쳐 봤어요.",
                color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge,
            )
        }
        Instrument("평균 속도", avgKw?.let { "${it.toInt()}" } ?: "—", "kW", expAvg?.let { "기대 ${it.toInt()}kW" })
        Instrument("걸린 시간", "${minutes.toInt()}", "분", expMinutes?.let { "기대 ${it.toInt()}분" })
        Instrument("같은 차종 중 상위", topPct?.let { "${it.toInt().coerceIn(1, 99)}" } ?: "—", "%", "시연용 합성 데이터", captionColor = EvFitColors.Warning)
    }

    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("SOC 구간별 충전 속도", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("${cls}kW급 충전기, 세로는 kW", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            ChartLegend("내 충전")
        }
        SocSpeedChart(expected, curvePoints(r.samples, battery, maxKw), r.fromSoc, r.toSoc, height = 300.dp)
        SyntheticLabel("같은 차종 기대 구간은 시연용 합성 데이터예요")
    }

    // 나란한 두 카드는 높이를 맞추고, 왼쪽 설명은 오른쪽 마지막 줄과 같은 바닥에 둔다
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.25f).fillMaxHeight()) {
            Text("구간별 내 속도와 기대 중앙값의 차이", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) {
                Muted("충전 중 SOC 기록이 부족해 구간 속도를 계산하지 못했어요")
            } else {
                BandDiffStrip(rows.map { Triple("${it.my.fromSoc}–${it.my.toSoc}%", it.my.avgKw.toInt(), it.exp?.median?.toInt()) })
                val worst = rows.filter { it.exp != null }.minByOrNull { it.my.avgKw - it.exp!!.median }
                Spacer(Modifier.weight(1f))
                Text(
                    when {
                        ok -> "모든 구간이 같은 차종 기대 구간 안에 들어왔어요. 70% 이후 느려지는 것도 같은 차종과 같은 흐름이에요."
                        worst != null -> "${worst.my.fromSoc}–${worst.my.toSoc}% 구간이 기대보다 ${(worst.exp!!.median - worst.my.avgKw).toInt()}kW 낮았어요. 같은 충전기에서 반복되면 충전기 쪽을 의심해 보세요."
                        else -> ""
                    },
                    color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Receipt(s, r, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Receipt(s: UiState, r: ChargeRecord, modifier: Modifier) {
    val data = s.data ?: return
    val plan = r.planId?.let { data.plans[it] }
    val sub = s.wallet.subscriptions.firstOrNull { it.planId == r.planId }
    val station = data.station(r.stationId)
    val q = station?.let { PriceEngine.quote(it, r.kwh, s.wallet, data.table, data.plans, data::operatorName) }
    // 같은 충전량을 다른 결제 수단으로 냈다면 — 내가 쓴 수단은 실제 금액으로 바꿔 넣는다
    val others = (q?.options ?: emptyList()).map { it.credential.label to it.costKrw }
        .filter { it.first != r.credentialLabel } + (r.credentialLabel to r.costKrw)
    val moe = r.moeReferenceKrw
    val bars = (others + listOfNotNull(moe?.let { "환경부 카드" to it })).distinctBy { it.first }.sortedBy { it.second }
    val maxCost = bars.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("영수증", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (r.simulated) Badge("시연", bg = EvFitColors.CardAlt, fg = EvFitColors.Warning)
        }
        Gauge(num(r.costKrw), "원", 56.sp)
        Text("${r.credentialLabel}${plan?.let { "  ${it.name}" } ?: ""}", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text("${one(r.kwh)}kWh   ${r.fromSoc}% → ${r.toSoc}%", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        bars.forEach { (label, cost) ->
            val mine = label == r.credentialLabel
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row {
                    Text(label, Modifier.weight(1f), color = if (mine) EvFitColors.TextPrimary else EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                    Text(won(cost), color = if (mine) EvFitColors.TextPrimary else EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
                ProgressBar(cost.toFloat() / maxCost, color = if (mine) EvFitColors.FitBlueFill else EvFitColors.Hairline, height = 8)
            }
        }
        moe?.takeIf { it > r.costKrw }?.let { Text("환경부 카드보다 ${won(it - r.costKrw)} 아꼈어요", color = EvFitColors.Saving, style = MaterialTheme.typography.titleSmall) }
        if (plan != null) {
            val cap = plan.monthlyCapKwh
            Muted(if (cap == null) "${plan.name} 남은 약정: 무제한" else "${plan.name} 남은 약정: ${one((cap - (sub?.usedKwhThisMonth ?: 0.0)).coerceAtLeast(0.0))}kWh")
        }
    }
}

@Composable
private fun ModelReport(s: UiState) {
    val data = s.data
    val bench = data?.benchFor(s.wallet.vehicleModel)
    if (bench == null) {
        Panel(Modifier.fillMaxWidth()) { Muted("지갑에서 차종을 고르면 같은 차종 회원의 충전 경험을 보여 드려요") }
        return
    }
    val r = s.lastRecord
    val station = r?.let { data.station(it.stationId) }
    val cls = AppData.chargerClass(station?.maxKw ?: 200)
    val mean = bench.avgKwByClass[cls.toString()] ?: 0.0
    val sd = bench.bands[cls.toString()]?.let { spreadOf(it) } ?: (mean * 0.1)
    val myAvg = r?.let {
        val minutes = if (it.samples.size >= 2) (it.samples.last().tMillis - it.samples.first().tMillis) / 60_000.0 else 0.0
        if (minutes > 0) it.kwh / (minutes / 60.0) else null
    }?.takeIf { station == null || it <= station.maxKw * 1.05 }

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Gauge(num(bench.members.toLong()), "명", 76.sp, EvFitColors.AccentText, Modifier.alignByBaseline())
        Text("같은 ${bench.model}을 타는 회원이에요", Modifier.alignByBaseline(), color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
    }
    // 왼쪽 분포 카드와 오른쪽 두 카드 묶음의 위·아래 선을 맞춘다
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.35f).fillMaxHeight()) {
            Text("${cls}kW급 충전 평균 속도 분포", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Muted(if (myAvg != null) "내 최근 충전은 회원 분포에서 이 위치예요" else "충전을 마치면 내 위치를 표시해요")
            MemberDistribution(mean, sd, myAvg, height = 420.dp)
            SyntheticLabel()
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Panel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                    Instrument("충전 성공률", one(bench.successRatePct), "%")
                    Instrument("만족도", one(bench.satisfaction), "/ 5")
                }
                SyntheticLabel()
            }
            Panel(Modifier.fillMaxWidth().weight(1f)) {
                Text("충전기 용량별 평균 속도", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                val maxAvg = bench.avgKwByClass.values.maxOrNull() ?: 1.0
                listOf(50, 100, 200, 350).forEach { c ->
                    val v = bench.avgKwByClass[c.toString()] ?: 0.0
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("${c}kW급", Modifier.width(84.dp), color = if (c == cls) EvFitColors.TextPrimary else EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                        ProgressBar((v / maxAvg).toFloat(), Modifier.weight(1f), color = if (c == cls) EvFitColors.FitBlueFill else EvFitColors.Hairline, height = 10)
                        Box(Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) { Gauge("${v.toInt()}", null, 26.sp) }
                    }
                }
                Muted("내 차는 ${bench.peakKw}kW까지 받아요. 그보다 큰 충전기도 속도는 비슷해요.")
            }
        }
    }
}

@Composable
private fun MonthReport(s: UiState) {
    val month = Prefs.currentMonth()
    val fmt = DateTimeFormatter.ofPattern("M/d HH:mm")
    val mine = s.records.filter { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault())).toString() == month }
    val spend = mine.sumOf { it.costKrw }
    val saved = mine.sumOf { (it.moeReferenceKrw ?: it.costKrw) - it.costKrw }
    // 지표 4개는 아래 두 카드와 같은 폭(1.4 : 1)으로 나눠 카드 왼쪽 선에 맞춘다
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.weight(1.4f)) {
            Instrument("${month.replace("-", "년 ")}월 지출", num(spend), "원", modifier = Modifier.weight(1f))
            Instrument("아낀 돈", num(saved), "원", "환경부 카드로 냈을 때와 비교", color = EvFitColors.Saving, modifier = Modifier.weight(1f))
        }
        Row(Modifier.weight(1f)) {
            Instrument("충전", "${mine.size}", "회", modifier = Modifier.weight(1f))
            Instrument("충전량", one(mine.sumOf { it.kwh }), "kWh", modifier = Modifier.weight(1f))
        }
    }
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Panel(Modifier.weight(1.4f).fillMaxHeight()) {
            Text("충전별 금액", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            if (mine.isEmpty()) Muted("이번 달 충전 기록이 아직 없어요")
            val maxRef = mine.maxOfOrNull { it.moeReferenceKrw ?: it.costKrw }?.coerceAtLeast(1) ?: 1
            mine.reversed().forEach { r ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Text(r.stationName, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(fmt)}   ${r.credentialLabel}   ${one(r.kwh)}kWh",
                                color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Gauge(num(r.costKrw), "원", 28.sp)
                            r.moeReferenceKrw?.let { Text("${won(it - r.costKrw)} 아낌", color = EvFitColors.Saving, style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                    r.moeReferenceKrw?.let { ProgressBar(it.toFloat() / maxRef, color = EvFitColors.Hairline, height = 8) }
                    ProgressBar(r.costKrw.toFloat() / maxRef, color = EvFitColors.FitBlueFill, height = 8)
                }
            }
            if (mine.isNotEmpty()) Text("위 막대는 환경부 카드, 아래 막대는 실제로 낸 금액이에요", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Panel(Modifier.weight(1f).fillMaxHeight()) {
            Text("구독 약정", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            if (s.wallet.subscriptions.isEmpty()) Muted("지갑에 구독이 없어요")
            s.wallet.subscriptions.forEach { sub ->
                val plan = s.data?.plans?.get(sub.planId) ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        AllowanceRing(sub.usedKwhThisMonth, plan.monthlyCapKwh)
                        Text(plan.monthlyCapKwh?.let { "${(((it - sub.usedKwhThisMonth) / it).coerceIn(0.0, 1.0) * 100).toInt()}%" } ?: "무제한",
                            color = EvFitColors.TextPrimary, style = MaterialTheme.typography.labelLarge)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(plan.name, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text("이번 달 ${one(sub.usedKwhThisMonth)}kWh 사용", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                        Text(plan.monthlyCapKwh?.let { "남은 약정 ${one((it - sub.usedKwhThisMonth).coerceAtLeast(0.0))}kWh" } ?: "약정 무제한",
                            color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
