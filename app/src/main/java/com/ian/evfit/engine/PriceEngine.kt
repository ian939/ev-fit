// 충전소 하나에 대해 지갑의 결제 수단별 예상 금액을 계산하고 싼 순으로 정렬하는 요금 엔진(FIT Price)
package com.ian.evfit.engine

object PriceEngine {
    const val MOE_BID = "ME"

    /** 예상 충전량(kWh) = (목표 SOC − 현재 SOC)/100 × 배터리 용량. 충전 손실은 무시한 근사치 */
    fun kwhNeeded(batteryKwh: Double, fromSoc: Int, toSoc: Int): Double =
        (toSoc - fromSoc).coerceAtLeast(0) * batteryKwh / 100.0

    fun quote(
        station: Station, kwh: Double, wallet: Wallet, table: PricingTable,
        plans: Map<String, SubscriptionPlan>, operatorName: (String) -> String,
    ): Quote {
        val stage = station.topStage
        val options = mutableListOf<PayOption>()
        val unavailable = mutableListOf<Unavailable>()

        if (wallet.moeCard) {
            val p = table.priceFor(MOE_BID, station.bid, stage)
            if (p != null) options += PayOption(Credential.MoeCard, listOf(CostPart(kwh, p, "환경부 카드")))
            else unavailable += Unavailable(Credential.MoeCard, "환경부 카드 로밍 미지원 충전소")
        }

        // 구독은 그 사업자 계정에 붙는다 — 구독이 있으면 계정도 있는 것으로 본다
        val accountBids = (wallet.memberships + wallet.subscriptions.mapNotNull { plans[it.planId]?.providerBid })
            .filter { it != MOE_BID }.toSortedSet()
        for (bid in accountBids) {
            val name = operatorName(bid)
            val account = Credential.Account(bid, name)
            val own = bid == station.bid
            val base = table.priceFor(bid, station.bid, stage)
            val baseDesc = if (own) "회원가" else "로밍가"
            val notes = mutableListOf<String>()
            var chosen: Pair<SubscriptionPlan, Double>? = null
            for (sub in wallet.subscriptions) {
                val plan = plans[sub.planId]?.takeIf { it.providerBid == bid } ?: continue
                val remaining = plan.monthlyCapKwh?.let { (it - sub.usedKwhThisMonth).coerceAtLeast(0.0) }
                    ?: Double.POSITIVE_INFINITY
                val inScope = own || (plan.scope == PlanScope.OWN_AND_ROAMING_FAST && base != null)
                val reason = when {
                    !inScope && plan.scope == PlanScope.OWN_ONLY -> "${plan.name}: $name 충전소 전용"
                    !inScope -> "${plan.name}: 로밍 협약 밖 충전소"
                    station.highway && !plan.highwayAllowed -> "${plan.name}: 고속도로 휴게소 미적용"
                    remaining <= 0.0 -> "${plan.name}: 이번 달 약정량 소진"
                    else -> null
                }
                if (reason != null) {
                    notes += reason
                    continue
                }
                if (chosen == null || plan.fastPriceKrwKwh < chosen.first.fastPriceKrwKwh) chosen = plan to remaining
            }
            if (chosen != null) {
                val (plan, remaining) = chosen
                val planKwh = minOf(kwh, remaining)
                val rest = kwh - planKwh
                val parts = mutableListOf(CostPart(planKwh, plan.fastPriceKrwKwh, "${plan.name} 약정"))
                if (rest > 1e-9) {
                    if (base == null) {
                        unavailable += Unavailable(account, "${plan.name}: 약정 초과분 요금 정보 없음")
                        continue
                    }
                    parts += CostPart(rest, base, "약정 초과분 $baseDesc")
                }
                options += PayOption(account, parts, plan, notes)
            } else if (base != null) {
                options += PayOption(account, listOf(CostPart(kwh, base, baseDesc)), null, notes)
            } else {
                unavailable += Unavailable(account, "$name 카드로 이 충전기 결제 불가(로밍 없음)")
            }
        }

        options.sortWith(compareBy<PayOption> { it.costKrw }.thenBy { if (it.credential is Credential.MoeCard) 1 else 0 })
        val moeRef = table.priceFor(MOE_BID, station.bid, stage)?.let { Math.round(kwh * it) }
        return Quote(station, stage, kwh, options, unavailable, moeRef)
    }
}
