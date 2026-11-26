package com.team4.live_calendar.domain.calendar.dto

import com.team4.live_calendar.domain.calendar.entity.Subscribe

// 그룹 안에 둔 내 캘린더
// id는 캘린더 ID (일정 API의 calendarId로 그대로 쓴다)
// color는 내 화면 색상이 있으면 그것, 없으면 원본 색상
// role은 내 이용 관계의 역할 이름 (OWNER, EDITOR, VIEWER …)
// isVisible은 내 화면 표시 여부 (숨긴 캘린더도 목록에는 나온다), myColor는 내가 정한 색 (없으면 null)
// categories는 이 캘린더의 카테고리 (만든 순서, 없으면 빈 목록)
data class GroupCalendarResponse(
    val id: Long,
    val name: String,
    val color: String?,
    val role: String,
    val isVisible: Boolean,
    val myColor: String?,
    val categories: List<GroupCategoryResponse>,
) {
    companion object {
        fun from(subscribe: Subscribe, categories: List<GroupCategoryResponse>): GroupCalendarResponse =
            GroupCalendarResponse(
                id = requireNotNull(subscribe.calendar.id),
                name = subscribe.calendar.name,
                color = subscribe.color ?: subscribe.calendar.color,
                role = subscribe.role.name,
                isVisible = subscribe.isVisible,
                myColor = subscribe.color,
                categories = categories,
            )
    }
}
