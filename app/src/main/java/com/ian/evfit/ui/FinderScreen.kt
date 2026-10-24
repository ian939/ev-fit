// 충전소 찾기(FIT Route) — 경로상·목적지·내 주변 충전소를 FIT 점수·최저가·거리 순으로 보여 준다
package com.ian.evfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.engine.FitScore
import com.ian.evfit.engine.RankedStation
import com.ian.evfit.ui.theme.EvFitColors

@Composable
fun FinderScreen(s: UiState, vm: AppViewModel, wide: Boolean) {
    var showInfo by remember { mutableStateOf(false) }
    val f = s.finder
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("내 단가로 찾기", tag = "FIT Route")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Segment("경로상 충전", f.center == FinderCenter.ROUTE_STOP, enabled = s.route?.stops?.isNotEmpty() == true) { vm.findStations(FinderCenter.ROUTE_STOP) }
            Segment("목적지 주변", f.center == FinderCenter.DESTINATION, enabled = s.route?.destination != null) { vm.findStations(FinderCenter.DESTINATION) }
            Segment("내 주변", f.center == FinderCenter.CURRENT) { vm.findStations(FinderCenter.CURRENT) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Segment("FIT 추천", f.sort == FinderSort.FIT) { vm.findStations(sort = FinderSort.FIT) }
            Segment("최저가", f.sort == FinderSort.PRICE) { vm.findStations(sort = FinderSort.PRICE) }
            Segment("가까운 순", f.sort == FinderSort.DISTANCE) { vm.findStations(sort = FinderSort.DISTANCE) }
            Text("ⓘ FIT 점수", color = EvFitColors.AccentText, fontSize = 18.sp, modifier = Modifier.clickable { showInfo = true }.padding(8.dp))
            if (s.nextStop != null || s.route != null) {
                Text("내비 주변 충전소 열기", color = EvFitColors.AccentText, fontSize = 18.sp,
                    modifier = Modifier.clickable { vm.openNearbyChargersInNavi() }.padding(8.dp))
            }
        }
        Muted("예상 충전량 ${one(f.kwh)}kWh 기준${if (s.socAssumed) " (SOC 미확인 — 30% 가정)" else ""}")
        if (f.results.isEmpty()) {
            EvCard(Modifier.fillMaxWidth()) {
                Muted("결제 가능한 충전소가 없습니다")
                EvButton("지갑 확인", { vm.selectTab(Tab.WALLET) }, secondary = true)
            }
        } else {
            // 정렬·기준을 바꾸면 LazyColumn이 key로 이전 첫 항목 위치를 유지해 새 1위가 가려지므로 맨 위로 돌린다
            val listState = rememberLazyListState()
            LaunchedEffect(f.results) { listState.scrollToItem(0) }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 주행 중(UX 제한)에는 목록을 5개로 줄인다 — 목록을 받은 뒤 주행을 시작해도 바로 적용되게 화면에서 자른다
                itemsIndexed(if (s.restricted) f.results.take(5) else f.results, key = { _, r -> r.quote.station.id }) { i, r ->
                    StationRow(s, r, topFit = f.sort == FinderSort.FIT && i == 0, wide = wide) { vm.openDetail(r.quote.station.id) }
                }
            }
        }
    }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton({ showInfo = false }) { Text("확인", fontSize = 19.sp) } },
            title = { Text("FIT 점수란?") },
            text = {
                Text(
                    "후보 충전소끼리 비교해 요금 ${(FitScore.W_PRICE * 100).toInt()} + 시간 ${(FitScore.W_TIME * 100).toInt()} + 신뢰도 ${(FitScore.W_TRUST * 100).toInt()}로 매긴 점수예요.\n" +
                        "시간 = 경로 추가 이동(거리×2분, 추정) + 예상 충전 시간(동일 차종 평균 속도).\n" +
                        "신뢰도 = 동일 차량 충전 성공률. 시간·신뢰도에는 추정과 시연용 합성 데이터가 들어 있어요.",
                    fontSize = 18.sp,
                )
            },
        )
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) EvFitColors.ActionBg else EvFitColors.Card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        color = when {
            !enabled -> EvFitColors.Disabled
            selected -> androidx.compose.ui.graphics.Color.White
            else -> EvFitColors.TextSecondary
        },
        fontSize = 19.sp, fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun StationRow(s: UiState, r: RankedStation, topFit: Boolean, wide: Boolean, onClick: () -> Unit) {
    val q = r.quote
    val best = q.best ?: return
    val operator = s.data?.operatorName(q.station.bid) ?: ""
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (topFit) EvFitColors.CardAlt else EvFitColors.Card)
            .clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (topFit) Badge("✓ 최적 FIT")
            Text(q.station.name, Modifier.weight(1f), color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (r.fit > 0.0) Text("FIT ${one(r.fit)}", color = EvFitColors.AccentText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Muted("$operator · 최대 ${q.station.maxKw}kW · ${one(r.distanceKm)}km${if (q.station.highway) " · 고속도로" else ""}")
                if (wide) Muted("내 멤버십 적용 요금 · 경로 추가 +${r.detourMin.toInt()}분(추정) · 충전 약 ${r.chargeMin.toInt()}분")
                q.savingVsMoe?.takeIf { it > 0 }?.let { Text("환경부 카드 대비 -${won(it)}", color = EvFitColors.Saving, fontSize = 18.sp) }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Badge(best.credential.label)
                Text("${unitPrice(best.effectiveUnitPrice)}/kWh", color = EvFitColors.TextSecondary, fontSize = 18.sp)
                Text(won(best.costKrw), color = EvFitColors.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}
