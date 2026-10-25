// 경로선 투영·회랑 후보(반대 방향 휴게소 제외)·도달 가능·탭별 순위 테스트 — 기대값은 손으로 계산한 값
package com.ian.evfit.engine

import com.ian.evfit.engine.TestFixtures.quote
import com.ian.evfit.engine.TestFixtures.station
import com.ian.evfit.engine.TestFixtures.wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorTest {
    // 북위 37도 → 36도로 곧장 남쪽으로 가는 경로(동경 127도)
    private val south = RouteLine(listOf(LatLng(37.0, 127.0), LatLng(36.0, 127.0)))
    // 위도 36.5도 경도 0.01도 = 111.320×cos(36.5°)×0.01 = 0.8949km
    private val kx = 111.320 * Math.cos(Math.toRadians(36.5))

    private fun at(id: String, lat: Double, lng: Double, highway: Boolean) =
        Station(id, id, "", lat, lng, "ST", 200, listOf(Stage.S5), highway)

    @Test
    fun 남쪽으로_가면_서쪽이_오른쪽이고_거리는_위도1도_110_574km() {
        assertEquals(110.574, south.lengthKm, 0.001)
        assertEquals(36.5, south.pointAt(55.287).lat, 1e-4)
        val east = south.project(LatLng(36.5, 127.01))
        assertEquals(55.287, east.alongKm, 0.01)
        assertEquals(0.01 * kx, east.offsetKm, 0.001)
        assertFalse(east.rightSide)
        assertTrue(south.project(LatLng(36.5, 126.99)).rightSide)
    }

    @Test
    fun 회랑_후보는_앞쪽_1km_안이고_반대방향_휴게소는_뺀다() {
        val stations = listOf(
            at("A", 36.5, 126.995, highway = true),  // 오른쪽 0.447km → 경로 위
            at("B", 36.5, 127.005, highway = true),  // 왼쪽 0.447km → 반대 방향 휴게소
            at("C", 36.6, 127.01, highway = false),  // 0.01도 = 0.8949km → 우회 후보
            at("D", 36.9, 127.0, highway = false),   // 출발 11km 지점 → 차(20km) 뒤
            at("E", 36.5, 127.012, highway = false), // 0.012도 = 1.0738km → 회랑(1km) 밖
        )
        val cands = CorridorRanker.candidates(stations, south, fromKm = 20.0)
        assertEquals(listOf("A", "C"), cands.map { it.station.id })
        assertTrue(cands[0].onRoute)
        assertFalse(cands[1].onRoute)
        // 우회 = 0.8949×2×1.2 + 나들목 5 = 7.148분
        val c = cands[1].projection.offsetKm
        assertEquals(0.01 * kx, c, 0.001)
        assertEquals(7.148, CorridorRanker.detourMinutes(c, onRoute = false, highwayStation = false), 0.001)
        assertEquals(c * 2.4 + 5.0, CorridorRanker.detourMinutes(c, onRoute = false, highwayStation = false), 1e-9)
        assertEquals(0.0, CorridorRanker.detourMinutes(0.4, onRoute = true, highwayStation = true), 1e-9)
    }

    @Test
    fun 도달_범위_밖은_FIT_점수에서_빼고_순위에도_넣지_않는다() {
        val near = CorridorRanker.Candidate(at("N", 36.0, 127.0, true), RouteProjection(120.0, 0.2, true), true)
        val far = CorridorRanker.Candidate(at("F", 36.0, 127.0, true), RouteProjection(140.0, 0.2, true), true)
        // 차 40km, 주행 가능 92.5km → 120km(80.2) 도달, 140km(100.2) 못 감
        val built = CorridorRanker.build(
            listOf(near, far), 48.488, wallet, TestFixtures.table, TestFixtures.plans, TestFixtures.operatorName,
            chargeMinutes = { _, _ -> 16.0 }, successPct = { 97.0 }, carKm = 40.0, rangeKm = 92.5,
        )
        assertTrue(built.first { it.station.id == "N" }.reachable)
        assertFalse(built.first { it.station.id == "F" }.reachable)
        assertEquals(100.0, built.first { it.station.id == "N" }.ranked.fit, 1e-9)
        assertEquals(0.0, built.first { it.station.id == "F" }.ranked.fit, 1e-9)
        assertEquals(listOf("N"), CorridorRanker.rank(RouteMode.PRICE, built).map { it.station.id })
    }

    @Test
    fun 탭마다_기준이_다르게_줄_세운다() {
        // SK 구독 마스터 295원: 48.488kWh = 14,304원, 40kWh = 11,800원, 33.9kWh = 10,000원
        fun item(id: String, kwh: Double, detour: Double, charge: Double, fit: Double, onRoute: Boolean, reachable: Boolean = true) =
            CorridorStation(
                RankedStation(quote(station("ST", Stage.S5, highway = onRoute, id = id), kwh), 0.0, detour, charge, 97.0, fit),
                alongKm = 70.0, offsetKm = 0.0, onRoute = onRoute, reachable = reachable,
            )
        val a = item("a", 48.488, detour = 0.0, charge = 16.0, fit = 70.0, onRoute = true)
        val b = item("b", 40.0, detour = 11.4, charge = 30.0, fit = 60.0, onRoute = false)
        val c = item("c", 48.488, detour = 3.0, charge = 8.0, fit = 85.0, onRoute = false)
        val d = item("d", 33.9, detour = 0.0, charge = 5.0, fit = 90.0, onRoute = true, reachable = false)
        assertEquals(14304L, a.costKrw)
        assertEquals(11800L, b.costKrw)
        val all = listOf(a, b, c, d)
        fun ids(m: RouteMode) = CorridorRanker.rank(m, all).map { it.station.id }
        assertEquals(listOf("c", "a", "b"), ids(RouteMode.FIT))
        assertEquals(listOf("a", "c", "b"), ids(RouteMode.ON_ROUTE))
        assertEquals(listOf("b", "a", "c"), ids(RouteMode.PRICE))
        assertEquals(listOf("c", "a", "b"), ids(RouteMode.FAST))
    }
}
