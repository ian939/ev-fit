// 경로 회랑(경로선 옆 5km) 안의 충전소를 도달 가능 여부와 함께 모으고, 탭 기준(FIT·경로 우선·최저가·빠른 충전)으로 줄 세우는 랭커
package com.ian.evfit.engine

enum class RouteMode(val label: String) {
    FIT("FIT 추천"), ON_ROUTE("경로 우선"), PRICE("최저가"), FAST("빠른 충전"),
}

data class CorridorStation(
    /** 요금·우회·충전 시간·신뢰도·FIT 점수(distanceKm = 경로선에서 떨어진 거리) */
    val ranked: RankedStation,
    val alongKm: Double,
    val offsetKm: Double,
    /** 진행 방향 고속도로 휴게소처럼 경로를 벗어나지 않고 들를 수 있는 곳 */
    val onRoute: Boolean,
    val reachable: Boolean,
) {
    val station: Station get() = ranked.quote.station
    val costKrw: Long get() = ranked.quote.best!!.costKrw
    val totalMin: Double get() = ranked.detourMin + ranked.chargeMin
}

object CorridorRanker {
    const val MAX_OFFSET_KM = 5.0
    const val ON_ROUTE_OFFSET_KM = 0.6
    /** 반대 방향 휴게소로 보는 거리 — 이 안쪽 고속도로 충전소가 진행 방향 왼쪽이면 들를 수 없다 */
    const val OPPOSITE_SIDE_KM = 1.5
    /** 우회 이동 1km에 드는 분(평균 50km/h 가정) */
    const val MIN_PER_KM = 1.2
    /** 고속도로를 나갔다 들어오는 나들목 시간(추정) */
    const val EXIT_PENALTY_MIN = 5.0

    data class Candidate(val station: Station, val projection: RouteProjection, val onRoute: Boolean)

    /** 경로를 벗어났다 돌아오는 시간(추정) = 떨어진 거리×2(왕복)×1.2분 + 고속도로 경로에서 일반 충전소면 나들목 5분 */
    fun detourMinutes(offsetKm: Double, onRoute: Boolean, highwayStation: Boolean, exitPenalty: Boolean = true): Double =
        if (onRoute) 0.0 else offsetKm * 2 * MIN_PER_KM + (if (highwayStation || !exitPenalty) 0.0 else EXIT_PENALTY_MIN)

    /** 차 위치(fromKm) 앞쪽 회랑 안 충전소. 반대 방향 휴게소는 뺀다 */
    fun candidates(stations: List<Station>, line: RouteLine, fromKm: Double, maxOffsetKm: Double = MAX_OFFSET_KM): List<Candidate> {
        val pad = maxOffsetKm / 88.0 // 위경도 1도 ≥ 88km(위도 37도 경도 방향) — 넉넉한 사각형 사전 거르기
        val minLat = line.points.minOf { it.lat } - pad; val maxLat = line.points.maxOf { it.lat } + pad
        val minLng = line.points.minOf { it.lng } - pad; val maxLng = line.points.maxOf { it.lng } + pad
        return stations.asSequence()
            .filter { it.lat in minLat..maxLat && it.lng in minLng..maxLng }
            .mapNotNull { s ->
                val p = line.project(s.pos)
                when {
                    p.offsetKm > maxOffsetKm || p.alongKm < fromKm - 0.5 -> null
                    s.highway && p.offsetKm < OPPOSITE_SIDE_KM && !p.rightSide -> null
                    else -> Candidate(s, p, onRoute = s.highway && p.offsetKm <= ON_ROUTE_OFFSET_KM)
                }
            }
            .toList()
    }

    /**
     * 후보마다 같은 충전량(kwh) 기준 요금을 매기고, 도달 가능한 곳끼리 FIT 점수를 낸다.
     * 도달 가능 = 차 위치부터 경로 거리 + 떨어진 거리 ≤ rangeKm.
     */
    fun build(
        cands: List<Candidate>, kwh: Double, wallet: Wallet, table: PricingTable,
        plans: Map<String, SubscriptionPlan>, operatorName: (String) -> String,
        chargeMinutes: (Station, Double) -> Double, successPct: (Station) -> Double,
        carKm: Double, rangeKm: Double, exitPenalty: Boolean = true,
    ): List<CorridorStation> {
        val priced = cands.mapNotNull { c ->
            val q = PriceEngine.quote(c.station, kwh, wallet, table, plans, operatorName)
            if (q.best == null) return@mapNotNull null
            val reachable = (c.projection.alongKm - carKm).coerceAtLeast(0.0) + c.projection.offsetKm <= rangeKm
            CorridorStation(
                RankedStation(
                    q, c.projection.offsetKm,
                    detourMin = detourMinutes(c.projection.offsetKm, c.onRoute, c.station.highway, exitPenalty),
                    chargeMin = chargeMinutes(c.station, kwh),
                    successPct = successPct(c.station),
                ),
                c.projection.alongKm, c.projection.offsetKm, c.onRoute, reachable,
            )
        }
        val reach = priced.filter { it.reachable }
        val parts = FitScore.parts(reach.map { FitInput(it.costKrw, it.ranked.detourMin, it.ranked.chargeMin, it.ranked.successPct) })
        val partsById = reach.mapIndexed { i, c -> c.station.id to parts[i] }.toMap()
        return priced.map { c ->
            partsById[c.station.id]?.let { c.copy(ranked = c.ranked.copy(fit = it.total, fitParts = it)) } ?: c
        }
    }

    /** 도달 가능한 곳만 탭 기준으로 줄 세운다 */
    fun rank(mode: RouteMode, items: List<CorridorStation>): List<CorridorStation> {
        val reach = items.filter { it.reachable }
        return when (mode) {
            RouteMode.FIT -> reach.sortedWith(compareByDescending<CorridorStation> { it.ranked.fit }.thenBy { it.costKrw })
            RouteMode.ON_ROUTE -> reach.sortedWith(compareBy<CorridorStation> { it.ranked.detourMin }.thenBy { it.costKrw }.thenBy { it.alongKm })
            RouteMode.PRICE -> reach.sortedWith(compareBy<CorridorStation> { it.costKrw }.thenBy { it.ranked.detourMin })
            RouteMode.FAST -> reach.sortedWith(compareBy<CorridorStation> { it.totalMin }.thenBy { it.costKrw })
        }
    }
}
