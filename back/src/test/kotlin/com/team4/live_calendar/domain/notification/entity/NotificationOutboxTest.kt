package com.team4.live_calendar.domain.notification.entity

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.user.entity.User
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationOutboxTest {
    private val now = Instant.parse("2026-11-27T00:00:00Z")

    private fun outbox(): NotificationOutbox {
        val history = EventHistory.create(
            Calendar.create("캘린더", null, null),
            1L,
            User.signUp("actor@mail.com", "{bcrypt}hash", "작성자"),
            EventHistoryTargetType.EVENT,
            null,
            EventHistoryActionType.CREATE,
            "일정",
        )
        return NotificationOutbox.create(history)
    }

    @Test
    fun `실패하면 재시도 시각을 설정하고 횟수 초과 시 최종 실패한다`() {
        val outbox = outbox()

        outbox.markRetry(now, "temporary", Duration.ofSeconds(30), 2)
        assertEquals(NotificationOutboxStatus.PENDING, outbox.status)
        assertEquals(1, outbox.retryCount)
        assertEquals(now.plusSeconds(30), outbox.nextRetryAt)

        outbox.markRetry(now.plusSeconds(30), "permanent", Duration.ofSeconds(60), 2)
        assertEquals(NotificationOutboxStatus.FAILED, outbox.status)
        assertEquals(2, outbox.retryCount)
        assertNull(outbox.nextRetryAt)
        assertEquals(now.plusSeconds(30), outbox.processedAt)
    }

    @Test
    fun `오래된 처리 잠금은 다시 대기 상태로 복구한다`() {
        val outbox = outbox()
        outbox.markProcessing(now.minusSeconds(600))

        outbox.recover(now, now.minusSeconds(300))

        assertEquals(NotificationOutboxStatus.PENDING, outbox.status)
        assertNull(outbox.lockedAt)
        assertEquals(now, outbox.nextRetryAt)
    }

    @Test
    fun `완료 처리하면 잠금과 재시도 정보가 초기화된다`() {
        val outbox = outbox()
        outbox.markProcessing(now)
        outbox.markRetry(now, "temporary", Duration.ofSeconds(30), 3)
        outbox.markProcessing(now.plusSeconds(30))

        outbox.markCompleted(now.plusSeconds(31))

        assertEquals(NotificationOutboxStatus.COMPLETED, outbox.status)
        assertEquals(now.plusSeconds(31), outbox.processedAt)
        assertNull(outbox.lockedAt)
        assertNull(outbox.nextRetryAt)
        assertNull(outbox.errorMessage)
    }
}
