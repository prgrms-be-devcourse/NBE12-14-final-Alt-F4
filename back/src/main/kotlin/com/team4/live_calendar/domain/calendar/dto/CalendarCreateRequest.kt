package com.team4.live_calendar.domain.calendar.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 캘린더 생성 요청 (이름만 필수)
// timezone이 없으면 UTC, groupId가 없으면 내 첫 그룹(그룹이 없으면 "내 캘린더" 그룹을 만든다)
class CalendarCreateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    val description: String? = null,
    @field:Size(max = 30)
    val color: String? = null,
    val timezone: String? = null,
    val groupId: Long? = null,
)
