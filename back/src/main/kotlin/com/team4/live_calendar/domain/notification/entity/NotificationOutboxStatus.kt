package com.team4.live_calendar.domain.notification.entity

enum class NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
}