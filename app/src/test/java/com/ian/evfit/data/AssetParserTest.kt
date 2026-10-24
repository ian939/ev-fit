// 실제 앱 자산(assets/data)을 파싱해 핵심 값이 들어 있는지 확인하는 통합 테스트
package com.ian.evfit.data

import com.ian.evfit.engine.ActiveSubscription
import com.ian.evfit.engine.Stage
import com.ian.evfit.engine.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AssetParserTest {
    private val data = AssetParser.parse { File("src/main/assets/data/$it").readText(Charsets.UTF_8) }

    @Test
    fun 자산에_요금_충전소_구독_차종이_들어_있다() {
        assertEquals(393.1, data.table.priceFor("ME", "ST", Stage.S5)!!, 1e-9)
        assertEquals(391.0, data.table.memberPrice("ST", Stage.S4)!!, 1e-9)
        val st = data.station("ST441324")!!
        assertTrue(st.highway)
        assertEquals(Stage.S5, st.topStage)
        assertEquals(7, data.plans.size)
        assertTrue(data.vehicle("더 뉴 아이오닉5 2WD 롱레인지 19인치")!!.batteryKwh == 83.6)
        assertEquals(177.3, data.benchFor("더 뉴 아이오닉5 2WD 롱레인지 19인치")!!.avgKwByClass.getValue("200"), 1e-9)
        assertEquals("SK일렉링크", data.operatorName("ST"))
    }

    @Test
    fun 월이_바뀌면_구독_사용량을_0으로() {
        val w = Wallet(subscriptions = listOf(ActiveSubscription("cv_passh_blue", 120.0, "2026-10")))
        assertEquals(0.0, Prefs.rollMonth(w, "2026-11").subscriptions[0].usedKwhThisMonth, 0.0)
        assertEquals(120.0, Prefs.rollMonth(w, "2026-10").subscriptions[0].usedKwhThisMonth, 0.0)
    }
}
