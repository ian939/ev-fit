// 충전소의 급속 충전기 실시간 상태 집계와 '지금 충전 가능 N면 이상' 거르기
package com.ian.evfit.engine

/**
 * 급속 충전기 상태 집계. 환경부 충전기 상태 코드 기준으로 2 충전대기 = free, 3 충전중 = busy,
 * 1·4·5 통신이상·운영중지·점검중 = down, 9 상태미확인 = 나머지
 */
data class LiveStatus(val total: Int, val free: Int, val busy: Int, val down: Int, val updatedMinAgo: Int) {
    val unknown: Int get() = total - free - busy - down
}

/** 목록·지도 거르기 — 지금 충전 가능한 급속 충전기 수 하한 */
enum class AvailFilter(val label: String, val minFree: Int) {
    ALL("전체", 0), ACTIVE("작동 중만", 1), TWO("2면 이상", 2), FOUR("4면 이상", 4);

    /** 상태를 받지 못한 충전소(null)는 거르지 않는다 */
    fun passes(live: LiveStatus?): Boolean = live == null || live.free >= minFree

    companion object {
        fun parse(name: String?): AvailFilter = entries.firstOrNull { it.name == name } ?: ACTIVE
    }
}
