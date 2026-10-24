// EV FIT 추천 순위용 FIT 점수 — 요금·시간·신뢰도를 후보 안에서 0~100으로 정규화해 가중합(브랜드 약속 "The Right Charge for You")
package com.ian.evfit.engine

import kotlin.math.roundToLong

data class FitInput(val costKrw: Long, val detourMin: Double, val chargeMin: Double, val successPct: Double)

object FitScore {
    const val W_PRICE = 0.5
    const val W_TIME = 0.3
    const val W_TRUST = 0.2

    /** 추가 이동 시간 추정: 기준점까지 거리 × 2분(왕복, 평균 60km/h 가정) */
    fun detourMinutes(distanceKm: Double): Double = distanceKm * 2.0

    /** 후보들의 FIT 점수(0~100, 소수 1자리). 요금·시간은 낮을수록, 신뢰도는 높을수록 100에 가깝다 */
    fun scores(inputs: List<FitInput>): List<Double> {
        if (inputs.isEmpty()) return emptyList()
        fun norm(xs: List<Double>, lowerIsBetter: Boolean): List<Double> {
            val lo = xs.min()
            val hi = xs.max()
            return xs.map { x ->
                when {
                    hi == lo -> 100.0
                    lowerIsBetter -> 100.0 * (hi - x) / (hi - lo)
                    else -> 100.0 * (x - lo) / (hi - lo)
                }
            }
        }
        val p = norm(inputs.map { it.costKrw.toDouble() }, lowerIsBetter = true)
        val t = norm(inputs.map { it.detourMin + it.chargeMin }, lowerIsBetter = true)
        val r = norm(inputs.map { it.successPct }, lowerIsBetter = false)
        return inputs.indices.map { i -> ((W_PRICE * p[i] + W_TIME * t[i] + W_TRUST * r[i]) * 10).roundToLong() / 10.0 }
    }
}
