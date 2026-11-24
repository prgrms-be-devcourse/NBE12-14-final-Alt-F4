package com.team4.live_calendar.domain.category.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

// 카테고리 수정 요청 (모든 칸 보내기, 안 보낸 color는 비워진다)
// 카테고리의 캘린더는 바꿀 수 없어 calendarId를 받지 않는다
class CategoryUpdateRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val name: String,
    @field:Size(max = 30)
    val color: String? = null,
)
