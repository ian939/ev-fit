// 홈 — 슬로건, 차량 상태, 다음 충전(FIT Price) 카드, 이번 달 요약, 시연 버튼
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
import com.ian.evfit.data.Prefs
import com.ian.evfit.engine.Credential
import com.ian.evfit.ui.theme.EvFitColors
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun HomeScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("내 차에 딱 맞는 충전", color = EvFitColors.AccentText, style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Badge("🚘 ${s.wallet.vehicleModel ?: "차종 미선택"} · 내 멤버십 적용 중", bg = EvFitColors.Card, fg = EvFitColors.TextPrimary)
        }
        if (s.vehicleAssumed) Text("지갑에서 차종을 고르세요 — 70kWh 가정", color = EvFitColors.Warning, style = MaterialTheme.typography.bodyMedium)
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    VehicleCard(s)
                    MonthCard(s)
                }
                Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    NextChargeCard(s, vm)
                    if (s.demoMode) DemoButtons(s, vm)
                }
            }
        } else {
            VehicleCard(s)
            NextChargeCard(s, vm)
            if (s.demoMode) DemoButtons(s, vm)
            MonthCard(s)
        }
    }
}

@Composable
private fun VehicleCard(s: UiState) {
    EvCard(Modifier.fillMaxWidth()) {
        Muted("배터리")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${s.soc ?: "—"}", fontSize = 64.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
            Text("%", fontSize = 28.sp, color = EvFitColors.TextSecondary)
        }
        Muted("목표 ${s.wallet.targetSoc}% · 배터리 ${one(s.batteryKwh)}kWh")
        if (s.soc == null) Text("SOC 미확인 — 30% 가정", color = EvFitColors.Warning, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NextChargeCard(s: UiState, vm: AppViewModel) {
    val a = s.nextStop
    EvCard(Modifier.fillMaxWidth(), highlight = true) {
        SectionTitle("다음 충전", tag = "FIT Price")
        if (a == null) {
            Muted("경로에 충전 정차가 없어요. 내비에서 목적지를 정하면 자동으로 찾아요.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (s.demoMode) EvButton("시연 시작", { vm.startDemo() })
                EvButton("내 주변 충전소", { vm.selectTab(Tab.FINDER); vm.findStations(FinderCenter.CURRENT) }, secondary = true)
            }
            return@EvCard
        }
        val top = a.atStop.firstOrNull() ?: a.nearby.firstOrNull()
        Text(a.stop.name, style = MaterialTheme.typography.headlineMedium, color = EvFitColors.TextPrimary)
        if (top == null) {
            Muted("이 정차지에서 지갑의 결제 수단으로 결제할 수 있는 충전소가 없어요")
            return@EvCard
        }
        val q = top.quote
        val best = q.best!!
        val operator = s.data?.operatorName(q.station.bid) ?: ""
        Muted("$operator · 최대 ${q.station.maxKw}kW · 예상 ${one(a.kwh)}kWh${if (q.station.highway) " · 고속도로" else ""}")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Badge(best.credential.label + (best.appliedPlan?.let { " · ${it.name.substringAfterLast(' ')}" } ?: ""))
            Text("${unitPrice(best.effectiveUnitPrice)}/kWh", color = EvFitColors.TextSecondary, fontSize = 20.sp)
        }
        Text("예상 ${won(best.costKrw)}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
        q.savingVsMoe?.takeIf { it > 0 }?.let {
            Text("환경부 카드보다 ${won(it)} 절약", color = EvFitColors.Saving, style = MaterialTheme.typography.titleMedium)
        }
        a.moeOnly.filter { it.quote.station.id != q.station.id }.firstOrNull()?.let { m ->
            Muted("같은 휴게소 ${s.data?.operatorName(m.quote.station.bid)} 충전기는 환경부 카드만 가능")
        }
        best.notes.firstOrNull()?.let { Muted(it) }
        q.options.filter { it.credential is Credential.Account }.flatMap { it.notes }.firstOrNull { it !in best.notes }?.let { Muted(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvButton("충전 준비", { vm.prepare(q.station) })
            EvButton("다른 충전소 비교", { vm.selectTab(Tab.FINDER); vm.findStations(FinderCenter.ROUTE_STOP) }, secondary = true)
        }
    }
}

@Composable
private fun DemoButtons(s: UiState, vm: AppViewModel) {
    EvCard(Modifier.fillMaxWidth()) {
        Muted("시연 모드 — 서울 → 정부대전청사, 망향휴게소 충전")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvButton("시연 시작", { vm.startDemo() }, secondary = true)
            EvButton("도착(시연)", { vm.arrive() }, secondary = true, enabled = s.nextStop != null)
            EvButton("충전 시작(시연)", { vm.startDemoCharging() }, secondary = true, enabled = s.prep != null)
        }
    }
}

@Composable
private fun MonthCard(s: UiState) {
    val month = Prefs.currentMonth()
    val mine = s.records.filter { YearMonth.from(Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault())).toString() == month }
    EvCard(Modifier.fillMaxWidth()) {
        SectionTitle("이번 달")
        Muted("충전 ${mine.size}회 · 지출 ${won(mine.sumOf { it.costKrw })} · 절약 ${won(mine.sumOf { r -> (r.moeReferenceKrw ?: r.costKrw) - r.costKrw })}")
        s.wallet.subscriptions.forEach { sub ->
            val plan = s.data?.plans?.get(sub.planId) ?: return@forEach
            val cap = plan.monthlyCapKwh
            Text(plan.name, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
            if (cap == null) Muted("약정 무제한 · 이번 달 ${one(sub.usedKwhThisMonth)}kWh 사용")
            else {
                ProgressBar((sub.usedKwhThisMonth / cap).toFloat())
                Muted("남은 약정 ${one((cap - sub.usedKwhThisMonth).coerceAtLeast(0.0))} / ${one(cap)}kWh")
            }
        }
    }
}
