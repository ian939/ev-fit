// 충전 준비 화면(v2) — 도착 전 순서(결제 계정→PnC 계약 전환→충전구→목표 충전량→확인)와 예상 금액·충전 시작
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.AppData
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.gauge

private enum class StepState { DONE, WAIT, WARN }

@Composable
fun PrepScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    val p = s.prep ?: return
    val best = p.quote.best ?: return
    val operator = s.data?.operatorName(p.station.bid) ?: ""
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        Column(Modifier.weight(1.2f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            BackRow("경로") { vm.closePrep() }
            Text("충전 준비", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
            Text(p.station.name, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Attr("사업자", operator)
                Attr("최대 출력", "${p.station.maxKw}kW")
                Attr("지금 배터리", "${s.soc ?: "—"}%")
            }
            Hairline()
            Step(1, "결제 계정 확정", "${best.credential.label}${best.appliedPlan?.let { "  ${it.name}" } ?: ""}", StepState.DONE)
            Step(
                2, "PnC 계약 전환",
                p.contract?.let { "${it.emaid}  ${it.label}로 인증${if (it.simulated) "(시뮬레이션)" else ""}" } ?: "전환하는 중",
                if (p.contract != null) StepState.DONE else StepState.WAIT,
            )
            Step(3, "충전구 열기", p.portResult ?: "차에 요청하는 중", stateOf(p.portResult, "충전구를"))
            Step(4, "목표 충전량", p.limitResult ?: "차에 요청하는 중", stateOf(p.limitResult, "목표"))
            Step(5, "충전 전 확인", p.tips.joinToString("\n"), StepState.DONE)
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(EvFitColors.Panel).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("예상 금액", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.titleMedium)
            Gauge(num(best.costKrw), "원", 84.sp)
            Text("${one(p.quote.kwh)}kWh × ${unitPrice(best.effectiveUnitPrice)}", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            p.quote.savingVsMoe?.takeIf { it > 0 }?.let {
                Text("환경부 카드보다 ${won(it)} 적게 내요", color = EvFitColors.Saving, style = MaterialTheme.typography.titleMedium)
            }
            Hairline()
            // 같은 차종 기대 곡선으로 본 이번 충전 — 시작·목표 SOC 구간과 예상 시간
            val from = s.soc ?: UiState.ASSUMED_SOC
            val target = s.wallet.targetSoc
            val expected = s.data?.benchFor(s.wallet.vehicleModel)?.bands?.get(AppData.chargerClass(p.station.maxKw).toString())
            val expMin = expected?.let { ex ->
                (from until target).sumOf { soc -> 0.01 * s.batteryKwh / (ex.firstOrNull { soc >= it.from && soc < it.to }?.median ?: 100.0) * 60 }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${from}%에서 ${target}%까지", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                expMin?.let { Gauge("약 ${it.toInt()}", "분", 36.sp) }
            }
            if (expected != null) {
                SocSpeedChart(expected, emptyList(), from, target, Modifier.weight(1f), height = 220.dp, compact = true)
                SyntheticLabel("같은 차종 기대 속도(시연용 합성 데이터)로 계산했어요")
            } else {
                Box(Modifier.weight(1f))
            }
            if (s.demoMode) EvButton("충전 시작(시연)", { vm.startDemoCharging() }, Modifier.fillMaxWidth())
            else Text("충전 커넥터를 꽂으면 자동으로 충전 기록을 시작해요", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun stateOf(result: String?, okPrefix: String) = when {
    result == null -> StepState.WAIT
    result.contains("미지원") -> StepState.WARN
    result.startsWith(okPrefix) -> StepState.DONE
    else -> StepState.WAIT
}

/** 순서가 있는 준비 단계 — 번호 링, 끝나면 민트 체크 */
@Composable
private fun Step(n: Int, title: String, detail: String, state: StepState) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            val ring = when (state) { StepState.DONE -> EvFitColors.Saving; StepState.WARN -> EvFitColors.Warning; StepState.WAIT -> EvFitColors.Hairline }
            Canvas(Modifier.size(42.dp)) { drawCircle(ring, style = Stroke(3.dp.toPx())) }
            Text("$n", style = gauge(22.sp, color = if (state == StepState.WAIT) EvFitColors.TextSecondary else Color.White))
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = if (state == StepState.WARN) EvFitColors.Warning else EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
