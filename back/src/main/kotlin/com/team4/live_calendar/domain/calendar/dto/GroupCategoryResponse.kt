package com.team4.live_calendar.domain.calendar.dto

import com.team4.live_calendar.domain.category.entity.EventCategory

// 그룹 조회에서 캘린더마다 보여 주는 카테고리
// color는 원본 색 (사람별 색은 고도화). isVisible은 내 화면 표시 여부 (내가 숨겼으면 false)
data class GroupCategoryResponse(
    val id: Long,
    val name: String,
    val color: String?,
    val isVisible: Boolean,
) {
    companion object {
        fun from(category: EventCategory, isVisible: Boolean): GroupCategoryResponse =
            GroupCategoryResponse(
                id = requireNotNull(category.id),
                name = category.name,
                color = category.color,
                isVisible = isVisible,
            )
    }
}
