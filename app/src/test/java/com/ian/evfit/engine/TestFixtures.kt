// 엔진 테스트 공통 픽스처 — 환경부 공시(스냅샷 41, 2026-10-24) 단가 일부와 구독 3종·기본 지갑
package com.ian.evfit.engine

object TestFixtures {
    val table = PricingTable(
        self = mapOf(
            "JA|s4" to 380.0, "CV|s4" to 430.0, "CV|s5" to 430.0, "ST|s4" to 391.0, "ST|s5" to 391.0,
            "EV|s4" to 296.0, "SG|s5" to 347.0, "ME|s4" to 348.4, "ME|s5" to 393.1,
        ),
        roaming = mapOf(
            "ME|JA|s4" to 348.4, "ME|CV|s4" to 348.4, "ME|CV|s5" to 393.1, "ME|ST|s4" to 348.4, "ME|ST|s5" to 393.1,
            "ME|EV|s4" to 348.4, "ME|HD|s5" to 393.1, "ME|SG|s5" to 393.1,
            "CV|JA|s4" to 490.0, "CV|ST|s4" to 440.0, "CV|ST|s5" to 440.0, "CV|EV|s4" to 440.0,
            "ST|JA|s4" to 450.0, "ST|CV|s4" to 485.0, "ST|EV|s4" to 465.0,
        ),
    )

    val blue = SubscriptionPlan(
        "cv_passh_blue", "채비패스 H 블루", "현대", "CV", 19900, 400.0, 299.0, PlanScope.OWN_AND_ROAMING_FAST, true,
    )
    val master = SubscriptionPlan(
        "st_sub_master", "SK일렉링크 구독 마스터", "공통", "ST", 19900, null, 295.0, PlanScope.OWN_ONLY, true,
    )
    val vmembers = SubscriptionPlan(
        "cv_vmembers", "채비 V 멤버스", "공통", "CV", 4900, null, 331.1, PlanScope.OWN_ONLY, false,
    )
    val plans = listOf(blue, master, vmembers).associateBy { it.id }

    private val names = mapOf("ST" to "SK일렉링크", "CV" to "채비", "EV" to "에버온")
    val operatorName: (String) -> String = { names.getValue(it) }

    val wallet = Wallet(
        moeCard = true,
        subscriptions = listOf(
            ActiveSubscription("cv_passh_blue", 0.0, "2026-11"),
            ActiveSubscription("st_sub_master", 0.0, "2026-11"),
        ),
    )

    fun station(bid: String, stage: Stage, highway: Boolean = false, id: String = "T-$bid") =
        Station(id, "테스트 $bid", "", 0.0, 0.0, bid, 200, listOf(stage), highway)

    fun quote(s: Station, kwh: Double, w: Wallet = wallet): Quote =
        PriceEngine.quote(s, kwh, w, table, plans, operatorName)

    /** bid가 null이면 환경부 카드 선택지 */
    fun Quote.option(bid: String?): PayOption = options.first {
        if (bid == null) it.credential is Credential.MoeCard else (it.credential as? Credential.Account)?.bid == bid
    }
}
