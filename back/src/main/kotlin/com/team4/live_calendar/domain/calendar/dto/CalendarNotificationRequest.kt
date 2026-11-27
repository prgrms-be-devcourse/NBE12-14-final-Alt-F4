package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotNull

data class CalendarNotificationRequest(
    @field:NotNull
    val notificationEnabled: Boolean?,
)
