// 테스트 실행 환경이 동작하는지 확인하는 최소 테스트
package com.ian.evfit

import org.junit.Assert.assertEquals
import org.junit.Test

class SanityTest {
    @Test
    fun 더하기가_동작한다() {
        assertEquals(4, 2 + 2)
    }
}
