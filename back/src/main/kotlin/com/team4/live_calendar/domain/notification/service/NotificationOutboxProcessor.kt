package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.calendar.repository.SubscribeRepository
import com.team4.live_calendar.domain.notification.entity.EventHistoryActionType
import com.team4.live_calendar.domain.notification.entity.NotificationOutboxStatus
import com.team4.live_calendar.domain.notification.entity.NotificationType
import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.dto.NotificationResponse
import com.team4.live_calendar.domain.notification.repository.NotificationOutboxRepository
import com.team4.live_calendar.domain.notification.repository.NotificationRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NotificationOutboxProcessor(
    private val notificationOutboxRepository: NotificationOutboxRepository,
    private val subscribeRepository: SubscribeRepository,
    private val notificationRepository: NotificationRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun process(id: Long) {
        val outbox = notificationOutboxRepository.findById(id).orElse(null) ?: return
        if (outbox.status != NotificationOutboxStatus.PROCESSING) return

        val history = outbox.history
        val eventHistoryId = history.id ?: error("EventHistory ID is missing")
        val notificationType = when (history.actionType) {
            EventHistoryActionType.CREATE -> NotificationType.EVENT_CREATED
            EventHistoryActionType.UPDATE -> NotificationType.EVENT_UPDATED
            EventHistoryActionType.DELETE -> NotificationType.EVENT_DELETED
        }
        val subscribers = subscribeRepository.findAllNotificationEnabledByCalendarId(
            requireNotNull(history.calendar.id),
            requireNotNull(history.actor.id),
        )
        val params = mapOf(
            "eventId" to history.eventId,
            "eventTitle" to history.eventTitle,
            "targetType" to history.targetType.name,
            "recurrenceId" to history.recurrenceId?.toString(),
            "beforeData" to history.beforeData,
            "afterData" to history.afterData,
        )

        subscribers.forEach { subscribe ->
            if (!notificationRepository.existsByUserIdAndEventHistoryId(requireNotNull(subscribe.user.id), eventHistoryId)) {
                val notification = notificationRepository.save(
                    Notification.create(
                        subscribe.user,
                        history.calendar,
                        notificationType,
                        params,
                        history,
                    )
                )
                eventPublisher.publishEvent(
                    NotificationCreatedEvent(
                        requireNotNull(subscribe.user.id),
                        NotificationResponse.from(notification),
                    )
                )
            }
        }

        outbox.markCompleted(Instant.now())
    }
}
