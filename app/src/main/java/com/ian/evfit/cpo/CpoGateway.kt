// 충전사업자(CPO) 연동 인터페이스와 시연용 목 구현 — PnC 계약 전환·충전 전 확인 안내
package com.ian.evfit.cpo

import com.ian.evfit.engine.Credential
import kotlinx.coroutines.delay

/** 차량에 걸어 둔 PnC 계약(시연에서는 시뮬레이션) */
data class PncContract(val emaid: String, val label: String, val simulated: Boolean)

interface CpoGateway {
    /** 다음 충전에서 차량이 이 결제 수단의 계약으로 인증하도록 전환 */
    suspend fun switchPncContract(vin: String?, credential: Credential): PncContract

    /** 충전 전에 사용자가 확인할 것(쿠폰·포인트는 결제 순간이 아니라 미리 설정하는 구조) */
    fun prechargeTips(credential: Credential): List<String>
}

class MockCpoGateway : CpoGateway {
    override suspend fun switchPncContract(vin: String?, credential: Credential): PncContract {
        delay(800)
        val provider = when (credential) {
            Credential.MoeCard -> "MEC"
            is Credential.Account -> when (credential.bid) {
                "CV" -> "CVH"
                "ST" -> "SKE"
                else -> credential.bid.filter { it.isLetterOrDigit() }.take(3).uppercase().padEnd(3, 'X')
            }
        }
        val seed = ((vin ?: "EVFIT-DEMO-VIN") + provider).hashCode().toLong().let { if (it < 0) -it else it }
        val id = (seed % 100_000_000).toString().padStart(8, '0')
        val check = id.sumOf { it - '0' } % 10
        return PncContract("KR-$provider-C$id-$check", credential.label, simulated = true)
    }

    override fun prechargeTips(credential: Credential): List<String> = when (credential) {
        Credential.MoeCard -> listOf("환경부 카드: 충전기에 카드 태그 또는 EV이음 앱 QR")
        is Credential.Account -> when (credential.bid) {
            "ST" -> listOf("SK일렉링크 앱: 할인은 구독·제휴 > 쿠폰 > 무상크레딧 > 포인트 순으로 자동 적용")
            "CV" -> listOf("채비 앱: 크레딧이 있으면 우선 차감되는지 확인")
            else -> listOf("${credential.name} 앱: 회원 쿠폰·포인트 자동 적용 설정 확인")
        }
    }
}
