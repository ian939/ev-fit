// 거리 계산·충전 곡선·충전소 랭커(최저가·FIT) 테스트
package com.ian.evfit.engine

import com.ian.evfit.engine.TestFixtures.station
import com.ian.evfit.engine.TestFixtures.wallet
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoAndCurveTest {
    @Test
    fun 서울만남휴게소에서_망향휴게소까지_약_68km() {
        val d = Geo.distanceKm(LatLng(37.45958384, 127.04233054), LatLng(36.8541795, 127.1804275))
        assertEquals(68.42, d, 0.05)
    }

    @Test
    fun SOC_2퍼센트씩_1분이면_100kW() {
        val bands = ChargeCurve.bandSpeeds(
            listOf(SocSample(0, 40), SocSample(60_000, 42), SocSample(120_000, 44)), 83.6,
        )
        assertEquals(1, bands.size)
        assertEquals(40, bands[0].fromSoc)
        assertEquals(50, bands[0].toSoc)
        assertEquals(100.32, bands[0].avgKw, 0.01)
    }

    @Test
    fun 최저가_랭킹은_결제불가_충전소를_빼고_금액순() {
        val candidates = listOf(
            station("JA", Stage.S4) to 0.0,
            station("ST", Stage.S4) to 0.0,
            station("HD", Stage.S5, highway = true) to 0.0,
        )
        val ranked = StationRanker.rank(
            candidates, 40.0, wallet.copy(moeCard = false),
            TestFixtures.table, TestFixtures.plans, TestFixtures.operatorName,
        )
        assertEquals(listOf("ST", "JA"), ranked.map { it.quote.station.bid })
        assertEquals(11800L, ranked[0].quote.best!!.costKrw)
        assertEquals(11960L, ranked[1].quote.best!!.costKrw)
    }

    @Test
    fun FIT_랭킹은_점수순이고_이동시간을_계산한다() {
        val ranked = StationRanker.rankFit(
            listOf(station("ST", Stage.S4) to 0.0, station("JA", Stage.S4) to 5.0),
            40.0, wallet, TestFixtures.table, TestFixtures.plans, TestFixtures.operatorName,
            chargeMinutes = { _, _ -> 20.0 },
            successPct = { mapOf("ST" to 95.0, "JA" to 99.0).getValue(it.bid) },
        )
        assertEquals(listOf("ST", "JA"), ranked.map { it.quote.station.bid })
        assertEquals(80.0, ranked[0].fit, 1e-9)
        assertEquals(20.0, ranked[1].fit, 1e-9)
        assertEquals(10.0, ranked[1].detourMin, 1e-9)
    }
}
