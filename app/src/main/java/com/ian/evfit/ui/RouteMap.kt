// 경로 회랑 지도 — 시도 경계·해안선 위에 이번 경로를 빛나는 회랑으로 그리고, 회랑 안 충전소·추천 ①②③ 링·차 위치·도달 한계와 경로 자(km)를 보여 준다
package com.ian.evfit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.evfit.demo.DemoDriver
import com.ian.evfit.engine.CorridorRanker
import com.ian.evfit.engine.CorridorStation
import com.ian.evfit.engine.LatLng
import com.ian.evfit.engine.RouteLine
import com.ian.evfit.engine.RouteMode
import com.ian.evfit.ui.theme.EvFitColors
import com.ian.evfit.ui.theme.Wanted
import com.ian.evfit.ui.theme.gauge
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min

/** 위경도 → 화면 좌표. 평균 위도 기준 등장방형, 경계에 맞춰 가운데 정렬 */
private class MapFrame(minLat: Double, maxLat: Double, private val minLng: Double, maxLng: Double, w: Float, h: Float, pad: Float) {
    private val k = cos(Math.toRadians((minLat + maxLat) / 2))
    private val scale = min((w - 2 * pad) / ((maxLng - minLng) * k), (h - 2 * pad) / (maxLat - minLat))
    private val ox = (w - (maxLng - minLng) * k * scale) / 2
    private val oy = (h - (maxLat - minLat) * scale) / 2
    private val top = maxLat
    fun pt(p: LatLng) = Offset((ox + (p.lng - minLng) * k * scale).toFloat(), (oy + (top - p.lat) * scale).toFloat())
    /** 1km의 화면 길이 */
    val pxPerKm: Float = (scale / 110.574).toFloat()
}

private fun frameFor(line: RouteLine?, center: LatLng, around: List<LatLng>, w: Float, h: Float, pad: Float): MapFrame {
    if (line == null) {
        // 경로가 없으면 내 위치와 주변 후보가 다 들어오게(최소 ±0.015도)
        val pts = around + center
        val half = maxOf(0.015, pts.maxOf { kotlin.math.abs(it.lat - center.lat) }, pts.maxOf { kotlin.math.abs(it.lng - center.lng) } * 0.8) * 1.15
        return MapFrame(center.lat - half, center.lat + half, center.lng - half / 0.8, center.lng + half / 0.8, w, h, pad)
    }
    val m = 0.05
    return MapFrame(
        line.points.minOf { it.lat } - m, line.points.maxOf { it.lat } + m,
        line.points.minOf { it.lng } - m, line.points.maxOf { it.lng } + m, w, h, pad,
    )
}

/** 추천 ①②③ 표식 위치 — 같은 휴게소처럼 겹치면 오른쪽으로 비켜 놓는다 */
private fun pickPositions(frame: MapFrame, picks: List<CorridorStation>, step: Float): List<Offset> {
    val out = mutableListOf<Offset>()
    picks.forEach { p ->
        val base = frame.pt(p.station.pos)
        val crowd = out.count { (it - base).getDistance() < step * 1.6f }
        out += base + Offset(step * crowd, 0f)
    }
    return out
}

private fun DrawScope.polyline(pts: List<Offset>, color: Color, width: Float, effect: PathEffect? = null) {
    if (pts.size < 2) return
    val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect))
}

private fun DrawScope.label(m: TextMeasurer, text: String, at: Offset, style: TextStyle, center: Boolean = true, plate: Color? = null) {
    val lay = m.measure(text, style)
    val tl = if (center) at - Offset(lay.size.width / 2f, lay.size.height / 2f) else at - Offset(0f, lay.size.height / 2f)
    // 점·선 위에 놓이는 글자는 바탕판을 깔아 읽히게 한다
    plate?.let {
        val p = 6 * density
        drawRoundRect(it, tl - Offset(p, p / 2), Size(lay.size.width + 2 * p, lay.size.height + p), CornerRadius(8 * density))
    }
    drawText(lay, topLeft = tl)
}

@Composable
fun RouteMap(s: UiState, vm: AppViewModel, modifier: Modifier = Modifier) {
    val data = s.data ?: return
    val trip = s.trip
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current.density
    val picks = trip.ranked.take(3)
    val selected = trip.find(trip.selectedId)

    val cityStyle = TextStyle(fontFamily = Wanted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = EvFitColors.TextSecondary.copy(alpha = 0.55f))
    val placeStyle = TextStyle(fontFamily = Wanted, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EvFitColors.TextPrimary)
    val calloutStyle = TextStyle(fontFamily = Wanted, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = EvFitColors.TextPrimary)
    val reachStyle = TextStyle(fontFamily = Wanted, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EvFitColors.Warning)
    val rankStyle = gauge(22.sp, color = EvFitColors.TextPrimary)
    val rankOnStyle = gauge(22.sp, color = Color.White)

    BoxWithConstraints(modifier.clipToBounds().background(EvFitColors.Sea)) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val center = s.location ?: DemoDriver.START_LOCATION
        val frame = remember(trip.line, center, trip.all, w, h) { frameFor(trip.line, center, trip.all.map { it.station.pos }, w, h, 48 * d) }
        val step = 34 * d
        val pickPos = pickPositions(frame, picks, step)

        Canvas(
            Modifier.fillMaxSize().pointerInput(frame, trip.all, trip.ranked) {
                detectTapGestures { tap ->
                    val hitPick = pickPos.indices.minByOrNull { (pickPos[it] - tap).getDistance() }
                        ?.takeIf { (pickPos[it] - tap).getDistance() < 36 * d }
                    if (hitPick != null) {
                        vm.select(picks[hitPick].station.id)
                        return@detectTapGestures
                    }
                    trip.ranked
                        .minByOrNull { (frame.pt(it.station.pos) - tap).getDistance() }
                        ?.takeIf { (frame.pt(it.station.pos) - tap).getDistance() < 26 * d }
                        ?.let { vm.select(it.station.id) }
                }
            },
        ) {
            // 바탕: 육지·시도 경계·도시 이름
            data.basemap.land.forEach { ring ->
                val p = Path().apply {
                    ring.forEachIndexed { i, ll -> val o = frame.pt(ll); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
                    close()
                }
                drawPath(p, EvFitColors.Land)
            }
            data.basemap.borders.forEach { polyline(it.map(frame::pt), EvFitColors.Hairline, 1.5f * d) }
            data.basemap.labels.forEach { label(measurer, it.name, frame.pt(it.pos), cityStyle) }

            val line = trip.line
            if (line != null) {
                val car = trip.carKm
                val reachEnd = (car + trip.rangeKm).coerceAtMost(line.lengthKm)
                val ahead = line.slice(car, line.lengthKm).map(frame::pt)
                val inReach = line.slice(car, reachEnd).map(frame::pt)
                // 회랑: 경로 양옆 1km 띠 — 도달 범위 안은 밝게
                polyline(ahead, EvFitColors.FitBlueFill.copy(alpha = 0.06f), frame.pxPerKm * CorridorRanker.MAX_OFFSET_KM.toFloat() * 2)
                polyline(inReach, EvFitColors.FitBlueFill.copy(alpha = 0.10f), frame.pxPerKm * CorridorRanker.MAX_OFFSET_KM.toFloat() * 2)
                // 경로선: 지난 구간 · 도달 범위 · 그 너머(점선)
                polyline(line.slice(0.0, car).map(frame::pt), EvFitColors.TextPrimary.copy(alpha = 0.28f), 5 * d)
                polyline(line.slice(reachEnd, line.lengthKm).map(frame::pt), EvFitColors.FitBlueFill.copy(alpha = 0.45f), 5 * d,
                    PathEffect.dashPathEffect(floatArrayOf(10 * d, 9 * d)))
                polyline(inReach, EvFitColors.Bg, 13 * d)
                polyline(inReach, EvFitColors.FitBlueFill, 8 * d)
                polyline(inReach, Color.White.copy(alpha = 0.85f), 2 * d)

                // 출발·목적지
                val start = frame.pt(line.points.first())
                drawCircle(EvFitColors.Bg, 9 * d, start)
                drawCircle(EvFitColors.TextSecondary, 9 * d, start, style = Stroke(3 * d))
                val end = frame.pt(line.points.last())
                drawCircle(EvFitColors.TextPrimary, 16 * d, end, style = Stroke(3 * d))
                drawCircle(EvFitColors.TextPrimary, 7 * d, end)
                s.route?.destination?.name?.let { label(measurer, it, end + Offset(26 * d, 0f), placeStyle, center = false, plate = EvFitColors.Bg.copy(alpha = 0.85f)) }

                // 도달 한계 표시(경로에 수직인 짧은 선)
                if (car + trip.rangeKm < line.lengthKm) {
                    val a = frame.pt(line.pointAt(reachEnd - 0.5))
                    val b = frame.pt(line.pointAt(reachEnd + 0.5))
                    val dir = (b - a).let { it / it.getDistance().coerceAtLeast(0.001f) }
                    val n = Offset(-dir.y, dir.x)
                    val c = frame.pt(line.pointAt(reachEnd))
                    drawLine(EvFitColors.Warning, c - n * (16 * d), c + n * (16 * d), strokeWidth = 4 * d, cap = StrokeCap.Round)
                    label(measurer, "주행 가능 끝", c + n * (24 * d), reachStyle, center = false, plate = EvFitColors.Bg.copy(alpha = 0.85f))
                }
            }

            // 회랑 안 충전소 점 — 목록에 오른 곳(도달 가능 + 거르기 통과)만 진하게
            val shown = trip.ranked.mapTo(HashSet()) { it.station.id }
            trip.all.forEach { c ->
                val o = frame.pt(c.station.pos)
                if (c.station.id in shown) drawCircle(EvFitColors.TextPrimary.copy(alpha = if (c.onRoute) 0.75f else 0.32f), (if (c.onRoute) 4.5f else 3f) * d, o)
                else drawCircle(EvFitColors.TextSecondary.copy(alpha = 0.22f), 1.8f * d, o)
            }

            // 차 위치(진행 방향 화살표)
            if (line != null) {
                val o = frame.pt(line.pointAt(trip.carKm))
                val ahead = frame.pt(line.pointAt(trip.carKm + 1.0))
                val deg = Math.toDegrees(atan2((ahead.y - o.y).toDouble(), (ahead.x - o.x).toDouble())).toFloat()
                drawCircle(EvFitColors.FitBlueFill.copy(alpha = 0.25f), 26 * d, o)
                drawCircle(EvFitColors.Bg, 15 * d, o)
                rotate(deg, o) {
                    val p = Path().apply {
                        moveTo(o.x + 12 * d, o.y); lineTo(o.x - 8 * d, o.y - 9 * d); lineTo(o.x - 4 * d, o.y); lineTo(o.x - 8 * d, o.y + 9 * d); close()
                    }
                    drawPath(p, EvFitColors.TextPrimary)
                }
            } else {
                val o = frame.pt(center)
                drawCircle(EvFitColors.FitBlueFill.copy(alpha = 0.25f), 26 * d, o)
                drawCircle(EvFitColors.TextPrimary, 9 * d, o)
            }

            // 추천 ①②③ — 동심원 링(브랜드 호). 고른 곳은 바깥 링을 하나 더
            picks.forEachIndexed { i, p ->
                val o = pickPos[i]
                val on = p.station.id == trip.selectedId
                if (on) drawCircle(EvFitColors.TextPrimary, 30 * d, o, style = Stroke(2.5f * d))
                drawCircle(EvFitColors.Bg, 22 * d, o)
                drawCircle(if (on) EvFitColors.ActionBg else EvFitColors.Bg, 19 * d, o)
                drawCircle(EvFitColors.FitBlueFill, 19 * d, o, style = Stroke(3.5f * d))
                label(measurer, "${i + 1}", o, if (on) rankOnStyle else rankStyle)
            }

            // 고른 충전소 말풍선 — 이름과 탭 기준 값
            selected?.let { sel ->
                val i = picks.indexOfFirst { it.station.id == sel.station.id }
                val o = if (i >= 0) pickPos[i] else frame.pt(sel.station.pos).also {
                    drawCircle(EvFitColors.TextPrimary, 9 * d, it); drawCircle(EvFitColors.ActionBg, 6 * d, it)
                }
                val text = "${sel.station.name}   ${metricText(trip.mode, sel)}"
                val lay = measurer.measure(text, calloutStyle)
                val padX = 16 * d
                val bw = lay.size.width + padX * 2
                val bh = lay.size.height + 18 * d
                val right = o.x + 40 * d + bw < size.width - 8 * d
                val tl = Offset(if (right) o.x + 38 * d else o.x - 38 * d - bw, o.y - bh / 2)
                drawRoundRect(EvFitColors.Panel, tl, Size(bw, bh), CornerRadius(12 * d))
                drawRoundRect(EvFitColors.FitBlueFill, tl, Size(bw, bh), CornerRadius(12 * d), style = Stroke(2 * d))
                drawText(lay, topLeft = tl + Offset(padX, 9 * d))
            }
        }

        // 왼쪽 위: 남은 거리
        // 왼쪽 글자는 상단 줄 워드마크와 같은 32dp 선에, 위는 오른쪽 패널 첫 줄과 같은 24dp에 맞춘다
        Column(Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val line = trip.line
            if (line != null) {
                Text("${s.route?.destination?.name ?: "목적지"}까지", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Gauge("${(line.lengthKm - trip.carKm).toInt()}", "km", 40.sp)
            } else {
                Text("내 주변 충전소", color = EvFitColors.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text("내비에서 목적지를 정하면 경로 안에서 골라 드려요", color = EvFitColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        // 오른쪽 위: 시연 조작
        if (s.demoMode && !trip.driving) {
            Text(
                if (s.route == null) "시연 시작" else "처음부터",
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 24.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (s.route == null) EvFitColors.ActionBg else EvFitColors.CardAlt)
                    .clickable { vm.startDemo() }.padding(horizontal = 22.dp, vertical = 16.dp),
                color = if (s.route == null) Color.White else EvFitColors.TextPrimary, style = MaterialTheme.typography.labelLarge,
            )
        }
        // 오른쪽 아래: 출처
        Text(
            listOf(trip.routeSource, "지도 Natural Earth").filter { it.isNotBlank() }.joinToString("   "),
            modifier = Modifier.align(Alignment.BottomEnd).padding(horizontal = 16.dp, vertical = 10.dp),
            color = EvFitColors.TextSecondary.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** 탭 기준에 맞춰 보여 줄 값 */
fun metricText(mode: RouteMode, c: CorridorStation): String = when (mode) {
    RouteMode.FIT -> "FIT ${one(c.ranked.fit)}"
    RouteMode.ON_ROUTE -> if (c.ranked.detourMin < 0.5) "우회 없음" else "우회 ${c.ranked.detourMin.toInt()}분"
    RouteMode.PRICE -> won(c.costKrw)
    RouteMode.FAST -> "${c.totalMin.toInt()}분"
}

/** 경로 자 — 출발 0km부터 목적지까지 직선 위에 차·도달 한계·회랑 충전소·추천 ①②③을 km로 찍는다 */
@Composable
fun CorridorRuler(s: UiState, modifier: Modifier = Modifier) {
    val trip = s.trip
    val line = trip.line ?: return
    val measurer = rememberTextMeasurer()
    val d = LocalDensity.current.density
    val picks = trip.ranked.take(3)
    val axis = TextStyle(fontFamily = Wanted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = EvFitColors.TextSecondary)
    val reachStyle = TextStyle(fontFamily = Wanted, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EvFitColors.Warning)
    val rankStyle = gauge(18.sp, color = EvFitColors.TextPrimary)
    val rankOnStyle = gauge(18.sp, color = Color.White)
    Canvas(modifier.background(EvFitColors.Bg)) {
        val left = 40 * d
        val right = size.width - 40 * d
        val y = size.height * 0.52f
        fun x(km: Double) = (left + (right - left) * (km / line.lengthKm)).toFloat()
        val reachEnd = (trip.carKm + trip.rangeKm).coerceAtMost(line.lengthKm)

        drawLine(EvFitColors.Hairline, Offset(left, y), Offset(right, y), 6 * d, StrokeCap.Round)
        drawLine(EvFitColors.TextPrimary.copy(alpha = 0.28f), Offset(left, y), Offset(x(trip.carKm), y), 6 * d, StrokeCap.Round)
        drawLine(EvFitColors.FitBlueFill, Offset(x(trip.carKm), y), Offset(x(reachEnd), y), 6 * d, StrokeCap.Round)
        // 회랑 충전소 눈금 — 경로 위(휴게소)는 길게, 목록에 오른 곳만 진하게
        val shown = trip.ranked.mapTo(HashSet()) { it.station.id }
        trip.all.forEach { c ->
            val h = if (c.onRoute) 14 * d else 7 * d
            drawLine(
                EvFitColors.TextPrimary.copy(alpha = if (c.station.id in shown) 0.45f else 0.15f),
                Offset(x(c.alongKm), y + 6 * d), Offset(x(c.alongKm), y + 6 * d + h), 1.5f * d,
            )
        }
        // 도달 한계 — 글자는 막대 아래(오른쪽 끝에 가까우면 눈금 왼쪽), 겹치는 km 눈금 글자는 생략
        var reachSpan = 0f..0f
        if (trip.carKm + trip.rangeKm < line.lengthKm) {
            val rx = x(reachEnd)
            drawLine(EvFitColors.Warning, Offset(rx, y - 16 * d), Offset(rx, y + 16 * d), 3 * d, StrokeCap.Round)
            val lay = measurer.measure("주행 가능 끝 ${reachEnd.toInt()}km", reachStyle)
            val lx = if (rx + 8 * d + lay.size.width > right - 70 * d) rx - 8 * d - lay.size.width else rx + 8 * d
            drawText(lay, topLeft = Offset(lx, y + 38 * d - lay.size.height / 2f))
            reachSpan = (lx - 24 * d)..(lx + lay.size.width + 24 * d)
        }
        // 차
        val cx = x(trip.carKm)
        val car = Path().apply { moveTo(cx, y + 10 * d); lineTo(cx - 9 * d, y + 26 * d); lineTo(cx + 9 * d, y + 26 * d); close() }
        drawPath(car, EvFitColors.TextPrimary)
        // 추천 ①②③(위) — km 순으로 놓되 서로 가까우면 오른쪽으로 비켜 놓는다
        val placed = mutableMapOf<Int, Float>()
        var lastX = -1000f
        picks.indices.sortedBy { picks[it].alongKm }.forEach { i ->
            val px = maxOf(x(picks[i].alongKm), lastX + 32 * d)
            placed[i] = px
            lastX = px
        }
        picks.forEachIndexed { i, p ->
            val o = Offset(placed.getValue(i), y - 30 * d)
            val on = p.station.id == trip.selectedId
            drawLine(EvFitColors.FitBlueFill, Offset(x(p.alongKm), y), o, 2 * d)
            drawCircle(if (on) EvFitColors.ActionBg else EvFitColors.Bg, 15 * d, o)
            drawCircle(EvFitColors.FitBlueFill, 15 * d, o, style = Stroke(3 * d))
            label(measurer, "${i + 1}", o, if (on) rankOnStyle else rankStyle)
        }
        // 눈금 글자
        label(measurer, "0", Offset(left, y + 38 * d), axis)
        var km = 50
        while (km < line.lengthKm - 15) {
            if (x(km.toDouble()) !in reachSpan) label(measurer, "$km", Offset(x(km.toDouble()), y + 38 * d), axis)
            km += 50
        }
        label(measurer, "${line.lengthKm.toInt()}km", Offset(right, y + 38 * d), axis)
    }
}
