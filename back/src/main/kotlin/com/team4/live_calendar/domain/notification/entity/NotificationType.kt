package com.team4.live_calendar.domain.notification.entity

// API 명세의 알림 목록 type 값
enum class NotificationType {
    EVENT_CREATED,
    EVENT_UPDATED,
    EVENT_CANCELLED,
    EVENT_DELETED,
    MEMBER_JOINED,
    ROOM_CREATED,
    ROOM_CONFIRMED,
    CALENDAR_DELETED,
    GROUP_REQUEST_RECEIVED,
    GROUP_REQUEST_APPROVED,
    GROUP_REQUEST_REJECTED,
}
