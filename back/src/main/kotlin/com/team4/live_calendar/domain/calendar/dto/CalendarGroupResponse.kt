package com.team4.live_calendar.domain.calendar.dto

import com.team4.live_calendar.domain.calendar.entity.CalendarGroup

// 그룹 생성·이름 수정 응답
data class CalendarGroupResponse(
    val id: Long,
    val name: String,
    val displayOrder: Int?,
) {
    companion object {
        fun from(group: CalendarGroup): CalendarGroupResponse =
            CalendarGroupResponse(
                id = requireNotNull(group.id),
                name = group.name,
                displayOrder = group.displayOrder,
            )
    }
}
