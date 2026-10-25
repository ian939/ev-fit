// 급속 충전기 실시간 상태 연동 — 실서비스는 환경부 충전기 상태 API(5분 주기 갱신), 시연은 충전소 id로 정해지는 합성 상태
package com.ian.evfit.cpo

import com.ian.evfit.engine.LiveStatus
import com.ian.evfit.engine.Station
import kotlin.random.Random

interface StatusGateway {
    /** 충전소별 급속 충전기 상태. 상태를 받지 못한 충전소는 결과에 없다 */
    suspend fun statusOf(stations: List<Station>): Map<String, LiveStatus>
}

/** 시연용 — overrides는 시연 시나리오가 정한 충전소 상태(망향휴게소 등), 나머지는 합성 */
class MockStatusGateway(private val overrides: Map<String, LiveStatus> = emptyMap()) : StatusGateway {
    override suspend fun statusOf(stations: List<Station>): Map<String, LiveStatus> =
        stations.associate { it.id to (overrides[it.id] ?: synthetic(it.id, it.fastCount)) }

    companion object {
        /** 충전기마다 id로 정해지는 상태: 점검·고장 7%, 상태 미확인 3%, 충전 중 32%, 충전 가능 58% */
        fun synthetic(id: String, fastCount: Int): LiveStatus {
            val total = fastCount.coerceAtLeast(1)
            var free = 0
            var busy = 0
            var down = 0
            val rnd = Random(id.hashCode())
            repeat(total) {
                when (rnd.nextInt(100)) {
                    in 0 until 7 -> down++
                    in 7 until 10 -> Unit
                    in 10 until 42 -> busy++
                    else -> free++
                }
            }
            return LiveStatus(total, free, busy, down, updatedMinAgo = 1 + rnd.nextInt(4))
        }
    }
}
