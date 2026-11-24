package com.team4.live_calendar.domain.category.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

// 카테고리 생성 요청 (calendarId, name 필수)
// Long?로 받아야 칸이 없을 때 400 INVALID_INPUT(errors에 calendarId)이 난다. Long이면 JSON 변환에서 실패해 code 없이 400이 된다
class CategoryCreateRequest(
    @field:NotNull
    val calendarId: Long?,
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:Size(max = 30)
    val color: String? = null,
)
