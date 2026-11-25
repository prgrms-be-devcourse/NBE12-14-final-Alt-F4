package com.team4.live_calendar.domain.auth.entity

import com.team4.live_calendar.domain.user.entity.User
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RefreshTokenTest {

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val user = User.signUp("test@mail.com", "{bcrypt}hash", "test")

    private fun tokenExpiringAt(expiresAt: Instant) =
        RefreshToken.create(user = user, tokenHash = "hash", expiresAt = expiresAt)

    @Test
    fun `만료 시각과 같거나 지났으면 만료다`() {
        // given
        val expiresNow = tokenExpiringAt(now)
        val expiredBefore = tokenExpiringAt(now.minusSeconds(1))

        // then
        assertTrue(expiresNow.isExpired(now))
        assertTrue(expiredBefore.isExpired(now))
    }

    @Test
    fun `만료 시각 전이면 만료가 아니다`() {
        // given
        val token = tokenExpiringAt(now.plusSeconds(1))

        // then
        assertFalse(token.isExpired(now))
    }
}
