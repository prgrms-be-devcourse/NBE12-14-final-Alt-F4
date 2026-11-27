
package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.NotificationOutbox
import com.team4.live_calendar.domain.notification.entity.NotificationOutboxStatus
import com.team4.live_calendar.domain.notification.repository.NotificationOutboxRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Service
@Transactional(readOnly = true)
class NotificationOutboxService(
    private val notificationOutboxRepository: NotificationOutboxRepository,
) {

    @Transactional
    fun save(history: EventHistory): NotificationOutbox =
        notificationOutboxRepository.save(NotificationOutbox.create(history))

    @Transactional
    fun claimNext(): Long? {
        repeat(16) {
            val now = Instant.now()
            val id = notificationOutboxRepository.findNextPendingIds(NotificationOutboxStatus.PENDING, now, PageRequest.of(0, 1)).firstOrNull() ?: return null
            if (notificationOutboxRepository.claim(id, NotificationOutboxStatus.PENDING, NotificationOutboxStatus.PROCESSING, now) == 1) return id
        }
        return null
    }

    @Transactional
    fun markRetry(id: Long, error: String) {
        val outbox = notificationOutboxRepository.findById(id).orElse(null) ?: return
        if (outbox.status != NotificationOutboxStatus.PROCESSING) return
        val delay = Duration.ofSeconds(30L * (1L shl outbox.retryCount.coerceAtMost(7)))
        outbox.markRetry(Instant.now(), error, delay, 10)
    }

    @Transactional
    fun recoverStale(lease: Duration = Duration.ofMinutes(5)): Int {
        val now = Instant.now()
        return notificationOutboxRepository.recoverStale(
            NotificationOutboxStatus.PENDING,
            NotificationOutboxStatus.PROCESSING,
            now,
            now.minus(lease),
        )
    }
}
