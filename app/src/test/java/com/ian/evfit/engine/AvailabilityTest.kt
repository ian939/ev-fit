// '지금 충전 가능 N면 이상' 거르기와 시연용 합성 상태 테스트 — 기대값은 손으로 센 값
package com.ian.evfit.engine

import com.ian.evfit.cpo.MockStatusGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AvailabilityTest {
    private fun live(total: Int, free: Int, busy: Int = 0, down: Int = 0) = LiveStatus(total, free, busy, down, updatedMinAgo = 1)

    @Test
    fun 빈_충전기_수로_거르고_상태를_모르면_통과() {
        val oneFree = live(2, 1, busy = 1)
        assertTrue(AvailFilter.ALL.passes(live(1, 0, down = 1)))
        assertTrue(AvailFilter.ACTIVE.passes(oneFree))
        assertFalse(AvailFilter.TWO.passes(oneFree))
        assertTrue(AvailFilter.FOUR.passes(live(5, 4, busy = 1)))
        assertFalse(AvailFilter.FOUR.passes(live(5, 3, busy = 2)))
        assertTrue(AvailFilter.FOUR.passes(null))
        assertEquals(1, live(5, 2, busy = 1, down = 1).unknown)
        // 저장값이 없거나 모르는 이름이면 기본(작동 중만)
        assertEquals(AvailFilter.ACTIVE, AvailFilter.parse(null))
        assertEquals(AvailFilter.TWO, AvailFilter.parse("TWO"))
    }

    @Test
    fun 거르기는_순위에서_빼기만_하고_순서는_탭_기준을_따른다() {
        fun cs(id: String, fit: Double, l: LiveStatus) = CorridorStation(
            RankedStation(TestFixtures.quote(Station(id, id, "", 36.5, 127.0, "ST", 200, listOf(Stage.S5), true), 40.0), 0.2, fit = fit),
            alongKm = 70.0, offsetKm = 0.2, onRoute = true, reachable = true, live = l,
        )
        val items = listOf(cs("A", 80.0, live(1, 0, down = 1)), cs("B", 79.0, live(2, 1, busy = 1)), cs("C", 77.0, live(5, 4, busy = 1)))
        assertEquals(listOf("A", "B", "C"), CorridorRanker.rank(RouteMode.FIT, items).map { it.station.id })
        assertEquals(listOf("B", "C"), CorridorRanker.rank(RouteMode.FIT, items, AvailFilter.ACTIVE).map { it.station.id })
        assertEquals(listOf("C"), CorridorRanker.rank(RouteMode.FIT, items, AvailFilter.TWO).map { it.station.id })
        assertEquals(listOf("C"), CorridorRanker.rank(RouteMode.FIT, items, AvailFilter.FOUR).map { it.station.id })
    }

    @Test
    fun 합성_상태는_id마다_같고_합이_전체를_넘지_않는다() {
        val a = MockStatusGateway.synthetic("ST441324", 5)
        assertEquals(a, MockStatusGateway.synthetic("ST441324", 5))
        assertEquals(5, a.total)
        assertTrue(a.unknown >= 0)
        assertEquals(1, MockStatusGateway.synthetic("X", 0).total)
        // 충전기 2만 대에서 충전 가능 비율은 정한 58% 근처
        val many = (0 until 4000).map { MockStatusGateway.synthetic("S$it", 5) }
        val freeShare = many.sumOf { it.free } / many.sumOf { it.total }.toDouble()
        assertEquals(0.58, freeShare, 0.02)
    }
}
