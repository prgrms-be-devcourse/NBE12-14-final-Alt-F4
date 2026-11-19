package com.team4.live_calendar.domain.event.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime

// 일정 내용 전체 수정. 캘린더 이동, 시간대·반복 규칙 변경은 지원하지 않는다
// 시간 일정은 startAt, endAt / 종일 일정은 isAllDay=true + startDate, endDate
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
)
