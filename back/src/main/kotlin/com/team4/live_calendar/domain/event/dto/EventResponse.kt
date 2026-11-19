package com.team4.live_calendar.domain.event.dto

import com.team4.live_calendar.domain.event.entity.Event
import java.time.Instant

data class EventResponse(
    val id: Long,
    val calendarId: Long,
    val categoryId: Long?,
    val userId: Long,
    val title: String,
    val description: String?,
    val location: String?,
    val startAt: Instant,
    val endAt: Instant,
    val isAllDay: Boolean,
    val timezone: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun from(event: Event): EventResponse =
            EventResponse(
                id = requireNotNull(event.id),
                calendarId = requireNotNull(event.calendar.id),
                categoryId = event.category?.id,
                userId = requireNotNull(event.user.id),
                title = event.title,
                description = event.description,
                location = event.location,
                startAt = event.startAt,
                endAt = event.endAt,
                isAllDay = event.isAllDay,
                timezone = event.timezone,
                createdAt = event.createdAt,
                updatedAt = event.updatedAt,
            )
    }
}
