
package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.NotificationOutbox
import com.team4.live_calendar.domain.notification.repository.NotificationOutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationOutboxService(
    private val notificationOutboxRepository: NotificationOutboxRepository,
) {

    @Transactional
    fun save(history: EventHistory): NotificationOutbox =
        notificationOutboxRepository.save(NotificationOutbox.create(history))
}
