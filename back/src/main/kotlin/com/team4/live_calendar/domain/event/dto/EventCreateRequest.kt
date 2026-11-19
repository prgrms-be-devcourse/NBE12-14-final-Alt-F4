package com.team4.live_calendar.domain.event.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.OffsetDateTime

// 시간 일정: startAt, endAt (시간대 포함 ISO-8601, 예: 2026-11-18T14:00:00+09:00)
// 종일 일정: isAllDay=true + startDate, endDate (날짜만, 예: 2026-11-18, endDate는 마지막 날 포함)
class EventCreateRequest(
    val calendarId: Long,

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

    // 반복 일정을 펼칠 때 기준 시간대 (IANA 이름, 예: Asia/Seoul). 없으면 UTC
    @field:Size(max = 100)
    val timezone: String? = null,
)
