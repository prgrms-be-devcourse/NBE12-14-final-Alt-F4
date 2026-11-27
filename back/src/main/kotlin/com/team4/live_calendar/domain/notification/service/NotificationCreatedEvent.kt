package com.team4.live_calendar.domain.notification.service

import com.team4.live_calendar.domain.notification.dto.NotificationResponse

data class NotificationCreatedEvent(
    val userId: Long,
    val notification: NotificationResponse,
)
