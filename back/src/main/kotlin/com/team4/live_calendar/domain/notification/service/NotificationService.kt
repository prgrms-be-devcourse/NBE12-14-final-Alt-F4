
package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.calendar.entity.Calendar
import com.team4.live_calendar.domain.notification.entity.EventHistory
import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.notification.repository.NotificationRepository
import com.team4.live_calendar.domain.user.entity.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {

    @Transactional
    fun save(
        user: User,
        calendar: Calendar?,
        type: NotificationType,
        params: Map<String, Any?>? = null,
        eventHistory: EventHistory? = null,
    ): Notification =
        notificationRepository.save(
            Notification.create(
                user = user,
                calendar = calendar,
                type = type,
                params = params,
                eventHistory = eventHistory,
            )
        )
}
