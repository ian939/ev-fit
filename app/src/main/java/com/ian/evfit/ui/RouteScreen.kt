// 경로 화면(v2) — 왼쪽 지도·경로 자, 오른쪽 패널(배터리·도착 SOC, 탭 기준 추천 목록, 고른 충전소 행동)과 충전소 상세 패널
package com.ian.evfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.data.AppData
import com.ian.evfit.engine.CorridorStation
import com.ian.evfit.engine.Credential
import com.ian.evfit.engine.RouteMode
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.gauge

@Composable
fun RouteScreen(s: UiState, vm: AppViewModel) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(0.6f).fillMaxHeight()) {
            RouteMap(s, vm, Modifier.weight(1f).fillMaxWidth())
            if (s.trip.line != null) CorridorRuler(s, Modifier.fillMaxWidth().height(124.dp))
        }
        Box(Modifier.weight(0.4f).fillMaxHeight().background(EvFitColors.Panel)) {
            val detail = s.trip.find(s.nav.detailStationId)
            if (detail != null) StationPanel(s, vm, detail) else RoutePanel(s, vm)
        }
    }
}

private fun modeCaption(mode: RouteMode, kwh: Double): String = when (mode) {
    RouteMode.FIT -> "요금·시간·신뢰도를 5 : 3 : 2로 합친 점수가 높은 곳"
    RouteMode.ON_ROUTE -> "경로를 벗어나지 않고 들를 수 있는 곳부터"
    RouteMode.PRICE -> "같은 ${one(kwh)}kWh를 가장 적은 돈으로 채우는 곳"
    RouteMode.FAST -> "우회와 충전 시간을 합쳐 가장 빨리 끝나는 곳"
}

@Composable
private fun RoutePanel(s: UiState, vm: AppViewModel) {
    val trip = s.trip
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusStrip(s)
        Segmented(RouteMode.entries.map { it.label }, trip.mode.ordinal, { vm.setMode(RouteMode.entries[it]) }, Modifier.fillMaxWidth())
        Text(modeCaption(trip.mode, trip.kwh), color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)

        val rows = trip.ranked.take(6)
        val list = rememberLazyListState()
        LaunchedEffect(trip.mode, trip.ranked.firstOrNull()?.station?.id) { list.scrollToItem(0) }
        if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    if (trip.all.isEmpty()) "경로 옆 5km 안에 결제할 수 있는 충전소가 없어요. 지갑의 결제 수단을 확인해 보세요."
                    else "남은 배터리로 닿는 충전소가 없어요. 가장 가까운 충전소부터 들르세요.",
                    color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
                itemsIndexed(rows, key = { _, r -> r.station.id }) { i, r ->
                    RankRow(s, i, r, r.station.id == trip.selectedId) { vm.select(r.station.id) }
                    if (i < rows.lastIndex) Hairline()
                }
            }
        }
        trip.find(trip.selectedId)?.let { SelectionFooter(s, vm, it) }
        Text(
            "금액은 ${one(trip.kwh)}kWh(${if (trip.basisAssumed) "도착 ${trip.basisSoc}% 가정" else "도착 ${trip.basisSoc}%"} → ${s.wallet.targetSoc}%) 기준, 우회·충전 시간은 추정이에요",
            color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusStrip(s: UiState) {
    val trip = s.trip
    val stop = s.route?.stops?.firstOrNull()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("배터리", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Gauge("${s.soc ?: "—"}", "%", 64.sp)
            Text("주행 가능 ${trip.rangeKm.toInt()}km", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
        }
        if (stop != null || trip.line != null) VHairline()
        if (stop != null && trip.stopKm != null && trip.stopKm >= trip.carKm - 0.5) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${stop.name} 도착", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                Gauge("${stop.arriveSoc ?: trip.basisSoc}", "%", 64.sp, color = EvFitColors.AccentText)
                Text("내비 계획 정차 ${(trip.stopKm - trip.carKm).toInt()}km 앞", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
            }
        } else if (trip.line != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("목적지까지", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Gauge("${(trip.line.lengthKm - trip.carKm).toInt()}", "km", 64.sp)
                val enough = trip.rangeKm >= trip.line.lengthKm - trip.carKm
                Text(if (enough) "충전 없이 갈 수 있어요" else "가는 길에 한 번 충전해요", color = if (enough) EvFitColors.Saving else EvFitColors.Warning, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** 순위 링 — 지도 표식과 같은 모양(1~3위), 4위부터는 숫자만 */
@Composable
private fun RankMark(rank: Int, selected: Boolean) {
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        if (rank <= 3) {
            Canvas(Modifier.size(40.dp)) {
                drawCircle(if (selected) EvFitColors.ActionBg else EvFitColors.Bg)
                drawCircle(EvFitColors.FitBlueFill, style = Stroke(3.dp.toPx()))
            }
            Text("$rank", style = gauge(22.sp, color = if (selected) Color.White else EvFitColors.TextPrimary))
        } else {
            Text("$rank", style = gauge(22.sp, color = EvFitColors.TextSecondary))
        }
    }
}

@Composable
private fun RankRow(s: UiState, index: Int, c: CorridorStation, selected: Boolean, onClick: () -> Unit) {
    val operator = s.data?.operatorName(c.station.bid) ?: ""
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) EvFitColors.CardAlt else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RankMark(index + 1, selected)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(c.station.name, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(operator, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text("${c.station.maxKw}kW", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                if (s.trip.line != null) Text("${c.alongKm.toInt()}km 지점", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (c.onRoute) "경로 위" else "우회 ${c.ranked.detourMin.toInt()}분",
                    color = if (c.onRoute) EvFitColors.Saving else EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (s.trip.mode) {
                RouteMode.FIT -> Gauge(one(c.ranked.fit), "FIT", 30.sp)
                RouteMode.ON_ROUTE -> Gauge(if (c.onRoute) "0" else "${c.ranked.detourMin.toInt()}", "분 우회", 30.sp)
                RouteMode.PRICE -> Gauge(num(c.costKrw), "원", 30.sp)
                RouteMode.FAST -> Gauge("${c.totalMin.toInt()}", "분 소요", 30.sp)
            }
            if (s.trip.mode != RouteMode.PRICE) Text(won(c.costKrw), color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SelectionFooter(s: UiState, vm: AppViewModel, c: CorridorStation) {
    val best = c.ranked.quote.best ?: return
    val saving = c.ranked.quote.savingVsMoe ?: 0
    val moeOnly = s.nextStop?.moeOnly?.firstOrNull()?.takeIf { s.nextStop?.atStop?.any { it.quote.station.id == c.station.id } == true }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EvFitColors.Bg).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${best.credential.label}로 결제", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${unitPrice(best.effectiveUnitPrice)}/kWh${best.appliedPlan?.let { "  ${it.name}" } ?: ""}",
                    color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Gauge(num(best.costKrw), "원", 40.sp)
                if (saving > 0) Text("환경부 카드보다 ${won(saving)} 적게", color = EvFitColors.Saving, style = MaterialTheme.typography.labelMedium)
            }
        }
        moeOnly?.let {
            Text(
                "같은 휴게소 ${s.data?.operatorName(it.quote.station.bid)} 충전기는 환경부 카드만 돼요",
                color = EvFitColors.Warning, style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvButton("충전 준비", { vm.goCharge(c.station) }, Modifier.weight(1.3f), enabled = !s.trip.driving)
            EvButton("자세히", { vm.openDetail(c.station.id) }, Modifier.weight(1f), secondary = true)
            if (s.trip.line != null) EvButton("경유지로", { vm.setAsWaypoint(c.station) }, Modifier.weight(1f), secondary = true)
        }
    }
}

/** 충전소 상세 — 지도는 그대로 두고 오른쪽 패널만 바꾼다 */
@Composable
private fun StationPanel(s: UiState, vm: AppViewModel, c: CorridorStation) {
    val station = c.station
    val q = vm.quoteFor(station) ?: c.ranked.quote
    val operator = s.data?.operatorName(station.bid) ?: ""
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        BackRow("목록") { vm.closeDetail() }
        Text(station.name, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Attr("사업자", operator)
            Attr("최대 출력", "${station.maxKw}kW")
            Attr(if (s.trip.line != null) "경로 위치" else "거리", if (s.trip.line != null) "${c.alongKm.toInt()}km 지점" else "${one(c.offsetKm)}km")
            Attr("우회", if (c.onRoute) "경로 위" else "${c.ranked.detourMin.toInt()}분(추정)", valueColor = if (c.onRoute) EvFitColors.Saving else EvFitColors.TextPrimary)
        }
        Text(station.addr, color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)

        Hairline()
        // FIT 점수와 근거
        c.ranked.fitParts?.let { parts ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Gauge(one(c.ranked.fit), "FIT", 56.sp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("요금" to parts.price, "시간" to parts.time, "신뢰도" to parts.trust).forEach { (k, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(k, Modifier.width(64.dp), color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                            ProgressBar((v / 100).toFloat(), Modifier.weight(1f), height = 8)
                            Text("${v.toInt()}", Modifier.width(40.dp), style = gauge(20.sp))
                        }
                    }
                }
            }
            Text("회랑 안 후보끼리 비교한 점수예요. 요금 50%, 시간 30%, 신뢰도 20%로 합쳐요.", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }

        Hairline()
        Text("결제 수단별 금액", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text("${one(q.kwh)}kWh 충전 기준", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        val maxCost = (q.options.map { it.costKrw } + listOfNotNull(q.moeReferenceKrw)).maxOrNull()?.coerceAtLeast(1) ?: 1
        q.options.forEachIndexed { i, o ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(o.credential.label, color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${unitPrice(o.effectiveUnitPrice)}/kWh${o.appliedPlan?.let { "  ${it.name}" } ?: ""}",
                            color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Gauge(num(o.costKrw), "원", 28.sp, color = if (i == 0) EvFitColors.TextPrimary else EvFitColors.TextSecondary)
                }
                ProgressBar(o.costKrw.toFloat() / maxCost, color = if (i == 0) EvFitColors.FitBlueFill else EvFitColors.TextSecondary.copy(alpha = 0.55f), height = 8)
                o.notes.forEach { Text(it, color = EvFitColors.Warning, style = MaterialTheme.typography.bodySmall) }
            }
        }
        q.unavailable.forEach { u ->
            Row(Modifier.fillMaxWidth()) {
                Text(u.credential.label, Modifier.weight(1f), color = EvFitColors.Disabled, style = MaterialTheme.typography.bodyMedium)
                Text(u.reason, color = EvFitColors.Disabled, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (q.options.none { it.credential is Credential.MoeCard }) {
            q.moeReferenceKrw?.let { Text("환경부 카드로 결제하면 ${won(it)}", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall) }
        }

        Hairline()
        val bench = s.data?.benchFor(s.wallet.vehicleModel)
        if (bench != null) {
            val cls = AppData.chargerClass(station.maxKw)
            Text("같은 차종의 이 충전소 경험", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Column { Text("충전 성공률", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium); Gauge(one(c.ranked.successPct), "%", 40.sp) }
                Column { Text("${cls}kW급 평균 속도", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium); Gauge("${(bench.avgKwByClass[cls.toString()] ?: 0.0).toInt()}", "kW", 40.sp) }
                Column { Text("만족도", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium); Gauge(one(bench.satisfaction), "/ 5", 40.sp) }
            }
            SyntheticLabel()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            EvButton("충전 준비", { vm.goCharge(station) }, Modifier.weight(1.3f), enabled = q.best != null && !s.trip.driving)
            if (s.trip.line != null) EvButton("경유지로", { vm.setAsWaypoint(station) }, Modifier.weight(1f), secondary = true)
            EvButton("목적지로", { vm.setDestination(station) }, Modifier.weight(1f), secondary = true)
        }
    }
}
