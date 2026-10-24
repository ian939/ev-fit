// EV FIT 화면 골격 — 가로는 왼쪽 레일, 세로는 하단 바. 상단바(워드마크·모드 배지·마이크)와 화면 전환
package com.ian.evfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.EvFitTheme
import com.ian.evfit.ui.theme.EvFitWordmark

private val TABS = listOf(Tab.HOME to ("⌂" to "홈"), Tab.FINDER to ("⚡" to "충전소"), Tab.REPORT to ("▤" to "리포트"), Tab.WALLET to ("◈" to "지갑"))

@Composable
fun EvFitApp(vm: AppViewModel) {
    val s by vm.ui.collectAsStateWithLifecycle()
    EvFitTheme {
        Surface(Modifier.fillMaxSize(), color = EvFitColors.Navy) {
            if (s.loading) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    EvFitWordmark(56)
                    Muted("내 차에 딱 맞는 충전 — 데이터 불러오는 중")
                }
                return@Surface
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val wide = maxWidth >= 900.dp
                if (wide) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(containerColor = EvFitColors.Card) {
                            Spacer(Modifier.size(12.dp))
                            TABS.forEach { (tab, label) ->
                                NavigationRailItem(
                                    selected = s.nav.tab == tab && s.nav.detailStationId == null,
                                    onClick = { vm.selectTab(tab) },
                                    icon = { Text(label.first, fontSize = 26.sp) },
                                    label = { Text(label.second, fontSize = 18.sp) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = EvFitColors.TextPrimary, selectedTextColor = EvFitColors.TextPrimary,
                                        unselectedIconColor = EvFitColors.TextSecondary, unselectedTextColor = EvFitColors.TextSecondary,
                                        indicatorColor = EvFitColors.ActionBg,
                                    ),
                                )
                            }
                        }
                        MainArea(s, vm, wide, Modifier.weight(1f))
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        MainArea(s, vm, wide, Modifier.weight(1f))
                        NavigationBar(containerColor = EvFitColors.Card) {
                            TABS.forEach { (tab, label) ->
                                NavigationBarItem(
                                    selected = s.nav.tab == tab,
                                    onClick = { vm.selectTab(tab) },
                                    icon = { Text(label.first, fontSize = 24.sp) },
                                    label = { Text(label.second, fontSize = 17.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = EvFitColors.TextPrimary, selectedTextColor = EvFitColors.TextPrimary,
                                        unselectedIconColor = EvFitColors.TextSecondary, unselectedTextColor = EvFitColors.TextSecondary,
                                        indicatorColor = EvFitColors.ActionBg,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainArea(s: UiState, vm: AppViewModel, wide: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TopBar(s, vm)
        s.banner?.let { b ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(EvFitColors.CardAlt).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(b, Modifier.weight(1f), color = EvFitColors.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text("닫기", color = EvFitColors.AccentText, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { vm.clearBanner() }.padding(8.dp))
            }
        }
        Box(Modifier.fillMaxSize()) {
            val detail = s.nav.detailStationId?.let { s.data?.station(it) }
            when {
                s.nav.chargingOpen && s.session != null -> ChargingScreen(s, vm, wide)
                s.nav.prepOpen && s.prep != null -> PrepScreen(s, vm, wide)
                detail != null -> StationDetailScreen(s, vm, detail, wide)
                else -> when (s.nav.tab) {
                    Tab.HOME -> HomeScreen(s, vm, wide)
                    Tab.FINDER -> FinderScreen(s, vm, wide)
                    Tab.REPORT -> ReportScreen(s, vm, wide)
                    Tab.WALLET -> WalletScreen(s, vm, wide)
                }
            }
        }
    }
}

@Composable
private fun TopBar(s: UiState, vm: AppViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EvFitWordmark(28)
        Spacer(Modifier.weight(1f))
        if (s.restricted) Badge("주행 중", bg = EvFitColors.CardAlt, fg = EvFitColors.Warning)
        if (s.demoMode) Badge("시연 모드", bg = EvFitColors.CardAlt, fg = EvFitColors.Warning)
        if (s.sttAvailable) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(EvFitColors.ActionBg).clickable { vm.listenVoice() },
                contentAlignment = Alignment.Center,
            ) { Text("🎤", fontSize = 24.sp) }
        }
    }
}
