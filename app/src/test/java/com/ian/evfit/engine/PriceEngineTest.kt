// 요금 엔진 검산 테스트 — 기대 금액은 환경부 공시 단가로 손계산한 값(plan.md Step 3 표)
package com.ian.evfit.engine

import com.ian.evfit.engine.TestFixtures.option
import com.ian.evfit.engine.TestFixtures.quote
import com.ian.evfit.engine.TestFixtures.station
import com.ian.evfit.engine.TestFixtures.wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceEngineTest {
    private val demoKwh = PriceEngine.kwhNeeded(83.6, 22, 80)

    @Test
    fun 이브이시스_급속은_채비패스H가_최저() {
        val q = quote(station("JA", Stage.S4), 40.0)
        val best = q.best!!
        assertEquals("CV", (best.credential as Credential.Account).bid)
        assertEquals(11960L, best.costKrw)
        assertEquals("cv_passh_blue", best.appliedPlan?.id)
        assertEquals(13936L, q.option(null).costKrw)
        assertEquals(18000L, q.option("ST").costKrw)
        assertTrue(q.unavailable.isEmpty())
        assertTrue(q.option("ST").notes.contains("SK일렉링크 구독 마스터: SK일렉링크 충전소 전용"))
        assertEquals(1976L, q.savingVsMoe)
    }

    @Test
    fun SK_도심_충전소는_SK마스터가_최저() {
        val q = quote(station("ST", Stage.S4), 40.0)
        assertEquals("ST", (q.best!!.credential as Credential.Account).bid)
        assertEquals(11800L, q.best!!.costKrw)
        assertEquals("st_sub_master", q.best!!.appliedPlan?.id)
        assertEquals(11960L, q.option("CV").costKrw)
        assertEquals(13936L, q.option(null).costKrw)
        assertEquals(2136L, q.savingVsMoe)
    }

    @Test
    fun SK_고속도로도_SK마스터가_최저() {
        assertEquals(48.488, demoKwh, 1e-6)
        val q = quote(station("ST", Stage.S5, highway = true), demoKwh)
        assertEquals("ST", (q.best!!.credential as Credential.Account).bid)
        assertEquals(14304L, q.best!!.costKrw)
        assertEquals("st_sub_master", q.best!!.appliedPlan?.id)
        assertEquals(14498L, q.option("CV").costKrw)
        assertEquals(19061L, q.option(null).costKrw)
        assertEquals(4757L, q.savingVsMoe)
    }

    @Test
    fun 같은_휴게소_SK시그넷은_환경부카드만_가능() {
        val q = quote(station("SG", Stage.S5, highway = true), demoKwh)
        assertTrue(q.best!!.credential is Credential.MoeCard)
        assertEquals(19061L, q.best!!.costKrw)
        assertEquals(2, q.unavailable.size)
        assertEquals(0L, q.savingVsMoe)
    }

    @Test
    fun 약정_잔여가_적으면_환경부카드가_최저() {
        val w = wallet.copy(subscriptions = listOf(
            ActiveSubscription("cv_passh_blue", 390.0, "2026-11"),
            ActiveSubscription("st_sub_master", 0.0, "2026-11"),
        ))
        val q = quote(station("JA", Stage.S4), 40.0, w)
        val cv = q.option("CV")
        assertEquals(17690L, cv.costKrw)
        assertEquals(2, cv.parts.size)
        assertEquals(10.0, cv.parts[0].kwh, 1e-9)
        assertEquals(299.0, cv.parts[0].unitPriceKrw, 1e-9)
        assertEquals(30.0, cv.parts[1].kwh, 1e-9)
        assertEquals(490.0, cv.parts[1].unitPriceKrw, 1e-9)
        assertTrue(q.best!!.credential is Credential.MoeCard)
        assertEquals(13936L, q.best!!.costKrw)
    }

    @Test
    fun 약정_소진이면_구독_미적용_안내() {
        val w = wallet.copy(subscriptions = listOf(
            ActiveSubscription("cv_passh_blue", 400.0, "2026-11"),
            ActiveSubscription("st_sub_master", 0.0, "2026-11"),
        ))
        val cv = quote(station("JA", Stage.S4), 40.0, w).option("CV")
        assertEquals(19600L, cv.costKrw)
        assertNull(cv.appliedPlan)
        assertTrue(cv.notes.contains("채비패스 H 블루: 이번 달 약정량 소진"))
    }

    @Test
    fun V멤버스는_고속도로에서_회원가() {
        val w = Wallet(moeCard = false, subscriptions = listOf(ActiveSubscription("cv_vmembers", 0.0, "2026-11")))
        val hw = quote(station("CV", Stage.S5, highway = true), 40.0, w).option("CV")
        assertEquals(17200L, hw.costKrw)
        assertNull(hw.appliedPlan)
        assertTrue(hw.notes.contains("채비 V 멤버스: 고속도로 휴게소 미적용"))
        val city = quote(station("CV", Stage.S4), 40.0, w).option("CV")
        assertEquals(13244L, city.costKrw)
        assertEquals("cv_vmembers", city.appliedPlan?.id)
    }

    @Test
    fun 에버온_회원이면_회원가가_최저() {
        val q = quote(station("EV", Stage.S4), 40.0, wallet.copy(memberships = setOf("EV")))
        assertEquals("EV", (q.best!!.credential as Credential.Account).bid)
        assertEquals(11840L, q.best!!.costKrw)
        assertEquals(11960L, q.option("CV").costKrw)
        assertEquals(13936L, q.option(null).costKrw)
        assertEquals(18600L, q.option("ST").costKrw)
    }

    @Test
    fun 결제수단이_없으면_best가_null() {
        val q = quote(station("HD", Stage.S5, highway = true), 40.0, wallet.copy(moeCard = false))
        assertTrue(q.options.isEmpty())
        assertNull(q.best)
        assertEquals(2, q.unavailable.size)
        assertEquals(15724L, q.moeReferenceKrw)
    }

    @Test
    fun 환경부카드만_있으면_E_pit도_가능() {
        val q = quote(station("HD", Stage.S5, highway = true), 40.0, Wallet(moeCard = true))
        assertTrue(q.best!!.credential is Credential.MoeCard)
        assertEquals(15724L, q.best!!.costKrw)
    }
}
