// EV FIT 화면 골격(v2) — 왼쪽 레일(경로·리포트·지갑, 직접 그린 아이콘), 상단 줄(워드마크·상태·마이크)과 화면 전환
package com.ian.evfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.EvFitTheme
import com.ian.evfit.ui.theme.EvFitWordmark

private val TABS = listOf(Tab.ROUTE to "경로", Tab.REPORT to "리포트", Tab.WALLET to "지갑")

@Composable
fun EvFitApp(vm: AppViewModel) {
    val s by vm.ui.collectAsStateWithLifecycle()
    EvFitTheme {
        Surface(Modifier.fillMaxSize(), color = EvFitColors.Bg) {
            if (s.loading) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    EvFitWordmark(56)
                    Muted("내 차에 딱 맞는 충전을 준비하고 있어요")
                }
                return@Surface
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 900.dp
                Row(Modifier.fillMaxSize()) {
                    Rail(s, vm)
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        TopBar(s, vm)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            Content(s, vm, wide)
                            s.banner?.let { b -> Banner(b) { vm.clearBanner() } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Content(s: UiState, vm: AppViewModel, wide: Boolean) {
    when {
        s.nav.chargingOpen && s.session != null -> Padded { ChargingScreen(s, vm, wide) }
        s.nav.prepOpen && s.prep != null -> Padded { PrepScreen(s, vm, wide) }
        else -> when (s.nav.tab) {
            Tab.ROUTE -> RouteScreen(s, vm)
            Tab.REPORT -> Padded { ReportScreen(s, vm, wide) }
            Tab.WALLET -> Padded { WalletScreen(s, vm, wide) }
        }
    }
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 20.dp)) { content() }
}

@Composable
private fun Rail(s: UiState, vm: AppViewModel) {
    Column(
        Modifier.width(104.dp).fillMaxHeight().background(EvFitColors.Rail).padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TABS.forEach { (tab, label) ->
            val on = s.nav.tab == tab && !s.nav.prepOpen && !s.nav.chargingOpen
            Column(
                Modifier.width(88.dp).clip(RoundedCornerShape(16.dp))
                    .background(if (on) EvFitColors.CardAlt else Color.Transparent)
                    .clickable { vm.selectTab(tab) }.padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tint = if (on) EvFitColors.TextPrimary else EvFitColors.TextSecondary
                Canvas(Modifier.size(34.dp)) { railIcon(tab, tint, if (on) EvFitColors.FitBlueFill else tint) }
                Text(label, color = tint, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** 레일 아이콘 — 경로(굽은 길과 두 점), 리포트(막대 셋), 지갑(카드) */
private fun DrawScope.railIcon(tab: Tab, tint: Color, accent: Color) {
    val w = size.width
    val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round)
    when (tab) {
        Tab.ROUTE -> {
            val p = Path().apply {
                moveTo(w * 0.22f, w * 0.80f)
                cubicTo(w * 0.22f, w * 0.45f, w * 0.78f, w * 0.60f, w * 0.78f, w * 0.22f)
            }
            drawPath(p, tint, style = stroke)
            drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.22f, w * 0.82f))
            drawCircle(accent, radius = w * 0.15f, center = Offset(w * 0.78f, w * 0.2f), style = Stroke(w * 0.08f))
        }
        Tab.REPORT -> {
            listOf(0.25f to 0.55f, 0.5f to 0.3f, 0.75f to 0.45f).forEachIndexed { i, (x, top) ->
                drawLine(if (i == 1) accent else tint, Offset(w * x, w * 0.82f), Offset(w * x, w * top), strokeWidth = w * 0.13f, cap = StrokeCap.Round)
            }
        }
        Tab.WALLET -> {
            drawRoundRect(tint, Offset(w * 0.12f, w * 0.24f), Size(w * 0.76f, w * 0.54f), CornerRadius(w * 0.1f), style = stroke)
            drawLine(accent, Offset(w * 0.12f, w * 0.42f), Offset(w * 0.88f, w * 0.42f), strokeWidth = w * 0.09f)
        }
    }
}

@Composable
private fun TopBar(s: UiState, vm: AppViewModel) {
    Row(
        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 32.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 워드마크와 차종은 같은 기준선에, 그 묶음은 줄 가운데에
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EvFitWordmark(28, Modifier.alignByBaseline())
            Text(s.wallet.vehicleModel ?: "차종을 지갑에서 골라 주세요", Modifier.alignByBaseline(), color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        Spacer(Modifier.weight(1f))
        if (s.restricted) Badge("주행 중", bg = EvFitColors.CardAlt, fg = EvFitColors.Warning)
        if (s.demoMode) Badge("시연 모드", bg = EvFitColors.CardAlt, fg = EvFitColors.Warning)
        if (s.sttAvailable) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(EvFitColors.ActionBg).clickable { vm.listenVoice() },
                contentAlignment = Alignment.Center,
            ) { Canvas(Modifier.size(24.dp)) { mic(Color.White) } }
        }
    }
}

private fun DrawScope.mic(c: Color) {
    val w = size.width
    drawRoundRect(c, Offset(w * 0.34f, w * 0.05f), Size(w * 0.32f, w * 0.55f), CornerRadius(w * 0.16f))
    drawArc(c, 0f, 180f, false, Offset(w * 0.18f, w * 0.22f), Size(w * 0.64f, w * 0.52f), style = Stroke(w * 0.09f, cap = StrokeCap.Round))
    drawLine(c, Offset(w * 0.5f, w * 0.74f), Offset(w * 0.5f, w * 0.94f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
}

@Composable
private fun Banner(text: String, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.clip(RoundedCornerShape(16.dp)).background(EvFitColors.CardAlt).padding(start = 22.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.bodyLarge)
            Text(
                "닫기", color = EvFitColors.AccentText, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClose).padding(14.dp),
            )
        }
    }
}
