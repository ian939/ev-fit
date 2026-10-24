// 기준점 주변 급속 충전소를 찾아 최저가 순 또는 FIT 점수 순으로 줄 세우는 랭커(FIT Route)
package com.ian.evfit.engine

data class RankedStation(
    val quote: Quote, val distanceKm: Double,
    val detourMin: Double = 0.0, val chargeMin: Double = 0.0, val successPct: Double = 0.0, val fit: Double = 0.0,
    val fitParts: FitParts? = null,
)

object StationRanker {
    /** center 반경 radiusKm 안의 충전소를 가까운 순으로 최대 limit개 */
    fun nearby(stations: List<Station>, center: LatLng, radiusKm: Double, limit: Int): List<Pair<Station, Double>> =
        stations.asSequence()
            .map { it to Geo.distanceKm(center, it.pos) }
            .filter { it.second <= radiusKm }
            .sortedBy { it.second }
            .take(limit)
            .toList()

    /** 결제 가능한 선택지가 있는 충전소만, 최저 금액 → 거리 순 */
    fun rank(
        candidates: List<Pair<Station, Double>>, kwh: Double, wallet: Wallet, table: PricingTable,
        plans: Map<String, SubscriptionPlan>, operatorName: (String) -> String,
    ): List<RankedStation> =
        candidates.mapNotNull { (s, d) ->
            val q = PriceEngine.quote(s, kwh, wallet, table, plans, operatorName)
            if (q.best == null) null else RankedStation(q, d)
        }.sortedWith(compareBy<RankedStation> { it.quote.best!!.costKrw }.thenBy { it.distanceKm })

    /** FIT 추천 순 — rank 결과에 이동·충전 시간과 신뢰도를 붙여 FIT 점수 내림차순, 같으면 금액 오름차순 */
    fun rankFit(
        candidates: List<Pair<Station, Double>>, kwh: Double, wallet: Wallet, table: PricingTable,
        plans: Map<String, SubscriptionPlan>, operatorName: (String) -> String,
        chargeMinutes: (Station, Double) -> Double, successPct: (Station) -> Double,
    ): List<RankedStation> {
        val enriched = rank(candidates, kwh, wallet, table, plans, operatorName).map { r ->
            r.copy(
                detourMin = FitScore.detourMinutes(r.distanceKm),
                chargeMin = chargeMinutes(r.quote.station, kwh),
                successPct = successPct(r.quote.station),
            )
        }
        val fits = FitScore.scores(enriched.map { FitInput(it.quote.best!!.costKrw, it.detourMin, it.chargeMin, it.successPct) })
        return enriched.mapIndexed { i, r -> r.copy(fit = fits[i]) }
            .sortedWith(compareByDescending<RankedStation> { it.fit }.thenBy { it.quote.best!!.costKrw })
    }
}
