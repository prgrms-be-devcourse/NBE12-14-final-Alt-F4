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
    val rrule: String?,
    // 기간 조회: 이 회차의 원래 시작 / 상세·생성·수정: startAt과 같음
    val occurrenceStart: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        // 반복 일정의 회차는 startAt, endAt에 그 회차의 시간을 넣는다 (id는 시리즈와 같음)
        fun from(event: Event, startAt: Instant = event.startAt, endAt: Instant = event.endAt): EventResponse =
            EventResponse(
                id = requireNotNull(event.id),
                calendarId = requireNotNull(event.calendar.id),
                categoryId = event.category?.id,
                userId = requireNotNull(event.user.id),
                title = event.title,
                description = event.description,
                location = event.location,
                startAt = startAt,
                endAt = endAt,
                isAllDay = event.isAllDay,
                timezone = event.timezone,
                rrule = event.rrule,
                occurrenceStart = startAt,
                createdAt = event.createdAt,
                updatedAt = event.updatedAt,
            )
    }
}
