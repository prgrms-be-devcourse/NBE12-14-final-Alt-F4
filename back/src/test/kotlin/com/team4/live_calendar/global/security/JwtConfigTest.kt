package com.team4.live_calendar.global.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import kotlin.test.assertNotNull

class JwtConfigTest {

    private fun config(secret: String) = JwtConfig(
        JwtProperties(
            secret = secret,
            issuer = "live-calendar",
            accessTokenExpiry = Duration.ofMinutes(30),
            refreshTokenExpiry = Duration.ofDays(14),
        ),
    )

    @Test
    fun `비밀키가 32바이트 미만이면 Encoder와 Decoder를 만들 수 없다`() {
        // given
        val config = config("short-secret")

        // when & then
        assertThrows<IllegalArgumentException> { config.jwtEncoder() }
        assertThrows<IllegalArgumentException> { config.jwtDecoder() }
    }

    @Test
    fun `비밀키가 32바이트 이상이면 Encoder와 Decoder를 만든다`() {
        // given
        val config = config("x".repeat(32))

        // when & then
        assertNotNull(config.jwtEncoder())
        assertNotNull(config.jwtDecoder())
    }
}
