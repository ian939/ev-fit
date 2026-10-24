// 시연 모드 시나리오 — 에뮬레이터에 없는 위치·경로·충전 중 SOC 흐름을 공급한다(서울→정부대전청사, 망향휴게소 정차)
package com.ian.evfit.demo

import com.ian.evfit.data.ModelBench
import com.ian.evfit.engine.ActiveSubscription
import com.ian.evfit.engine.LatLng
import com.ian.evfit.engine.SocSample
import com.ian.evfit.engine.Wallet
import com.ian.evfit.platform.NamedPoint
import com.ian.evfit.platform.PlannedStop
import com.ian.evfit.platform.RoutePlan
import kotlin.random.Random

object DemoDriver {
    const val VEHICLE_MODEL = "더 뉴 아이오닉5 2WD 롱레인지 19인치"
    const val BATTERY_KWH = 83.6
    const val START_SOC = 22
    /** 재생 압축 배율 — 실제 1분을 1초로 */
    const val PLAYBACK_SPEEDUP = 60

    val START_LOCATION = LatLng(37.4846, 127.0349)
    val DESTINATION = NamedPoint("정부대전청사", 36.3587, 127.3845, address = "대전광역시 서구 청사로 189")
    val STOP = PlannedStop("망향휴게소 (부산)", 36.8541795, 127.1804275, maxKw = 200, arriveSoc = START_SOC, targetSoc = 80)

    fun presetWallet(month: String) = Wallet(
        vehicleModel = VEHICLE_MODEL,
        batteryKwh = BATTERY_KWH,
        moeCard = true,
        memberships = emptySet(),
        subscriptions = listOf(
            ActiveSubscription("cv_passh_blue", 0.0, month),
            ActiveSubscription("st_sub_master", 0.0, month),
        ),
        targetSoc = 80,
    )

    fun route() = RoutePlan(DESTINATION, listOf(STOP))

    /**
     * 1% 단위 충전 샘플. 구간 중앙값 kW에 ±4% 흔들림을 주고, 1%에 걸리는 시간 = 0.01×용량 ÷ kW.
     * 시간은 실제(압축 전) 값이다.
     */
    fun chargingSamples(
        bench: ModelBench, chargerClass: Int, batteryKwh: Double, fromSoc: Int, toSoc: Int, seed: Long = 7L,
    ): List<SocSample> {
        val bands = bench.bands.getValue(chargerClass.toString())
        val rnd = Random(seed)
        var t = 0L
        val out = mutableListOf(SocSample(0, fromSoc))
        for (soc in fromSoc until toSoc) {
            val median = bands.first { soc >= it.from && soc < it.to }.median
            val kw = median * (1 + rnd.nextDouble(-0.04, 0.04))
            t += (0.01 * batteryKwh / kw * 3_600_000).toLong()
            out += SocSample(t, soc + 1)
        }
        return out
    }
}
