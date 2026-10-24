// 충전 준비 — 결제 계정·PnC 계약 전환(시뮬레이션)·충전구 열기·목표 SOC·충전 전 확인을 한 화면에
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
import com.ian.evfit.ui.theme.EvFitColors

@Composable
fun PrepScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    val p = s.prep ?: return
    val best = p.quote.best ?: return
    val operator = s.data?.operatorName(p.station.bid) ?: ""
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BackRow { vm.closePrep() }
        SectionTitle("충전 준비", tag = "EV FIT")
        Text("${p.station.name} · $operator", style = MaterialTheme.typography.headlineMedium, color = EvFitColors.TextPrimary)
        EvCard(Modifier.fillMaxWidth(), highlight = true) {
            Step("결제 계정", "${best.credential.label}${best.appliedPlan?.let { " (${it.name})" } ?: ""}", done = true)
            Step(
                "PnC 계약 전환",
                p.contract?.let { "${it.emaid} · ${it.label}로 인증${if (it.simulated) " (시뮬레이션)" else ""}" } ?: "전환 중…",
                done = p.contract != null,
            )
            Step("충전구 열기", p.portResult ?: "요청 중…", done = p.portResult?.startsWith("충전구를") == true, warn = p.portResult?.contains("미지원") == true)
            Step("목표 충전량", p.limitResult ?: "요청 중…", done = p.limitResult?.startsWith("목표") == true && p.limitResult.contains("설정"), warn = p.limitResult?.contains("미지원") == true)
            Step("충전 전 확인", p.tips.joinToString(" / "), done = true)
        }
        EvCard(Modifier.fillMaxWidth()) {
            Muted("예상 ${one(p.quote.kwh)}kWh · ${unitPrice(best.effectiveUnitPrice)}/kWh")
            Text("예상 ${won(best.costKrw)}", fontSize = 40.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
            p.quote.savingVsMoe?.takeIf { it > 0 }?.let { Text("환경부 카드보다 ${won(it)} 절약", color = EvFitColors.Saving, style = MaterialTheme.typography.titleMedium) }
        }
        if (s.demoMode) EvButton("충전 시작(시연)", { vm.startDemoCharging() })
        else Muted("충전 커넥터를 꽂으면 자동으로 충전 기록을 시작해요")
    }
}

@Composable
private fun Step(title: String, detail: String, done: Boolean, warn: Boolean = false) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            when { warn -> "⚠"; done -> "✓"; else -> "…" },
            color = when { warn -> EvFitColors.Warning; done -> EvFitColors.Saving; else -> EvFitColors.TextSecondary },
            fontSize = 26.sp, fontWeight = FontWeight.Black,
        )
        Column {
            Text(title, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Muted(detail)
        }
    }
}
