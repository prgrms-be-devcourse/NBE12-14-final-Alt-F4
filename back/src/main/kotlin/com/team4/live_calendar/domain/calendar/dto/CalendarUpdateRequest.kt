package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 캘린더 수정 요청 (모든 칸 보내기)
// 안 보낸 description, color는 비워진다. timezone만 없거나 비어 있으면 기존 값을 유지한다 (일정 수정과 같음)
class CalendarUpdateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    val description: String? = null,
    @field:Size(max = 30)
    val color: String? = null,
    val timezone: String? = null,
)
