// 요금 엔진이 쓰는 데이터 모델 — 충전소·단가표·구독 상품·지갑·결제 선택지
package com.ian.evfit.engine

import kotlinx.serialization.Serializable

enum class Stage(val key: String) {
    S1("s1"), S2("s2"), S3("s3"), S4("s4"), S5("s5");

    companion object {
        fun parse(key: String): Stage = entries.first { it.key == key }
        fun fromKw(kw: Int): Stage = when {
            kw < 30 -> S1
            kw < 50 -> S2
            kw < 100 -> S3
            kw < 200 -> S4
            else -> S5
        }
    }
}

data class LatLng(val lat: Double, val lng: Double)

data class Station(
    val id: String, val name: String, val addr: String,
    val lat: Double, val lng: Double, val bid: String,
    val maxKw: Int, val stages: List<Stage>, val highway: Boolean,
) {
    /** 단가 계산 기준 단계 — 이 충전소의 급속 단계 중 최고 */
    val topStage: Stage get() = stages.maxBy { it.ordinal }
    val pos: LatLng get() = LatLng(lat, lng)
}

/** 환경부 공시 단가표. self 키 "bid|s4", roaming 키 "cardBid|chargerBid|s4" */
class PricingTable(private val self: Map<String, Double>, private val roaming: Map<String, Double>) {
    fun memberPrice(bid: String, stage: Stage): Double? = self["$bid|${stage.key}"]
    fun roamingPrice(cardBid: String, chargerBid: String, stage: Stage): Double? =
        roaming["$cardBid|$chargerBid|${stage.key}"]

    /** cardBid 회원카드로 chargerBid 충전기를 쓸 때 단가: 같은 사업자면 회원가, 다르면 로밍가 */
    fun priceFor(cardBid: String, chargerBid: String, stage: Stage): Double? =
        if (cardBid == chargerBid) memberPrice(cardBid, stage) else roamingPrice(cardBid, chargerBid, stage)
}

@Serializable
enum class PlanScope { OWN_ONLY, OWN_AND_ROAMING_FAST }

@Serializable
data class SubscriptionPlan(
    val id: String, val name: String, val brand: String, val providerBid: String,
    val monthlyFeeKrw: Int, val monthlyCapKwh: Double? = null, val fastPriceKrwKwh: Double,
    val scope: PlanScope, val highwayAllowed: Boolean,
    val eligibility: String = "", val note: String = "", val sourceUrl: String = "", val asOf: String = "",
)

@Serializable
data class ActiveSubscription(val planId: String, val usedKwhThisMonth: Double = 0.0, val month: String)

@Serializable
data class Wallet(
    val vehicleModel: String? = null,
    val batteryKwh: Double? = null,
    val moeCard: Boolean = true,
    val memberships: Set<String> = emptySet(),
    val subscriptions: List<ActiveSubscription> = emptyList(),
    val targetSoc: Int = 80,
)

sealed interface Credential {
    val label: String

    data object MoeCard : Credential {
        override val label = "환경부 카드"
    }

    data class Account(val bid: String, val name: String) : Credential {
        override val label get() = "$name 카드"
    }
}

data class CostPart(val kwh: Double, val unitPriceKrw: Double, val desc: String)

data class PayOption(
    val credential: Credential,
    val parts: List<CostPart>,
    val appliedPlan: SubscriptionPlan? = null,
    val notes: List<String> = emptyList(),
) {
    val costKrw: Long get() = Math.round(parts.sumOf { it.kwh * it.unitPriceKrw })
    val effectiveUnitPrice: Double get() = parts.sumOf { it.kwh * it.unitPriceKrw } / parts.sumOf { it.kwh }

    /** 구독 약정에서 차감될 kWh */
    val planKwh: Double get() = if (appliedPlan == null) 0.0 else parts.first().kwh
}

data class Unavailable(val credential: Credential, val reason: String)

data class Quote(
    val station: Station, val stage: Stage, val kwh: Double,
    val options: List<PayOption>, val unavailable: List<Unavailable>,
    /** 환경부 카드(전국 공통 로밍가)로 결제했다면의 금액 — 절약액 기준. 지갑에 환경부 카드가 없어도 계산 */
    val moeReferenceKrw: Long?,
) {
    val best: PayOption? get() = options.firstOrNull()
    val savingVsMoe: Long? get() = best?.let { b -> moeReferenceKrw?.let { it - b.costKrw } }
}
