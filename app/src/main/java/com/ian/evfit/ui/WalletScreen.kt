// 지갑 — 차종·환경부 카드·회원 사업자·구독(이번 달 사용량)·목표 SOC·화면(다크/라이트)·시연 모드·데이터 기준일·브랜드 고지
package com.ian.evfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.Prefs
import com.ian.evfit.engine.ActiveSubscription
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.EvFitWordmark

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    val data = s.data ?: return
    val w = s.wallet
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("내 지갑")
        if (s.restricted) {
            EvCard(Modifier.fillMaxWidth()) { Muted("정차 중에만 편집할 수 있어요") }
            return@Column
        }

        EvCard(Modifier.fillMaxWidth()) {
            Text("차종", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            var brand by remember { mutableStateOf(data.vehicle(w.vehicleModel)?.brand ?: "현대") }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("현대", "기아", "제네시스").forEach { b -> Chip(b, brand == b) { brand = b } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.vehicles.filter { it.brand == brand }.forEach { v ->
                    Chip(v.model, w.vehicleModel == v.model) { vm.updateWallet(w.copy(vehicleModel = v.model, batteryKwh = v.batteryKwh)) }
                }
            }
        }

        EvCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("환경부 카드", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Muted("전국 공통 로밍가로 결제(협약 민간 충전기 포함)")
                }
                Switch(w.moeCard, { vm.updateWallet(w.copy(moeCard = it)) }, colors = switchColors())
            }
        }

        EvCard(Modifier.fillMaxWidth()) {
            Text("회원 사업자", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Muted("자주 쓰는 충전사업자 회원카드를 고르세요(급속 충전소 많은 순)")
            val top = data.operatorFastStations.filterKeys { it != "ME" && !it.startsWith("X:") }
                .entries.sortedByDescending { it.value }.take(15).map { it.key }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                top.forEach { bid ->
                    Chip(data.operatorName(bid), bid in w.memberships) {
                        vm.updateWallet(w.copy(memberships = if (bid in w.memberships) w.memberships - bid else w.memberships + bid))
                    }
                }
            }
        }

        EvCard(Modifier.fillMaxWidth()) {
            Text("구독", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Muted("구독은 그 사업자 카드에 붙어 자동 적용돼요 · 기준 ${data.plansAsOf}")
            data.plans.values.groupBy { it.brand }.forEach { (brand, plans) ->
                Muted(if (brand == "현대") "현대 연계" else "사업자 구독")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    plans.forEach { p ->
                        val on = w.subscriptions.any { it.planId == p.id }
                        Chip("${p.name} · ${unitPrice(p.fastPriceKrwKwh)}", on) {
                            vm.updateWallet(w.copy(subscriptions = if (on) w.subscriptions.filter { it.planId != p.id }
                            else w.subscriptions + ActiveSubscription(p.id, 0.0, Prefs.currentMonth())))
                        }
                    }
                }
            }
            w.subscriptions.forEach { sub ->
                val p = data.plans[sub.planId] ?: return@forEach
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(p.name, Modifier.weight(1f), color = EvFitColors.TextPrimary, fontSize = 19.sp)
                    Muted("이번 달 ${one(sub.usedKwhThisMonth)}kWh${p.monthlyCapKwh?.let { " / ${one(it)}" } ?: " / 무제한"}")
                    EvButton("−10", { vm.updateWallet(w.copy(subscriptions = w.subscriptions.map { if (it.planId == p.id) it.copy(usedKwhThisMonth = (it.usedKwhThisMonth - 10).coerceAtLeast(0.0)) else it })) }, secondary = true)
                    EvButton("+10", { vm.updateWallet(w.copy(subscriptions = w.subscriptions.map { if (it.planId == p.id) it.copy(usedKwhThisMonth = it.usedKwhThisMonth + 10) else it })) }, secondary = true)
                }
            }
        }

        EvCard(Modifier.fillMaxWidth()) {
            Text("목표 충전량", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(70, 80, 90).forEach { t -> Chip("$t%", w.targetSoc == t) { vm.updateWallet(w.copy(targetSoc = t)) } }
            }
        }

        EvCard(Modifier.fillMaxWidth()) {
            Text("화면", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("다크", EvFitColors.dark) { vm.setDarkTheme(true) }
                Chip("라이트", !EvFitColors.dark) { vm.setDarkTheme(false) }
            }
        }

        EvCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("시연 모드", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Muted("위치·경로·충전 흐름을 시나리오로 재생해요. 끄면 차량·내비 실제 값을 써요")
                }
                Switch(s.demoMode, { vm.setDemoMode(it) }, colors = switchColors())
            }
            EvButton("시연 지갑 불러오기", { vm.loadDemoWallet() }, secondary = true)
        }

        EvCard(Modifier.fillMaxWidth()) {
            Text("데이터 기준", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Muted("요금: 무공해차 통합누리집 전기차 충전요금 안내(${data.pricesAsOf}) · 충전소: 환경부 충전소 현황(${data.stationsYm}) · 구독: 각 사 공식 페이지(${data.plansAsOf})")
            Muted("동일 차종 통계·성공률·만족도는 시연용 합성 데이터예요. CPO 연동과 PnC 계약 전환은 시뮬레이션이에요.")
        }

        EvCard(Modifier.fillMaxWidth(), highlight = true) {
            EvFitWordmark(32)
            Text("복잡한 충전은 하나로, 최적의 충전은 나에게.", color = EvFitColors.AccentText, style = MaterialTheme.typography.titleMedium)
            Muted("EV FIT은 공모전 컨셉 브랜드이며 현대자동차 공식 브랜드 자산이 아닙니다.")
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
            .background(if (selected) EvFitColors.ActionBg else EvFitColors.CardAlt)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        color = if (selected) Color.White else EvFitColors.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedTrackColor = EvFitColors.ActionBg, checkedThumbColor = Color.White,
    uncheckedTrackColor = EvFitColors.CardAlt, uncheckedThumbColor = EvFitColors.TextSecondary,
)
