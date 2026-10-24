// 경로선(위경도 점 목록) 위의 누적 거리·투영·진행 방향 좌우를 계산하는 기하 유틸(지도 회랑 추천용)
package com.ian.evfit.engine

import kotlin.math.cos
import kotlin.math.sqrt

/** 경로 위 투영 결과 — alongKm: 출발점부터 경로를 따라 간 거리, offsetKm: 경로선에서 떨어진 거리, rightSide: 진행 방향 오른쪽 */
data class RouteProjection(val alongKm: Double, val offsetKm: Double, val rightSide: Boolean)

/**
 * 200km 안쪽 회랑이라 경로 평균 위도 기준 등장방형(km) 평면으로 계산한다.
 * x = 경도×111.320×cos(평균 위도), y = 위도×110.574 (WGS84 1도 길이 근사).
 */
class RouteLine(val points: List<LatLng>) {
    init {
        require(points.size >= 2) { "경로 점은 2개 이상이어야 한다" }
    }

    private val kx = 111.320 * cos(Math.toRadians(points.map { it.lat }.average()))
    private val ky = 110.574
    private val xs = DoubleArray(points.size) { points[it].lng * kx }
    private val ys = DoubleArray(points.size) { points[it].lat * ky }

    /** 각 점까지의 누적 거리(km) */
    val cumKm: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + seg(i - 1)
    }
    val lengthKm: Double get() = cumKm.last()

    private fun seg(i: Int): Double {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        return sqrt(dx * dx + dy * dy)
    }

    fun project(p: LatLng): RouteProjection {
        val px = p.lng * kx
        val py = p.lat * ky
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        var bestCross = 0.0
        for (i in 0 until points.size - 1) {
            val ax = xs[i]; val ay = ys[i]
            val dx = xs[i + 1] - ax; val dy = ys[i + 1] - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val qx = ax + t * dx; val qy = ay + t * dy
            val d2 = (px - qx) * (px - qx) + (py - qy) * (py - qy)
            if (d2 < best) {
                best = d2
                bestAlong = cumKm[i] + t * sqrt(len2)
                bestCross = dx * (py - ay) - dy * (px - ax)
            }
        }
        // 동(x)·북(y) 좌표에서 외적이 음수면 진행 방향의 오른쪽(시계 방향)
        return RouteProjection(bestAlong, sqrt(best), bestCross < 0)
    }

    /** 출발점부터 km만큼 간 지점 */
    fun pointAt(km: Double): LatLng {
        val k = km.coerceIn(0.0, lengthKm)
        val i = (1 until points.size).firstOrNull { cumKm[it] >= k } ?: (points.size - 1)
        val span = cumKm[i] - cumKm[i - 1]
        val t = if (span == 0.0) 0.0 else (k - cumKm[i - 1]) / span
        val a = points[i - 1]
        val b = points[i]
        return LatLng(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
    }

    /** fromKm~toKm 구간의 점 목록(양 끝 보간점 포함) — 지난 구간·남은 구간을 나눠 그릴 때 쓴다 */
    fun slice(fromKm: Double, toKm: Double): List<LatLng> {
        val a = fromKm.coerceIn(0.0, lengthKm)
        val b = toKm.coerceIn(a, lengthKm)
        val inner = points.indices.filter { cumKm[it] > a && cumKm[it] < b }.map { points[it] }
        return listOf(pointAt(a)) + inner + listOf(pointAt(b))
    }
}
