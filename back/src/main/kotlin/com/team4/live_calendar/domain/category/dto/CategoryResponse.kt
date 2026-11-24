package com.team4.live_calendar.domain.category.dto

import com.team4.live_calendar.domain.category.entity.EventCategory

// 카테고리 하나 (생성·수정 응답). color는 원본 색
data class CategoryResponse(
    val id: Long,
    val calendarId: Long,
    val name: String,
    val color: String?,
) {
    companion object {
        fun from(category: EventCategory): CategoryResponse =
            CategoryResponse(
                id = requireNotNull(category.id),
                calendarId = requireNotNull(category.calendar.id),
                name = category.name,
                color = category.color,
            )
    }
}
