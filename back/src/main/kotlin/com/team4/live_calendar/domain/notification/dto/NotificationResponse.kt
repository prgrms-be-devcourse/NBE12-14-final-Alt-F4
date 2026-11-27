package com.team4.live_calendar.domain.notification.dto

import com.team4.live_calendar.domain.notification.entity.Notification
import com.team4.live_calendar.domain.notification.entity.NotificationType
import java.time.Instant

data class NotificationResponse(
    val id: Long,
    val type: NotificationType,
    val calendarId: Long?,
    val calendarName: String?,
    val eventId: Long?,
    val eventTitle: String?,
    val params: Map<String, Any?>?,
    val isRead: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(notification: Notification): NotificationResponse {
            val history = notification.eventHistory
            return NotificationResponse(
                requireNotNull(notification.id),
                notification.type,
                notification.calendar?.id,
                notification.calendar?.name,
                history?.eventId,
                history?.eventTitle,
                notification.params,
                notification.isRead,
                notification.createdAt,
            )
        }
    }
}
