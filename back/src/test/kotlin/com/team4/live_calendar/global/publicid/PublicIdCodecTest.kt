package com.team4.live_calendar.global.publicid

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 스프링 없이 인코더만 본다. 알파벳은 테스트 안에서 고정한다
class PublicIdCodecTest {

    private val codec = PublicIdCodec("9T1CV48Z7FQGS0KY3HMBAJWNXD2PR6E5", minLength = 6)

    @Test
    fun `인코딩한 코드는 Crockford 글자로 6자 이상이고 다시 같은 id로 읽힌다`() {
        // when
        val code = codec.encode(1L)

        // then
        assertTrue(Regex("^[0-9A-HJKMNP-TV-Z]{6,}$").matches(code))
        assertEquals(1L, codec.decode(code))
    }

    @Test
    fun `소문자로 받아도 같은 id로 읽힌다`() {
        // given
        val code = codec.encode(42L)

        // when & then
        assertEquals(42L, codec.decode(code.lowercase()))
    }

    @Test
    fun `I와 L은 1로, O는 0으로 읽는다`() {
        // given: 1과 0이 들어간 코드가 나올 때까지 id를 늘려 찾는다
        val (id, code) = (1L..10_000L).asSequence()
            .map { it to codec.encode(it) }
            .first { (_, code) -> '1' in code && '0' in code }
        val typo = code.replace('1', 'l').replace('0', 'O')

        // when & then
        assertEquals(id, codec.decode(typo))
    }

    @Test
    fun `6자보다 짧거나 Crockford에 없는 글자가 섞이면 null`() {
        // given
        val code = codec.encode(7L)

        // when & then
        assertNull(codec.decode(code.take(5)))
        assertNull(codec.decode(code.dropLast(1) + "U"))
        assertNull(codec.decode(""))
    }

    @Test
    fun `숫자 여러 개가 든 코드는 null`() {
        // given: 우리 API는 id 하나만 쓴다
        val multi = org.sqids.Sqids.builder()
            .alphabet("9T1CV48Z7FQGS0KY3HMBAJWNXD2PR6E5").minLength(6).build()
            .encode(listOf(1L, 2L))

        // when & then
        assertNull(codec.decode(multi))
    }

    @Test
    fun `알파벳이 Crockford 32종류를 한 번씩 섞은 값이 아니면 만들 때 실패한다`() {
        // when & then
        assertThrows<IllegalArgumentException> { PublicIdCodec("0123456789ABCDEFGHJKMNPQRSTVWXYU", 6) }
        assertThrows<IllegalArgumentException> { PublicIdCodec("0123456789", 6) }
    }

    @Test
    fun `알파벳이 다르면 같은 id도 다른 코드가 된다`() {
        // given
        val other = PublicIdCodec("D7KS2RHYQ3FEAX1BJGZVT5WMNC694P80", minLength = 6)

        // when & then
        assertTrue(codec.encode(5L) != other.encode(5L))
    }
}
