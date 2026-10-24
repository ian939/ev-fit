// 주행 가능 거리와 이동 후 SOC를 차종 전비로 어림하는 모델(지도 도달 범위·시연 이동용)
package com.ian.evfit.engine

import kotlin.math.roundToInt

object RangeModel {
    /** 도착 때 남겨 둘 예비 SOC(%) */
    const val RESERVE_PCT = 10
    /** 전비를 모를 때 쓰는 값(km/kWh) */
    const val DEFAULT_KM_PER_KWH = 5.0

    /** 예비 SOC를 남기고 갈 수 있는 거리(km) = (SOC−예비)% × 배터리 kWh × 전비 */
    fun rangeKm(soc: Int, batteryKwh: Double, kmPerKwh: Double): Double =
        ((soc - RESERVE_PCT).coerceAtLeast(0) / 100.0) * batteryKwh * kmPerKwh

    /** km를 달린 뒤 SOC(반올림) = SOC − km ÷ (전비 × 배터리 kWh) × 100 */
    fun socAfter(soc: Int, km: Double, batteryKwh: Double, kmPerKwh: Double): Int =
        (soc - km / (kmPerKwh * batteryKwh) * 100).roundToInt().coerceAtLeast(0)
}
