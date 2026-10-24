// 충전소 상세 — 결제 수단별 비교표(FIT Price)와 "이 충전소, 내 차에 맞아요"(FIT Insight, 합성)
package com.ian.evfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.AppData
import com.ian.evfit.engine.PayOption
import com.ian.evfit.engine.Station
import com.ian.evfit.engine.SyntheticStats
import com.ian.evfit.ui.theme.EvFitColors

@Composable
fun StationDetailScreen(s: UiState, vm: AppViewModel, station: Station, wide: Boolean) {
    val q = vm.quoteFor(station)
    val fit = vm.fitFor(station.id)
    val operator = s.data?.operatorName(station.bid) ?: station.bid
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackRow { vm.back() }
        Text(station.name, style = MaterialTheme.typography.headlineMedium, color = EvFitColors.TextPrimary)
        Muted("$operator · 최대 ${station.maxKw}kW${if (station.highway) " · 고속도로 휴게소" else ""} · ${station.addr}")
        fit?.let { Muted("FIT ${one(it.fit)} · 경로 추가 +${it.detourMin.toInt()}분(추정) · 충전 약 ${it.chargeMin.toInt()}분") }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(14.dp)) { PriceTable(q) ; Actions(vm, station, q?.best != null) }
                Column(Modifier.weight(1f)) { InsightCard(s, station) }
            }
        } else {
            PriceTable(q)
            Actions(vm, station, q?.best != null)
            InsightCard(s, station)
        }
    }
}

@Composable
private fun PriceTable(q: com.ian.evfit.engine.Quote?) {
    EvCard(Modifier.fillMaxWidth(), highlight = true) {
        SectionTitle("결제 수단별 예상 요금", tag = "FIT Price")
        if (q == null) {
            Muted("요금 정보를 계산할 수 없어요")
            return@EvCard
        }
        Muted("예상 충전량 ${one(q.kwh)}kWh · 단가 기준 ${q.stage.key.uppercase()} 출력 구간")
        q.options.forEachIndexed { i, o -> OptionRow(o, i == 0) }
        q.unavailable.forEach { u ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(u.credential.label, color = EvFitColors.Disabled, fontSize = 19.sp)
                Text(u.reason, color = EvFitColors.Disabled, fontSize = 17.sp)
            }
        }
        q.moeReferenceKrw?.let { Muted("환경부 카드(전국 공통 로밍가) 기준 ${won(it)}") }
    }
}

@Composable
private fun OptionRow(o: PayOption, best: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (best) Badge("최저") else Text("  ", fontSize = 17.sp)
            Text(o.credential.label, Modifier.weight(1f), color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("${unitPrice(o.effectiveUnitPrice)}/kWh", color = EvFitColors.TextSecondary, fontSize = 18.sp)
            Text(won(o.costKrw), color = EvFitColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Muted(o.parts.joinToString(" + ") { "${it.desc} ${one(it.kwh)}kWh×${unitPrice(it.unitPriceKrw)}" })
        o.notes.forEach { Text(it, color = EvFitColors.Warning, fontSize = 17.sp) }
    }
}

@Composable
private fun Actions(vm: AppViewModel, station: Station, payable: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EvButton("이 충전소로 안내받기 →", { vm.setAsWaypoint(station) })
        EvButton("목적지로 설정", { vm.setDestination(station) }, secondary = true)
        EvButton("충전 준비", { vm.prepare(station) }, secondary = true, enabled = payable)
    }
}

@Composable
private fun InsightCard(s: UiState, station: Station) {
    val bench = s.data?.benchFor(s.wallet.vehicleModel)
    EvCard(Modifier.fillMaxWidth()) {
        SectionTitle("이 충전소, 내 차에 맞아요", tag = "FIT Insight")
        SyntheticLabel()
        if (bench == null) {
            Muted("지갑에서 차종을 고르면 동일 차종 기준으로 보여 드려요")
            return@EvCard
        }
        val cls = AppData.chargerClass(station.maxKw)
        val avg = bench.avgKwByClass[cls.toString()] ?: 0.0
        val min = avg * 0.75
        val max = minOf(station.maxKw * 0.95, avg * 1.15)
        Muted("동일 차량 충전 성공률")
        Text("${one(SyntheticStats.successPct(station.id))}%", fontSize = 44.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
        Muted("동일 모델 충전 속도(${cls}kW급)")
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            listOf("최저" to min, "평균" to avg, "최고" to max).forEach { (k, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Muted(k)
                    Text("${v.toInt()}", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = if (k == "평균") EvFitColors.AccentText else EvFitColors.TextPrimary)
                    Muted("kW")
                }
            }
        }
        Text("★ ${one(bench.satisfaction)} / 5.0 동일 차종 경험 기반", color = EvFitColors.TextPrimary, fontSize = 19.sp)
    }
}
