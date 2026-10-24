// 충전 중 SOC 샘플로 SOC 구간별 평균 충전 속도(kW)를 계산하는 유틸(FIT Insight)
package com.ian.evfit.engine

import kotlinx.serialization.Serializable

@Serializable
data class SocSample(val tMillis: Long, val soc: Int)

data class BandSpeed(val fromSoc: Int, val toSoc: Int, val avgKw: Double)

object ChargeCurve {
    /**
     * 연속 샘플 쌍 (a, b)에서 SOC와 시간이 모두 늘어난 것만 쓴다.
     * 쌍은 a.soc가 속한 구간에 넣고, 구간별 에너지 Σ(ΔSOC/100×용량) ÷ 시간 Σ(Δt) = 평균 kW.
     */
    fun bandSpeeds(samples: List<SocSample>, batteryKwh: Double, bandSize: Int = 10): List<BandSpeed> {
        val energy = sortedMapOf<Int, Double>()
        val hours = sortedMapOf<Int, Double>()
        samples.sortedBy { it.tMillis }.zipWithNext().forEach { (a, b) ->
            if (b.soc > a.soc && b.tMillis > a.tMillis) {
                val band = a.soc / bandSize * bandSize
                energy[band] = (energy[band] ?: 0.0) + (b.soc - a.soc) / 100.0 * batteryKwh
                hours[band] = (hours[band] ?: 0.0) + (b.tMillis - a.tMillis) / 3_600_000.0
            }
        }
        return energy.map { (band, e) -> BandSpeed(band, band + bandSize, e / hours.getValue(band)) }
    }
}
