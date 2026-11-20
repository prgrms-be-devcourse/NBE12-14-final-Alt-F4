package com.team4.live_calendar.domain.event.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime

// 일정 내용 전체 수정 (반복 일정은 모든 회차가 함께 바뀜). 캘린더 이동은 지원하지 않는다
// 시간 일정은 startAt, endAt / 종일 일정은 isAllDay=true + startDate, endDate
// rrule을 보내지 않으면 반복이 해제되므로, 반복을 유지하려면 기존 rrule을 같이 보낸다
class EventUpdateRequest(
    val categoryId: Long?,

    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    val description: String?,

    @field:Size(max = 255)
    val location: String?,

    val isAllDay: Boolean = false,

    val startAt: OffsetDateTime? = null,

    val endAt: OffsetDateTime? = null,

    val startDate: LocalDate? = null,

    val endDate: LocalDate? = null,

    // 없으면 기존 시간대 유지. 단, 일반·종일 일정을 처음 시간 반복으로 바꿀 때는 꼭 보내야 한다 (없으면 400 INVALID_RECURRENCE)
    @field:Size(max = 100)
    val timezone: String? = null,

    // 없거나 비어 있으면 반복 해제
    val rrule: String? = null,
)
