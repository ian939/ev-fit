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
