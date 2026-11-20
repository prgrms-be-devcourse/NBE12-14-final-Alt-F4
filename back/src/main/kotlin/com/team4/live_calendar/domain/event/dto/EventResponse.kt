package com.team4.live_calendar.domain.event.dto

import com.team4.live_calendar.domain.event.entity.Event
import com.team4.live_calendar.domain.event.entity.EventException
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
    // 반복 일정의 회차: 이 회차의 원래 시작 (회차를 옮겨도 그대로) / 반복 없는 일정·상세·생성·전체 수정: startAt과 같음
    // 이 일정만 수정·삭제할 때 이 값을 그대로 보낸다
    val occurrenceStart: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        // 반복 일정의 회차는 startAt, endAt에 그 회차의 시간을 넣는다 (id는 시리즈와 같음)
        fun from(
            event: Event,
            startAt: Instant = event.startAt,
            endAt: Instant = event.endAt,
            occurrenceStart: Instant = startAt,
        ): EventResponse =
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
                occurrenceStart = occurrenceStart,
                createdAt = event.createdAt,
                updatedAt = event.updatedAt,
            )

        // 이 일정만 수정한 회차: 제목·설명·장소·시간·수정 시각은 예외 값, 나머지는 반복 일정 값
        fun from(event: Event, exception: EventException): EventResponse =
            from(event, requireNotNull(exception.startAt), requireNotNull(exception.endAt), exception.recurrenceId)
                .copy(
                    title = requireNotNull(exception.title),
                    description = exception.description,
                    location = exception.location,
                    updatedAt = exception.updatedAt,
                )
    }
}
