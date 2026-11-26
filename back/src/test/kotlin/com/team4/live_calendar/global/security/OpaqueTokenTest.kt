package com.team4.live_calendar.global.security

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OpaqueTokenTest {

    @Test
    fun `토큰은 URL에 쓸 수 있는 43자이고 매번 다르다`() {
        // when
        val first = OpaqueToken.generate()
        val second = OpaqueToken.generate()

        // then
        assertEquals(43, first.length)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]+")))
        assertNotEquals(first, second)
    }

    @Test
    fun `같은 토큰은 항상 같은 64자 해시가 된다`() {
        // given
        val token = OpaqueToken.generate()

        // when
        val hash = OpaqueToken.hash(token)

        // then
        assertEquals(64, hash.length)
        assertEquals(hash, OpaqueToken.hash(token))
    }
}
