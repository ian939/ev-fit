// FIT 점수 검산 테스트 — 기대값은 최소-최대 정규화 가중합을 손계산(plan.md Step 3)
package com.ian.evfit.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitScoreTest {
    @Test
    fun 시연_정차지_세_후보의_FIT_점수() {
        val s = FitScore.scores(listOf(
            FitInput(14304, 0.0, 18.0, 98.0),
            FitInput(14498, 0.0, 18.0, 95.0),
            FitInput(19061, 10.0, 15.0, 99.0),
        ))
        assertEquals(listOf(95.0, 78.0, 20.0), s)
    }

    @Test
    fun 가장_싼_곳이_1위가_아닐_수_있다() {
        val s = FitScore.scores(listOf(
            FitInput(11800, 30.0, 40.0, 93.0),
            FitInput(11850, 0.0, 20.0, 99.0),
            FitInput(11960, 10.0, 25.0, 97.0),
        ))
        assertEquals(listOf(50.0, 84.4, 34.3), s)
    }

    @Test
    fun 후보가_하나면_100점이고_합성_성공률은_범위_안() {
        assertEquals(listOf(100.0), FitScore.scores(listOf(FitInput(1, 0.0, 0.0, 0.0))))
        val a = SyntheticStats.successPct("ST441324")
        assertTrue(a in 92.0..99.5)
        assertEquals(a, SyntheticStats.successPct("ST441324"), 0.0)
    }
}
