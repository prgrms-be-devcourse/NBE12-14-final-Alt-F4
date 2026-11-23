package com.team4.live_calendar.global.security

import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertTrue

class RefreshTokenCookieFactoryTest {

    private val factory = RefreshTokenCookieFactory(
        jwtProperties = JwtProperties(
            secret = "unit-test-secret-key-0123456789-abcdef",
            issuer = "live-calendar",
            accessTokenExpiry = Duration.ofMinutes(30),
            refreshTokenExpiry = Duration.ofDays(14),
        ),
        cookieProperties = AuthCookieProperties(),
    )

    @Test
    fun `기본 설정의 쿠키는 운영에 필요한 보안 속성을 모두 가진다`() {
        // when
        val cookie = factory.create("raw-token").toString()

        // then
        listOf("HttpOnly", "Secure", "SameSite=Lax", "Path=/api/v1/auth", "Max-Age=1209600").forEach {
            assertTrue(cookie.contains(it), "쿠키에 $it 이(가) 없다: $cookie")
        }
    }

    @Test
    fun `만료용 쿠키는 Max-Age가 0이다`() {
        // when
        val cookie = factory.expire().toString()

        // then
        assertTrue(cookie.contains("Max-Age=0"))
    }
}
