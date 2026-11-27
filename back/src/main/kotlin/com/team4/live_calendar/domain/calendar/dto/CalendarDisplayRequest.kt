package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// 캘린더 표시 설정 (모든 칸 보내기). color를 안 보내면 내 색이 지워진다
class CalendarDisplayRequest(
    @field:NotNull
    val isVisible: Boolean? = null,
    @field:Size(max = 30)
    val color: String? = null,
)
