// 충전 중 — SOC·누적 kWh·현재 금액·순간 kW와 PnC 인증 계정 표시
package com.ian.evfit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
fun ChargingScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    val c = s.session ?: return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle("충전 중", tag = "EV FIT")
        Text(c.station.name, style = MaterialTheme.typography.headlineMedium, color = EvFitColors.TextPrimary)
        Muted("PnC 인증: ${c.option.credential.label}${s.prep?.contract?.let { " · ${it.emaid}" } ?: ""}${if (c.simulated) " (시뮬레이션)" else ""}")
        EvCard(Modifier.fillMaxWidth(), highlight = true) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${c.soc}", fontSize = 80.sp, fontWeight = FontWeight.Black, color = EvFitColors.TextPrimary)
                Text("%", fontSize = 32.sp, color = EvFitColors.TextSecondary)
                Muted("  ${c.fromSoc}% → 목표 ${s.wallet.targetSoc}%")
            }
            ProgressBar((c.soc - c.fromSoc).toFloat() / (s.wallet.targetSoc - c.fromSoc).coerceAtLeast(1))
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                Metric("충전량", "${one(c.kwh)}kWh")
                Metric("현재 금액", won(c.costKrw))
                Metric("충전 속도", c.instantKw?.let { "${it.toInt()}kW" } ?: "—")
            }
        }
        if (c.simulated) EvButton("충전 종료(시연)", { vm.finishSession() }, secondary = true)
        else Muted("커넥터를 뽑거나 목표 충전량에 닿으면 자동으로 끝나요")
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Muted(label)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = EvFitColors.TextPrimary)
    }
}
