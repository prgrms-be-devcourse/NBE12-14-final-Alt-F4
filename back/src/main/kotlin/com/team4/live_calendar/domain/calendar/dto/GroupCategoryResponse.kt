package com.team4.live_calendar.domain.calendar.dto

import com.team4.live_calendar.domain.category.entity.EventCategory

// 그룹 조회에서 캘린더마다 보여 주는 카테고리
// color는 원본 색 (사람별 색은 카테고리 표시·숨김 작업에서 쓴다)
data class GroupCategoryResponse(
    val id: Long,
    val name: String,
    val color: String?,
) {
    companion object {
        fun from(category: EventCategory): GroupCategoryResponse =
            GroupCategoryResponse(
                id = requireNotNull(category.id),
                name = category.name,
                color = category.color,
            )
    }
}
