package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 그룹 생성·이름 수정 요청 (앞뒤 공백은 저장할 때 지운다)
class CalendarGroupRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
)
