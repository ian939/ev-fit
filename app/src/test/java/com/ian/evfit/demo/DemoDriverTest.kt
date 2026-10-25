// 시연 충전 샘플이 합성 벤치 곡선을 따르는지 확인하는 테스트
package com.ian.evfit.demo

import com.ian.evfit.data.AssetParser
import com.ian.evfit.engine.ChargeCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DemoDriverTest {
    @Test
    fun 아이오닉5_22에서_80퍼센트_샘플() {
        val data = AssetParser.parse { File("src/main/assets/data/$it").readText(Charsets.UTF_8) }
        val bench = data.benchFor(DemoDriver.VEHICLE_MODEL)!!
        val samples = DemoDriver.chargingSamples(bench, 200, DemoDriver.BATTERY_KWH, 22, 80)

        assertEquals(59, samples.size)
        assertTrue(samples.zipWithNext().all { (a, b) -> b.soc == a.soc + 1 && b.tMillis > a.tMillis })

        val band20 = ChargeCurve.bandSpeeds(samples, DemoDriver.BATTERY_KWH).first { it.fromSoc == 20 }
        assertTrue("20~30% 구간 ${band20.avgKw}kW", band20.avgKw in 190.0 * 0.96..190.0 * 1.04)

        val minutes = samples.last().tMillis / 60_000.0
        assertTrue("총 ${minutes}분", minutes in 15.0..30.0)
    }
}

class DemoRouteTest {
    private val data = AssetParser.parse { File("src/main/assets/data/$it").readText(Charsets.UTF_8) }
    private val line = com.ian.evfit.engine.RouteLine(data.demoRoute)

    @Test
    fun 시연_경로에서_망향휴게소는_진행방향_경로_위이고_도착_SOC는_22() {
        val cands = com.ian.evfit.engine.CorridorRanker.candidates(data.stations, line, DemoDriver.CAR_KM)
        val st = cands.first { it.station.id == "ST441324" }
        assertTrue(st.onRoute)
        assertEquals(76.2, st.projection.alongKm, 0.3)
        // 같은 휴게소 SK시그넷(SG)도 경로 위, 반대 방향(서울) 휴게소는 없다
        assertTrue(cands.any { it.station.id == "SGMAC044" && it.onRoute })
        assertTrue(cands.none { it.station.highway && "(서울" in it.station.name && it.projection.offsetKm < 1.5 })
        val soc = com.ian.evfit.engine.RangeModel.socAfter(DemoDriver.CURRENT_SOC, st.projection.alongKm - DemoDriver.CAR_KM, DemoDriver.BATTERY_KWH, 5.53)
        assertEquals(DemoDriver.ARRIVE_SOC, soc)
    }

    @Test
    fun 시연_상태에서_작동_중만은_1위가_바뀌고_2면_이상이면_망향휴게소가_1위() {
        val bench = data.benchFor(DemoDriver.VEHICLE_MODEL)!!
        val kwh = com.ian.evfit.engine.PriceEngine.kwhNeeded(DemoDriver.BATTERY_KWH, DemoDriver.ARRIVE_SOC, 80)
        val cands = com.ian.evfit.engine.CorridorRanker.candidates(data.stations, line, DemoDriver.CAR_KM)
        val status = com.ian.evfit.cpo.MockStatusGateway(DemoDriver.LIVE_STATUS)
        val built = com.ian.evfit.engine.CorridorRanker.build(
            cands, kwh, DemoDriver.presetWallet("2026-10"), data.table, data.plans, data::operatorName,
            chargeMinutes = { s, k -> k / bench.avgKwByClass.getValue(com.ian.evfit.data.AppData.chargerClass(s.maxKw).toString()) * 60 },
            successPct = { com.ian.evfit.engine.SyntheticStats.successPct(it.id) },
            carKm = DemoDriver.CAR_KM, rangeKm = com.ian.evfit.engine.RangeModel.rangeKm(DemoDriver.CURRENT_SOC, DemoDriver.BATTERY_KWH, 5.53),
        ).let { b -> val live = kotlinx.coroutines.runBlocking { status.statusOf(b.map { it.station }) }; b.map { it.copy(live = live[it.station.id]) } }
        fun top(f: com.ian.evfit.engine.AvailFilter) =
            com.ian.evfit.engine.CorridorRanker.rank(com.ian.evfit.engine.RouteMode.FIT, built, f).first().station.id
        assertEquals("ST601458", top(com.ian.evfit.engine.AvailFilter.ALL))   // 안성TG셀프주유소 FIT 80.1, 점검 중
        assertEquals("CV000859", top(com.ian.evfit.engine.AvailFilter.ACTIVE)) // 청룡휴게주유소 78.8, 2면 중 1면
        assertEquals("ST441324", top(com.ian.evfit.engine.AvailFilter.TWO))   // 망향휴게소 77.4, 5면 중 4면
        assertEquals("ST441324", top(com.ian.evfit.engine.AvailFilter.FOUR))
    }

    @Test
    fun 주행_가능_거리는_예비_10퍼센트를_남긴다() {
        // (30−10)% × 83.6kWh × 5.53km/kWh = 92.46km, 예비 이하면 0
        assertEquals(92.4616, com.ian.evfit.engine.RangeModel.rangeKm(30, 83.6, 5.53), 1e-4)
        assertEquals(0.0, com.ian.evfit.engine.RangeModel.rangeKm(8, 83.6, 5.53), 1e-9)
        assertEquals(22, com.ian.evfit.engine.RangeModel.socAfter(30, 36.2, 83.6, 5.53))
    }
}
