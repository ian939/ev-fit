// 시연용 합성 통계 — 충전소 id로 정해지는 동일 차량 성공률(실측 아님)
package com.ian.evfit.engine

object SyntheticStats {
    /** 92.0~99.5 사이 값. 같은 id면 항상 같은 값 */
    fun successPct(stationId: String): Double {
        val h = stationId.hashCode().toLong().let { if (it < 0) -it else it }
        return 92.0 + (h % 76) / 10.0
    }
}
